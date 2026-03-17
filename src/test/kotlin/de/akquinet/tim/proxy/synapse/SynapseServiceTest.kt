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
import com.sksamuel.hoplite.Secret
import de.akquinet.tim.proxy.config.SynapseClientConfig
import de.akquinet.tim.proxy.orphanedrooms.OrphanedRoomChecker
import de.akquinet.tim.proxy.outcomes.CouldNotGetEventDetails
import de.akquinet.tim.proxy.outcomes.CouldNotGetRoomMessages
import de.akquinet.tim.proxy.outcomes.CouldNotGetRoomState
import de.akquinet.tim.proxy.outcomes.FailedToGetAdminAccessToken
import de.akquinet.tim.proxy.synapse.client.SynapseClient
import de.akquinet.tim.proxy.synapse.client.resources.LoginResponseBody
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminAccountData
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminDeleteRoom
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminRoomMessages
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminRoomState
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminRooms
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SynapseServiceTest :
  ShouldSpec({
    lateinit var mockSynapseClient: SynapseClient
    lateinit var synapseService: SynapseService

    val config =
      SynapseClientConfig(
        matrixDomain = "snpse.org",
        baseUrl = "https://snpse.org/",
        username = Secret("username"),
        password = Secret("password"),
      )

    beforeTest {
      mockSynapseClient = mockk<SynapseClient>(relaxed = true)
      synapseService = SynapseService(mockSynapseClient, config)
    }

    context("getPermissionConfig") {
      should("return config") {
        val synapseClientResponse =
          SynapseAdminAccountData.Response(
            accountData =
              JsonObject(
                content =
                  mapOf(
                    Pair(
                      "de.gematik.tim.account.permissionconfig.pro.v1",
                      JsonObject(
                        content = mapOf(Pair("defaultSetting", JsonPrimitive("block all")))
                      ),
                    )
                  )
              )
          )
        coEvery {
          mockSynapseClient.login(userIdOrLocalpart = "username", password = "password")
        } returns LoginResponseBody("access token", "@admin:snpse.org")
        coEvery { mockSynapseClient.getAccountData(userId = "@test-user:snpse.org") } returns
          Either.Right(synapseClientResponse)

        val result = synapseService.getAccountData(userId = "@test-user:snpse.org")
        result shouldBeRight synapseClientResponse
      }
      should("return error if admin access token not accessible") {
        coEvery {
          mockSynapseClient.login(userIdOrLocalpart = "username", password = "password")
        } throws RuntimeException("unauthorized")

        val result = synapseService.getAccountData(userId = "@test-user:snpse.org")
        result.shouldBeLeft().apply { shouldBeTypeOf<FailedToGetAdminAccessToken>() }
      }
    }

    context("getEvent") {
      val timestamp = 1764087110403
      should("return requested event") {
        coEvery {
          mockSynapseClient.login(userIdOrLocalpart = "username", password = "password")
        } returns LoginResponseBody("access token", "@test-user:snpse.org")
        coEvery {
          mockSynapseClient.getEventTimestamp(
            roomId = "!room1:matrix.org",
            eventId = "\$fukweghifu23:localhost",
          )
        } returns Either.Right(timestamp)

        val result =
          synapseService.getEventTimestamp(
            roomId = "!room1:matrix.org",
            eventId = "\$fukweghifu23:localhost",
          )

        coVerifyOrder {
          mockSynapseClient.login("username", "password")
          mockSynapseClient.bearerToken = "access token"
        }

        result shouldBeRight timestamp
      }

      should("return error if access token not accessible") {
        coEvery {
          mockSynapseClient.login(userIdOrLocalpart = "username", password = "password")
        } throws RuntimeException("unauthorized")

        val result =
          synapseService.getEventTimestamp(
            roomId = "!room1:matrix.org",
            eventId = "\$fukweghifu23:localhost",
          )
        result.shouldBeLeft().apply { shouldBeTypeOf<FailedToGetAdminAccessToken>() }
      }

      should("return error if server responds with error or something goes wrong") {
        coEvery {
          mockSynapseClient.login(userIdOrLocalpart = "username", password = "password")
        } returns LoginResponseBody("access token", "@test-user:snpse.org")
        coEvery {
          mockSynapseClient.getEventTimestamp(
            roomId = "!irg3ndeinLangerRoom01:localhost",
            eventId = "\$fukweghifu23:localhost",
          )
        } returns
          Either.Left(
            CouldNotGetEventDetails(
              roomId = "!irg3ndeinLangerRoom01:localhost",
              eventId = "\$fukweghifu23:localhost",
              error = "test error",
            )
          )

        val result =
          synapseService.getEventTimestamp(
            roomId = "!irg3ndeinLangerRoom01:localhost",
            eventId = "\$fukweghifu23:localhost",
          )

        coVerifyOrder {
          mockSynapseClient.login("username", "password")
          mockSynapseClient.bearerToken = "access token"
          mockSynapseClient.getEventTimestamp(
            roomId = "!irg3ndeinLangerRoom01:localhost",
            eventId = "\$fukweghifu23:localhost",
          )
        }

        result shouldBeLeft
          CouldNotGetEventDetails(
            roomId = "!irg3ndeinLangerRoom01:localhost",
            eventId = "\$fukweghifu23:localhost",
            error = "test error",
          )
      }
    }

    context("performOrphanedRoomCleanup") {
      val roomId = "!orphaned:snpse.org"
      val creator = "@creator:snpse.org"
      val oldTimestamp = 1707868800000L // Feb 14, 2024

      fun setupLoginMock() {
        coEvery {
          mockSynapseClient.login(userIdOrLocalpart = "username", password = "password")
        } returns LoginResponseBody("access-token", "@admin:snpse.org")
      }

      fun createRoom(roomId: String, creator: String?, joinedMembers: Int) =
        SynapseAdminRooms.Room(roomId = roomId, creator = creator, joinedMembers = joinedMembers)

      fun createRoomsResponse(vararg rooms: SynapseAdminRooms.Room) =
        SynapseAdminRooms.Response(rooms = rooms.toList(), totalRooms = rooms.size)

      fun createStateEvent(
        type: String,
        stateKey: String?,
        content: Map<String, String>,
        originServerTs: Long,
        sender: String,
      ) =
        SynapseAdminRoomState.StateEvent(
          type = type,
          stateKey = stateKey,
          content = JsonObject(content.mapValues { JsonPrimitive(it.value) }),
          originServerTs = originServerTs,
          sender = sender,
        )

      fun createMessagesResponse(vararg events: SynapseAdminRoomMessages.RoomEvent) =
        SynapseAdminRoomMessages.Response(chunk = events.toList())

      fun createRoomEvent(type: String, stateKey: String?, originServerTs: Long, sender: String) =
        SynapseAdminRoomMessages.RoomEvent(
          type = type,
          stateKey = stateKey,
          content = JsonObject(emptyMap()),
          originServerTs = originServerTs,
          sender = sender,
          eventId = "\$event:snpse.org",
        )

      should("delete room when all criteria are met") {
        setupLoginMock()
        val mockChecker = mockk<OrphanedRoomChecker>()

        coEvery { mockSynapseClient.listRooms(limit = 100, from = null) } returns
          Either.Right(createRoomsResponse(createRoom(roomId, creator, joinedMembers = 1)))

        coEvery { mockSynapseClient.getRoomMessages(roomId, limit = 50) } returns
          Either.Right(createMessagesResponse())

        coEvery { mockSynapseClient.getRoomState(roomId) } returns
          Either.Right(
            SynapseAdminRoomState.Response(
              listOf(
                createStateEvent(
                  "m.room.create",
                  "",
                  mapOf("creator" to creator),
                  oldTimestamp,
                  creator,
                )
              )
            )
          )

        every { mockChecker.checkRoomEvents(any(), roomId) } returns true
        every { mockChecker.checkRoomMembers(any(), creator, roomId) } returns true
        every { mockChecker.checkLastStateEventTimestamp(any(), roomId) } returns true

        coEvery { mockSynapseClient.deleteRoom(roomId, purge = true, block = false) } returns
          Either.Right(
            SynapseAdminDeleteRoom.Response(kickedUsers = emptyList(), localAliases = emptyList())
          )

        synapseService.performOrphanedRoomCleanup(mockChecker)

        coVerify(exactly = 1) { mockSynapseClient.deleteRoom(roomId, purge = true, block = false) }
      }

      should("NOT delete room when joinedMembers > 1 (quick check)") {
        setupLoginMock()
        val mockChecker = mockk<OrphanedRoomChecker>()

        coEvery { mockSynapseClient.listRooms(limit = 100, from = null) } returns
          Either.Right(createRoomsResponse(createRoom(roomId, creator, joinedMembers = 2)))

        synapseService.performOrphanedRoomCleanup(mockChecker)

        // Should not even call checker methods
        verify(exactly = 0) { mockChecker.checkRoomEvents(any(), any()) }
        coVerify(exactly = 0) { mockSynapseClient.deleteRoom(any(), any(), any()) }
      }

      should("NOT delete room when checkRoomEvents returns false (has non-state events)") {
        setupLoginMock()
        val mockChecker = mockk<OrphanedRoomChecker>()

        coEvery { mockSynapseClient.listRooms(limit = 100, from = null) } returns
          Either.Right(createRoomsResponse(createRoom(roomId, creator, joinedMembers = 1)))

        coEvery { mockSynapseClient.getRoomMessages(roomId, limit = 50) } returns
          Either.Right(createMessagesResponse())

        every { mockChecker.checkRoomEvents(any(), roomId) } returns false

        synapseService.performOrphanedRoomCleanup(mockChecker)

        // Should not proceed to check members or timestamp
        verify(exactly = 0) { mockChecker.checkRoomMembers(any(), any(), any()) }
        coVerify(exactly = 0) { mockSynapseClient.deleteRoom(any(), any(), any()) }
      }

      should("NOT delete room when checkRoomMembers returns false (other members joined)") {
        setupLoginMock()
        val mockChecker = mockk<OrphanedRoomChecker>()

        coEvery { mockSynapseClient.listRooms(limit = 100, from = null) } returns
          Either.Right(createRoomsResponse(createRoom(roomId, creator, joinedMembers = 1)))

        coEvery { mockSynapseClient.getRoomMessages(roomId, limit = 50) } returns
          Either.Right(createMessagesResponse())

        coEvery { mockSynapseClient.getRoomState(roomId) } returns
          Either.Right(SynapseAdminRoomState.Response(emptyList()))

        every { mockChecker.checkRoomEvents(any(), roomId) } returns true
        every { mockChecker.checkRoomMembers(any(), creator, roomId) } returns false

        synapseService.performOrphanedRoomCleanup(mockChecker)

        // Should not proceed to check timestamp
        verify(exactly = 0) { mockChecker.checkLastStateEventTimestamp(any(), any()) }
        coVerify(exactly = 0) { mockSynapseClient.deleteRoom(any(), any(), any()) }
      }

      should("NOT delete room when checkLastStateEventTimestamp returns false (too recent)") {
        setupLoginMock()
        val mockChecker = mockk<OrphanedRoomChecker>()

        coEvery { mockSynapseClient.listRooms(limit = 100, from = null) } returns
          Either.Right(createRoomsResponse(createRoom(roomId, creator, joinedMembers = 1)))

        coEvery { mockSynapseClient.getRoomMessages(roomId, limit = 50) } returns
          Either.Right(createMessagesResponse())

        coEvery { mockSynapseClient.getRoomState(roomId) } returns
          Either.Right(SynapseAdminRoomState.Response(emptyList()))

        every { mockChecker.checkRoomEvents(any(), roomId) } returns true
        every { mockChecker.checkRoomMembers(any(), creator, roomId) } returns true
        every { mockChecker.checkLastStateEventTimestamp(any(), roomId) } returns false

        synapseService.performOrphanedRoomCleanup(mockChecker)

        coVerify(exactly = 0) { mockSynapseClient.deleteRoom(any(), any(), any()) }
      }

      should("skip room when getRoomMessages fails") {
        setupLoginMock()
        val mockChecker = mockk<OrphanedRoomChecker>()

        coEvery { mockSynapseClient.listRooms(limit = 100, from = null) } returns
          Either.Right(createRoomsResponse(createRoom(roomId, creator, joinedMembers = 1)))

        coEvery { mockSynapseClient.getRoomMessages(roomId, limit = 50) } returns
          Either.Left(CouldNotGetRoomMessages(roomId, "error"))

        synapseService.performOrphanedRoomCleanup(mockChecker)

        verify(exactly = 0) { mockChecker.checkRoomEvents(any(), any()) }
        coVerify(exactly = 0) { mockSynapseClient.deleteRoom(any(), any(), any()) }
      }

      should("skip room when getRoomState fails") {
        setupLoginMock()
        val mockChecker = mockk<OrphanedRoomChecker>()

        coEvery { mockSynapseClient.listRooms(limit = 100, from = null) } returns
          Either.Right(createRoomsResponse(createRoom(roomId, creator, joinedMembers = 1)))

        coEvery { mockSynapseClient.getRoomMessages(roomId, limit = 50) } returns
          Either.Right(createMessagesResponse())

        coEvery { mockSynapseClient.getRoomState(roomId) } returns
          Either.Left(CouldNotGetRoomState(roomId, "error"))

        every { mockChecker.checkRoomEvents(any(), roomId) } returns true

        synapseService.performOrphanedRoomCleanup(mockChecker)

        verify(exactly = 0) { mockChecker.checkRoomMembers(any(), any(), any()) }
        coVerify(exactly = 0) { mockSynapseClient.deleteRoom(any(), any(), any()) }
      }

      should("process multiple rooms and only delete orphaned ones") {
        setupLoginMock()
        val mockChecker = mockk<OrphanedRoomChecker>()

        val orphanedRoom = "!orphaned:snpse.org"
        val activeRoom = "!active:snpse.org"

        coEvery { mockSynapseClient.listRooms(limit = 100, from = null) } returns
          Either.Right(
            createRoomsResponse(
              createRoom(orphanedRoom, creator, joinedMembers = 1),
              createRoom(activeRoom, creator, joinedMembers = 1),
            )
          )

        // Orphaned room - all checks pass
        coEvery { mockSynapseClient.getRoomMessages(orphanedRoom, limit = 50) } returns
          Either.Right(createMessagesResponse())
        coEvery { mockSynapseClient.getRoomState(orphanedRoom) } returns
          Either.Right(SynapseAdminRoomState.Response(emptyList()))
        every { mockChecker.checkRoomEvents(any(), orphanedRoom) } returns true
        every { mockChecker.checkRoomMembers(any(), creator, orphanedRoom) } returns true
        every { mockChecker.checkLastStateEventTimestamp(any(), orphanedRoom) } returns true

        // Active room - timestamp check fails
        coEvery { mockSynapseClient.getRoomMessages(activeRoom, limit = 50) } returns
          Either.Right(createMessagesResponse())
        coEvery { mockSynapseClient.getRoomState(activeRoom) } returns
          Either.Right(SynapseAdminRoomState.Response(emptyList()))
        every { mockChecker.checkRoomEvents(any(), activeRoom) } returns true
        every { mockChecker.checkRoomMembers(any(), creator, activeRoom) } returns true
        every { mockChecker.checkLastStateEventTimestamp(any(), activeRoom) } returns false

        coEvery { mockSynapseClient.deleteRoom(orphanedRoom, purge = true, block = false) } returns
          Either.Right(
            SynapseAdminDeleteRoom.Response(kickedUsers = emptyList(), localAliases = emptyList())
          )

        synapseService.performOrphanedRoomCleanup(mockChecker)

        // Only orphaned room should be deleted
        coVerify(exactly = 1) {
          mockSynapseClient.deleteRoom(orphanedRoom, purge = true, block = false)
        }
        coVerify(exactly = 0) { mockSynapseClient.deleteRoom(activeRoom, any(), any()) }
      }
    }
  })
