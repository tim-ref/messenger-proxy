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
package de.akquinet.tim.proxy.client.model

import de.akquinet.tim.proxy.client.model.CreateRoomRequestWithPrimitiveInitialState.Preset.PRIVATE
import de.akquinet.tim.proxy.client.model.CreateRoomRequestWithPrimitiveInitialState.Preset.TRUSTED_PRIVATE
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.clientserverapi.model.rooms.DirectoryVisibility
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent.RoomType.Unknown

class CreateRoomRequestWithPrimitiveInitialStateTest :
  ShouldSpec({
    val json = Json { ignoreUnknownKeys = true }

    should("decode without any errors") {
      val data =
        """
        {
            "visibility": "private",
            "name": "Test",
            "topic": "Test",
            "invite": [],
            "creation_content": {
                "type": "de.gematik.tim.roomtype.default.v1"
            },
            "initial_state": [
                {
                    "content": {
                        "algorithm": "m.megolm.v1.aes-sha2"
                    },
                    "state_key": "",
                    "type": "m.room.encryption"
                },
                {
                    "content": {
                        "history_visibility": "invited"
                    },
                    "state_key": "",
                    "type": "m.room.history_visibility"
                },
                {
                    "content": {
                        "name": "Test"
                    },
                    "state_key": "",
                    "type": "de.gematik.tim.room.name"
                },
                {
                    "content": {
                        "topic": "Test"
                    },
                    "state_key": "",
                    "type": "de.gematik.tim.room.topic"
                }
            ],
            "preset": "private_chat",
            "is_direct": false
        }
        """
          .trimIndent()

      val result = json.decodeFromString<CreateRoomRequestWithPrimitiveInitialState>(data)

      result shouldBe
        CreateRoomRequestWithPrimitiveInitialState(
          visibility = DirectoryVisibility.PRIVATE,
          roomAliasLocalPart = null,
          name = "Test",
          topic = "Test",
          invite = setOf(),
          inviteThirdPid = null,
          roomVersion = null,
          creationContent =
            CreateEventContent(
              creator = null,
              federate = true,
              roomVersion = "1",
              predecessor = null,
              type = Unknown(name = "de.gematik.tim.roomtype.default.v1"),
              externalUrl = null,
            ),
          preset = PRIVATE,
          isDirect = false,
          powerLevelContentOverride = null,
          initialState =
            listOf(
              JsonObject(
                mapOf(
                  "content" to
                    JsonObject(mapOf("algorithm" to JsonPrimitive("m.megolm.v1.aes-sha2"))),
                  "state_key" to JsonPrimitive(""),
                  "type" to JsonPrimitive("m.room.encryption"),
                )
              ),
              JsonObject(
                mapOf(
                  "content" to JsonObject(mapOf("history_visibility" to JsonPrimitive("invited"))),
                  "state_key" to JsonPrimitive(""),
                  "type" to JsonPrimitive("m.room.history_visibility"),
                )
              ),
              JsonObject(
                mapOf(
                  "content" to JsonObject(mapOf("name" to JsonPrimitive("Test"))),
                  "state_key" to JsonPrimitive(""),
                  "type" to JsonPrimitive("de.gematik.tim.room.name"),
                )
              ),
              JsonObject(
                mapOf(
                  "content" to JsonObject(mapOf("topic" to JsonPrimitive("Test"))),
                  "state_key" to JsonPrimitive(""),
                  "type" to JsonPrimitive("de.gematik.tim.room.topic"),
                )
              ),
            ),
        )
    }

    should("encode with correct initial states") {
      val data =
        CreateRoomRequestWithPrimitiveInitialState(
          visibility = DirectoryVisibility.PUBLIC,
          name = "Test-1",
          topic = "Test-1",
          invite = setOf(),
          roomVersion = "2",
          creationContent =
            CreateEventContent(
              creator = UserId("@test:example.org"),
              federate = false,
              predecessor =
                CreateEventContent.PreviousRoom(
                  roomId = RoomId("!room:example.org"),
                  eventId = EventId("!event1:example.org"),
                ),
              type = Unknown(name = "de.gematik.tim.roomtype.default.v2"),
            ),
          initialState =
            listOf(
              JsonObject(
                mapOf(
                  "content" to
                    JsonObject(mapOf("algorithm" to JsonPrimitive("m.megolm.v1.aes-sha2"))),
                  "state_key" to JsonPrimitive(""),
                  "type" to JsonPrimitive("m.room.encryption"),
                )
              ),
              JsonObject(
                mapOf(
                  "content" to JsonObject(mapOf("history_visibility" to JsonPrimitive("invited"))),
                  "state_key" to JsonPrimitive(""),
                  "type" to JsonPrimitive("m.room.history_visibility"),
                )
              ),
              JsonObject(
                mapOf(
                  "content" to JsonObject(mapOf("name" to JsonPrimitive("Test-1"))),
                  "state_key" to JsonPrimitive(""),
                  "type" to JsonPrimitive("de.gematik.tim.room.name"),
                )
              ),
              JsonObject(
                mapOf(
                  "content" to JsonObject(mapOf("topic" to JsonPrimitive("Test-1"))),
                  "state_key" to JsonPrimitive(""),
                  "type" to JsonPrimitive("de.gematik.tim.room.topic"),
                )
              ),
            ),
          preset = TRUSTED_PRIVATE,
          isDirect = false,
        )

      val result = json.encodeToString(data)
      result shouldBe
        """
        {
            "visibility": "public",
            "name": "Test-1",
            "topic": "Test-1",
            "invite": [],
            "room_version":"2",
            "creation_content":{
              "creator":"@test:example.org",
              "m.federate":false,
              "predecessor":{
                "room_id":"!room:example.org",
                "event_id":"!event1:example.org"
              },
              "type":"de.gematik.tim.roomtype.default.v2"
            },
            "initial_state": [
                {
                    "content": {
                        "algorithm": "m.megolm.v1.aes-sha2"
                    },
                    "state_key": "",
                    "type": "m.room.encryption"
                },
                {
                    "content": {
                        "history_visibility": "invited"
                    },
                    "state_key": "",
                    "type": "m.room.history_visibility"
                },
                {
                    "content": {
                        "name": "Test-1"
                    },
                    "state_key": "",
                    "type": "de.gematik.tim.room.name"
                },
                {
                    "content": {
                        "topic": "Test-1"
                    },
                    "state_key": "",
                    "type": "de.gematik.tim.room.topic"
                }
            ],
            "preset": "trusted_private_chat",
            "is_direct": false
        }
        """
          .trimIndent()
          .replace(Regex("""(\s+|\n|\r)"""), "")
    }
    should("match when decoded and encoded without change") {
      val data =
        """
        {
            "visibility": "private",
            "name": "Test-2",
            "topic": "Topic-2",
            "invite": [],
            "room_version":"1",
            "creation_content":{
              "creator":"@test:example.org",
              "predecessor":{
                "room_id":"!room1:example.org",
                "event_id":"!event2:example.org"
              },
              "type":"de.gematik.tim.roomtype.default.v1"
            },
            "initial_state": [
                {
                    "content": {
                        "algorithm": "m.megolm.v1.aes-sha2"
                    },
                    "state_key": "",
                    "type": "m.room.encryption"
                },
                {
                    "content": {
                        "history_visibility": "invited"
                    },
                    "state_key": "",
                    "type": "m.room.history_visibility"
                },
                {
                    "content": {
                        "name": "Test-1"
                    },
                    "state_key": "",
                    "type": "de.gematik.tim.room.name"
                },
                {
                    "content": {
                        "topic": "Test-1"
                    },
                    "state_key": "",
                    "type": "de.gematik.tim.room.topic"
                }
            ],
            "preset": "private_chat",
            "is_direct": true
        }
        """
          .trimIndent()
          .replace(Regex("""(\s+|\n|\r)"""), "")

      // visibility:"private" is the default value and will not be re-encoded
      val expectedOutput =
        """
        {
            "name": "Test-2",
            "topic": "Topic-2",
            "invite": [],
            "room_version":"1",
            "creation_content":{
              "creator":"@test:example.org",
              "predecessor":{
                "room_id":"!room1:example.org",
                "event_id":"!event2:example.org"
              },
              "type":"de.gematik.tim.roomtype.default.v1"
            },
            "initial_state": [
                {
                    "content": {
                        "algorithm": "m.megolm.v1.aes-sha2"
                    },
                    "state_key": "",
                    "type": "m.room.encryption"
                },
                {
                    "content": {
                        "history_visibility": "invited"
                    },
                    "state_key": "",
                    "type": "m.room.history_visibility"
                },
                {
                    "content": {
                        "name": "Test-1"
                    },
                    "state_key": "",
                    "type": "de.gematik.tim.room.name"
                },
                {
                    "content": {
                        "topic": "Test-1"
                    },
                    "state_key": "",
                    "type": "de.gematik.tim.room.topic"
                }
            ],
            "preset": "private_chat",
            "is_direct": true
        }
        """
          .trimIndent()
          .replace(Regex("""(\s+|\n|\r)"""), "")

      val decoded = json.decodeFromString<CreateRoomRequestWithPrimitiveInitialState>(data)
      val result = json.encodeToString(decoded)

      result shouldBe expectedOutput
    }
  })
