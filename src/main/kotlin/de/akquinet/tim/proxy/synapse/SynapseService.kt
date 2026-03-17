/*
 * Copyright © 2023 - 2026 akquinet GmbH (https://www.akquinet.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.akquinet.tim.proxy.synapse

import arrow.core.Either
import arrow.core.flatMap
import de.akquinet.tim.proxy.config.SynapseClientConfig
import de.akquinet.tim.proxy.orphanedrooms.OrphanedRoomChecker
import de.akquinet.tim.proxy.outcomes.FailedToGetAdminAccessToken
import de.akquinet.tim.proxy.synapse.client.SynapseClient
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminRooms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private const val PREFIX_A_28564 = "A_28564-01"

class SynapseService(val synapseClient: SynapseClient, val config: SynapseClientConfig) {

  /**
   * Acquires an admin access token and sets it on the SynapseClient.
   *
   * @return Either the access token on success, or FailedToGetAdminAccessToken on failure
   */
  suspend fun acquireAdminToken(): Either<FailedToGetAdminAccessToken, String> =
    Either.catch {
        synapseClient
          .login(
            userIdOrLocalpart =
              config.username?.value ?: throw RuntimeException("Admin user name not set"),
            password =
              config.password?.value ?: throw RuntimeException("Admin user password not set"),
          )
          .accessToken
          .also { synapseClient.bearerToken = it }
      }
      .mapLeft { FailedToGetAdminAccessToken(it) }

  suspend fun getEventTimestamp(roomId: String, eventId: String) =
    acquireAdminToken().flatMap { synapseClient.getEventTimestamp(roomId, eventId) }

  /**
   * Performs orphaned room cleanup by scanning all rooms and deleting those that match A_28564-01
   * criteria.
   */
  suspend fun performOrphanedRoomCleanup(checker: OrphanedRoomChecker) {
    logger.info { "$PREFIX_A_28564 Starting orphaned room cleanup scan" }

    val tokenResult = acquireAdminToken()
    if (tokenResult.isLeft()) {
      logger.error { "$PREFIX_A_28564 Could not obtain admin access token, skipping cleanup" }
      return
    }

    var deletedCount = 0
    var scannedCount = 0

    pagedRoomsFlow().collect { roomsPage ->
      roomsPage.rooms.forEach { room ->
        scannedCount++
        if (isOrphanedRoom(room, checker)) {
          synapseClient
            .deleteRoom(room.roomId, purge = true, block = false)
            .fold(
              ifLeft = { failure ->
                logger.warn {
                  "$PREFIX_A_28564 Failed to delete room ${room.roomId}: ${failure.message}"
                }
              },
              ifRight = {
                deletedCount++
                logger.info { "$PREFIX_A_28564 Successfully deleted orphaned room: ${room.roomId}" }
              },
            )
        }
      }
    }

    logger.info {
      "$PREFIX_A_28564 Cleanup completed: scanned=$scannedCount, deleted=$deletedCount"
    }
  }

  private fun pagedRoomsFlow(): Flow<SynapseAdminRooms.Response> = flow {
    var from: Int? = null
    do {
      synapseClient
        .listRooms(limit = 100, from = from)
        .onLeft { failure ->
          logger.error { "$PREFIX_A_28564 Failed to fetch rooms page: ${failure.message}" }
          from = null
        }
        .onRight { response ->
          emit(response)
          from = response.nextBatch
          if (from == null) {
            logger.info { "$PREFIX_A_28564 Finished scanning ${response.totalRooms} total rooms" }
          }
        }
    } while (from != null)
  }

  /**
   * Determines if a room is orphaned according to A_28564-01 criteria:
   * 1. Room contains only state events (no regular messages)
   * 2. Last state event is older than 14 days (configurable)
   * 3. Only room creator is in "join" membership state
   */
  private suspend fun isOrphanedRoom(
    room: SynapseAdminRooms.Room,
    checker: OrphanedRoomChecker,
  ): Boolean {
    val roomId = room.roomId

    // Quick check: if more than 1 joined member, not orphaned
    if (room.joinedMembers > 1) {
      logger.debug { "$PREFIX_A_28564 Room $roomId has ${room.joinedMembers} members, skipping" }
      return false
    }

    // Criterion 1: Room contains only state events (check before fetching state for efficiency)
    val messagesResult = synapseClient.getRoomMessages(roomId, limit = 50)
    if (messagesResult.isLeft()) {
      logger.warn { "$PREFIX_A_28564 Could not get messages for room $roomId, skipping" }
      return false
    }
    val messages = messagesResult.getOrNull() ?: return false

    if (!checker.checkRoomEvents(messages, roomId)) {
      return false
    }

    // Get room state (needed for member and timestamp checks)
    val stateResult = synapseClient.getRoomState(roomId)
    if (stateResult.isLeft()) {
      logger.warn { "$PREFIX_A_28564 Could not get state for room $roomId, skipping" }
      return false
    }

    val roomState = stateResult.getOrNull() ?: return false

    // Criterion 3: Only room creator should be in "join" state - criterion 3 before 2 to be more
    // efficient with network calls.
    if (!checker.checkRoomMembers(roomState.state, room.creator, roomId)) {
      return false
    }

    // Criterion 2: Last state event is older than threshold
    if (!checker.checkLastStateEventTimestamp(roomState.state, roomId)) {
      return false
    }

    logger.info {
      "$PREFIX_A_28564 Room $roomId identified as orphaned: " +
        "creator=${room.creator}, joined_members=${room.joinedMembers}"
    }
    return true
  }

  suspend fun getAccountData(userId: String) =
    acquireAdminToken().flatMap { synapseClient.getAccountData(userId) }
}
