/*
 * Copyright © 2023 - 2025 akquinet GmbH (https://www.akquinet.de)
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
package de.akquinet.tim.proxy.federation.testutils

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMessageBuilder
import io.ktor.http.contentType

fun HttpMessageBuilder.matrixAuthorizationHeader() {
    header(
        Authorization,
        """X-Matrix origin="fed",destination="otherHost:80",key="ed25519:ABC",sig="signature""""
    )
}

suspend fun HttpClient.postKeyClaimAuthenticated() =
    post("/_matrix/federation/v1/user/keys/claim?test=test") {
        matrixAuthorizationHeader()
        contentType(Application.Json)
        setBody("""{"one_time_keys":{}}""")
    }

suspend fun HttpClient.getEventAuthenticated() =
    get("/_matrix/federation/v1/event/1234") {
        matrixAuthorizationHeader()
    }

suspend fun HttpClient.putInvite(sender: String, invited: String) =
    put("/_matrix/federation/v2/invite/{roomId}/{eventId}") {
        matrixAuthorizationHeader()
        contentType(Application.Json)
        setBody(
            """
                    {
                      "event": {
                        "content": {
                          "membership": "invite"
                        },
                        "origin": "matrix.org",
                        "origin_server_ts": 1234567890,
                        "sender": "$sender",
                        "state_key": "$invited",
                        "type": "m.room.member"
                      },
                      "invite_room_state": [
                        {
                          "content": {
                            "name": "Example Room"
                          },
                          "sender": "@bob:example.org",
                          "state_key": "",
                          "type": "m.room.name"
                        },
                        {
                          "content": {
                            "join_rule": "invite"
                          },
                          "sender": "@bob:example.org",
                          "state_key": "",
                          "type": "m.room.join_rules"
                        }
                      ],
                      "room_version": "2"
                    }
                """.trimIndent()
        )
    }

suspend fun HttpClient.sendJoin(roomAlias: String) =
  put("/_matrix/federation/v2/send_join/$roomAlias:myServer.com/1234") {
    header(
      Authorization,
      """X-Matrix origin="fed",destination="myServer:80",key="ed25519:ABC",sig="signature"""",
    )
    contentType(Application.Json)
    setBody(
      """
      {
        "content": {
          "membership": "join"
        },
        "origin": "matrix.org",
        "origin_server_ts": 1234567890,
        "sender": "@someone:example.org",
        "state_key": "@someone:example.org",
        "type": "m.room.member"
      }
      """
        .trimIndent()
    )
  }

suspend fun HttpClient.makeJoin(roomAlias: String) =
  put("/_matrix/federation/v2/send_join/$roomAlias:myServer.com/1234") {
    header(
      Authorization,
      """X-Matrix origin="fed",destination="myServer:80",key="ed25519:ABC",sig="signature"""",
    )
  }
