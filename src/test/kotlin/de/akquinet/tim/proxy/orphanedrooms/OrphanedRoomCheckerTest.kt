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
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class OrphanedRoomCheckerTest :
  ShouldSpec({
    val cleanupConfig =
      ProxyConfiguration.OrphanedRoomCleanupConfig(
        enabled = true,
        checkIntervalDays = 1,
        roomAgeThresholdDays = 14,
      )

    // Fixed time: March 1, 2024
    val fixedClock = Clock.fixed(Instant.parse("2024-03-01T00:00:00Z"), ZoneId.of("UTC"))

    // Timestamp older than 14 days: Feb 14, 2024 (15 days before March 1 = 1 day past threshold)
    val oldTimestamp = Instant.parse("2024-02-14T00:00:00Z").toEpochMilli()

    // Timestamp newer than 14 days: Feb 17, 2024 (13 days before March 1 = 1 day within threshold)
    val recentTimestamp = Instant.parse("2024-02-17T00:00:00Z").toEpochMilli()

    val checker = OrphanedRoomCheckerImpl(cleanupConfig, fixedClock)

    fun createStateEvent(
      type: String,
      stateKey: String?,
      content: Map<String, String>,
      originServerTs: Long,
      sender: String,
    ): SynapseAdminRoomState.StateEvent {
      val jsonContent = JsonObject(content.mapValues { JsonPrimitive(it.value) })
      return SynapseAdminRoomState.StateEvent(
        type = type,
        stateKey = stateKey,
        content = jsonContent,
        originServerTs = originServerTs,
        sender = sender,
      )
    }

    fun createRoomEvent(
      type: String,
      stateKey: String?,
      content: Map<String, String>,
      originServerTs: Long,
      sender: String,
      eventId: String,
    ): SynapseAdminRoomMessages.RoomEvent {
      val jsonContent = JsonObject(content.mapValues { JsonPrimitive(it.value) })
      return SynapseAdminRoomMessages.RoomEvent(
        type = type,
        stateKey = stateKey,
        content = jsonContent,
        originServerTs = originServerTs,
        sender = sender,
        eventId = eventId,
      )
    }

    context("checkRoomMembers") {
      should("return true when only creator is joined") {
        val creator = "@creator:test.org"

        val state =
          listOf(
            createStateEvent(
              type = "m.room.create",
              stateKey = "",
              content = mapOf("creator" to creator),
              originServerTs = oldTimestamp,
              sender = creator,
            ),
            createStateEvent(
              type = "m.room.member",
              stateKey = creator,
              content = mapOf("membership" to "join"),
              originServerTs = oldTimestamp,
              sender = creator,
            ),
          )

        val result = checker.checkRoomMembers(state, creator, "!test:test.org")
        result shouldBe true
      }

      should("return false when multiple members are joined") {
        val creator = "@creator:test.org"
        val otherUser = "@other:test.org"

        val state =
          listOf(
            createStateEvent(
              type = "m.room.member",
              stateKey = creator,
              content = mapOf("membership" to "join"),
              originServerTs = oldTimestamp,
              sender = creator,
            ),
            createStateEvent(
              type = "m.room.member",
              stateKey = otherUser,
              content = mapOf("membership" to "join"),
              originServerTs = oldTimestamp,
              sender = otherUser,
            ),
          )

        val result = checker.checkRoomMembers(state, creator, "!test:test.org")
        result shouldBe false
      }

      should("return false when member other than creator has joined") {
        val creator = "@creator:test.org"
        val otherUser = "@other:test.org"

        val state =
          listOf(
            createStateEvent(
              type = "m.room.member",
              stateKey = creator,
              content = mapOf("membership" to "leave"),
              originServerTs = oldTimestamp,
              sender = creator,
            ),
            createStateEvent(
              type = "m.room.member",
              stateKey = otherUser,
              content = mapOf("membership" to "join"),
              originServerTs = oldTimestamp,
              sender = otherUser,
            ),
          )

        val result = checker.checkRoomMembers(state, creator, "!test:test.org")
        result shouldBe false
      }

      should("return true when no members are joined") {
        val creator = "@creator:test.org"

        val state =
          listOf(
            createStateEvent(
              type = "m.room.member",
              stateKey = creator,
              content = mapOf("membership" to "leave"),
              originServerTs = oldTimestamp,
              sender = creator,
            )
          )

        val result = checker.checkRoomMembers(state, creator, "!test:test.org")
        result shouldBe true
      }
    }

    context("checkRoomEvents") {
      should("return true when room has only state events") {
        val messages = SynapseAdminRoomMessages.Response(chunk = emptyList())

        val result = checker.checkRoomEvents(messages, "!orphaned:test.org")
        result shouldBe true
      }

      should("return false when room has non-state message events") {
        val creator = "@creator:test.org"

        val messages =
          SynapseAdminRoomMessages.Response(
            chunk =
              listOf(
                createRoomEvent(
                  type = "m.room.message",
                  stateKey = null,
                  content = mapOf("body" to "Hello world", "msgtype" to "m.text"),
                  originServerTs = oldTimestamp,
                  sender = creator,
                  eventId = "\$event1:test.org",
                )
              )
          )

        val result = checker.checkRoomEvents(messages, "!withmessages:test.org")
        result shouldBe false
      }

      should("return true when room has state events only") {
        val creator = "@creator:test.org"

        val messages =
          SynapseAdminRoomMessages.Response(
            chunk =
              listOf(
                createRoomEvent(
                  type = "m.room.member",
                  stateKey = creator,
                  content = mapOf("membership" to "join"),
                  originServerTs = oldTimestamp,
                  sender = creator,
                  eventId = "\$event1:test.org",
                )
              )
          )

        val result = checker.checkRoomEvents(messages, "!stateonly:test.org")
        result shouldBe true
      }
    }

    context("checkLastStateEventTimestamp") {
      should("return true when last event is older than threshold") {
        val creator = "@creator:test.org"

        val state =
          listOf(
            createStateEvent(
              type = "m.room.create",
              stateKey = "",
              content = mapOf("creator" to creator),
              originServerTs = oldTimestamp,
              sender = creator,
            )
          )

        val result = checker.checkLastStateEventTimestamp(state, "!old:test.org")
        result shouldBe true
      }

      should("return false when last event is newer than threshold") {
        val creator = "@creator:test.org"

        val state =
          listOf(
            createStateEvent(
              type = "m.room.create",
              stateKey = "",
              content = mapOf("creator" to creator),
              originServerTs = recentTimestamp,
              sender = creator,
            )
          )

        val result = checker.checkLastStateEventTimestamp(state, "!recent:test.org")
        result shouldBe false
      }

      should("return false when state is empty") {
        val result = checker.checkLastStateEventTimestamp(emptyList(), "!empty:test.org")
        result shouldBe false
      }

      should("use latest timestamp when multiple events present") {
        val creator = "@creator:test.org"

        val state =
          listOf(
            createStateEvent(
              type = "m.room.create",
              stateKey = "",
              content = mapOf("creator" to creator),
              originServerTs = oldTimestamp,
              sender = creator,
            ),
            createStateEvent(
              type = "m.room.member",
              stateKey = creator,
              content = mapOf("membership" to "join"),
              originServerTs = recentTimestamp,
              sender = creator,
            ),
          )

        val result = checker.checkLastStateEventTimestamp(state, "!mixed:test.org")
        result shouldBe false // Recent timestamp should make it not qualify
      }
    }
  })
