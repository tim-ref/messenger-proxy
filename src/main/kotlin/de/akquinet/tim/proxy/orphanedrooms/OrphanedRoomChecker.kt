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
package de.akquinet.tim.proxy.orphanedrooms

import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminRoomMessages
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminRoomState
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration.Companion.days
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private const val PREFIX_A_28564 = "A_28564-01"

private val STATE_EVENT_TYPES =
  setOf(
    "m.room.create",
    "m.room.member",
    "m.room.power_levels",
    "m.room.join_rules",
    "m.room.history_visibility",
    "m.room.name",
    "m.room.topic",
    "m.room.avatar",
    "m.room.canonical_alias",
    "m.room.aliases",
    "m.room.guest_access",
    "m.room.encryption",
    "m.room.server_acl",
    "m.room.third_party_invite",
    "m.room.related_groups",
    "m.room.pinned_events",
    "m.room.tombstone",
    "m.space.child",
    "m.space.parent",
    "de.gematik.tim.room.name",
    "de.gematik.tim.room.topic",
  )

/**
 * Checks rooms against gematik A_28564-01 criteria for orphaned room detection. This is a pure
 * business logic component without any infrastructure dependencies.
 */
interface OrphanedRoomChecker {
  /**
   * Checks if only the room creator is in "join" membership state (Criterion 3). Returns true if
   * the room qualifies for orphan consideration.
   */
  fun checkRoomMembers(
    state: List<SynapseAdminRoomState.StateEvent>,
    creator: String?,
    roomId: String,
  ): Boolean

  /**
   * Checks if the room contains only state events (no regular message events) (Criterion 1).
   * Returns true if the room qualifies for orphan consideration.
   */
  fun checkRoomEvents(messages: SynapseAdminRoomMessages.Response, roomId: String): Boolean

  /**
   * Checks if the last state event is older than the configured threshold (Criterion 2). Returns
   * true if the room qualifies for orphan consideration.
   */
  fun checkLastStateEventTimestamp(
    state: List<SynapseAdminRoomState.StateEvent>,
    roomId: String,
  ): Boolean
}

class OrphanedRoomCheckerImpl(
  private val cleanupConfig: ProxyConfiguration.OrphanedRoomCleanupConfig,
  private val clock: Clock,
) : OrphanedRoomChecker {

  override fun checkRoomMembers(
    state: List<SynapseAdminRoomState.StateEvent>,
    creator: String?,
    roomId: String,
  ): Boolean {
    val joinedMembers =
      state
        .filter { it.type == "m.room.member" }
        .filter { event ->
          val membership = event.content["membership"]?.toString()?.trim('"')
          membership == "join"
        }
        .map { it.stateKey }

    // If no creator or no joined members, can't determine
    if (creator == null || joinedMembers.isEmpty()) {
      if (joinedMembers.isEmpty()) {
        return true
      }
      logger.debug { "$PREFIX_A_28564 Room $roomId has no creator info, skipping" }
      return false
    }

    // Check if only the creator is joined
    val onlyCreatorJoined = joinedMembers.size == 1 && joinedMembers.first() == creator
    if (!onlyCreatorJoined) {
      logger.debug { "$PREFIX_A_28564 Room $roomId has members other than creator, skipping" }
    }
    return onlyCreatorJoined
  }

  override fun checkRoomEvents(
    messages: SynapseAdminRoomMessages.Response,
    roomId: String,
  ): Boolean {
    val hasNonStateEvents =
      messages.chunk.any { event -> !isStateEventType(event.type) && event.stateKey == null }

    if (hasNonStateEvents) {
      logger.debug { "$PREFIX_A_28564 Room $roomId contains non-state events, skipping" }
      return false
    }
    return true
  }

  override fun checkLastStateEventTimestamp(
    state: List<SynapseAdminRoomState.StateEvent>,
    roomId: String,
  ): Boolean {
    val lastStateEventTimestamp = state.maxOfOrNull { it.originServerTs }
    val thresholdTimestamp =
      Instant.now(clock).minusSeconds(cleanupConfig.roomAgeThresholdDays.days.inWholeSeconds)

    if (lastStateEventTimestamp == null) {
      logger.debug { "$PREFIX_A_28564 Room $roomId has no state events with timestamp, skipping" }
      return false
    }

    val lastEventTime = Instant.ofEpochMilli(lastStateEventTimestamp)
    if (lastEventTime.isAfter(thresholdTimestamp)) {
      logger.debug {
        "$PREFIX_A_28564 Room $roomId last event at $lastEventTime is newer than threshold $thresholdTimestamp, skipping"
      }
      return false
    }
    return true
  }

  private fun isStateEventType(eventType: String): Boolean {
    return eventType in STATE_EVENT_TYPES
  }
}
