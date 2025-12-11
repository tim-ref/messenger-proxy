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
package de.akquinet.tim.proxy.synapse.client

import com.sksamuel.hoplite.Secret
import de.akquinet.tim.proxy.config.SynapseClientConfig
import de.akquinet.tim.proxy.error.CouldNotGetRoomDetails
import de.akquinet.tim.proxy.synapse.client.resources.JoinRules
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.matchers.uri.shouldHaveHost
import io.kotest.matchers.uri.shouldHavePath
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.headersOf
import io.ktor.http.toURI
import kotlinx.coroutines.runBlocking

class SynapseClientTest :
  ShouldSpec({
    val clientConfig =
      SynapseClientConfig(
        matrixDomain = "snpse.org",
        baseUrl = "https://snpse.org/",
        username = Secret("username"),
        password = Secret("password"),
      )

    should("login") {
      runBlocking {
        val mockEngine = MockEngine { request ->
          val uri = request.url.toURI()

          request.method shouldBe Post
          uri shouldHaveHost "snpse.org"
          uri shouldHavePath "/_matrix/client/v3/login"

          respondOkJson(
            """
                    {
                      "access_token": "abc123",
                      "device_id": "GHTYAJCE",
                      "expires_in_ms": 60000,
                      "refresh_token": "def456",
                      "user_id": "@cheeky_monkey:matrix.org",
                      "well_known": {
                        "m.homeserver": {
                          "base_url": "https://example.org"
                        },
                        "m.identity_server": {
                          "base_url": "https://id.example.org"
                        }
                      }
                    }
                """
          )
        }
        val apiClient = SynapseClient(mockEngine, clientConfig)

        apiClient.login("username", "password")
      }
    }

    context("getRoomDetails") {
      should("return details of a room") {
        runBlocking {
          val mockEngine = MockEngine { request ->
            val uri = request.url.toURI()

            request.method shouldBe Get
            uri shouldHaveHost "snpse.org"
            uri shouldHavePath "/_synapse/admin/v1/rooms/!mscvqgqpHYjBGDxNym:snpse.org"
            request.headers.contains(Authorization, "Bearer access token").shouldBeTrue()

            respondOkJson(
              """
                    {
                      "room_id": "!mscvqgqpHYjBGDxNym:snpse.org",
                      "name": "Music Theory",
                      "avatar": "mxc://snpse.org/AQDaVFlbkQoErdOgqWRgiGSV",
                      "topic": "Theory, Composition, Notation, Analysis",
                      "canonical_alias": "#musictheory:snpse.org",
                      "joined_members": 127,
                      "joined_local_members": 2,
                      "joined_local_devices": 2,
                      "version": "1",
                      "creator": "@foo:snpse.org",
                      "encryption": null,
                      "federatable": true,
                      "public": true,
                      "join_rules": "invite",
                      "guest_access": null,
                      "history_visibility": "shared",
                      "state_events": 93534,
                      "room_type": "m.space",
                      "forgotten": false
                    }
                """
                .trimIndent()
            )
          }
          val apiClient = SynapseClient(mockEngine, clientConfig)
          apiClient.bearerToken = "access token"

          val result = apiClient.getRoomDetails("!mscvqgqpHYjBGDxNym:snpse.org")
          result shouldBeRight JoinRules.INVITE
        }
      }

      should("return error if something goes wrong") {
        val mockEngine = MockEngine { _ -> respond(content = "", status = OK) }
        val apiClient = SynapseClient(mockEngine, clientConfig)

        val result = apiClient.getRoomDetails("!mscvqgqpHYjBGDxNym:snpse.org")

        result.shouldBeLeft().apply {
            details shouldBe
                    "Could not use RoomDetails API, cause: lateinit property bearerToken has not been initialized"
        }
      }

      should("return error if room not found") {
        val mockEngine = MockEngine { _ ->
          respond(content = "Test NotFound", status = HttpStatusCode.NotFound)
        }
        val apiClient = SynapseClient(mockEngine, clientConfig)
        apiClient.bearerToken = "access token"

        val result = apiClient.getRoomDetails("!mscvqgqpHYjBGDxNym:snpse.org")

        result.shouldBeLeft().apply {
            shouldBeTypeOf< CouldNotGetRoomDetails>()
            details shouldBe "Could not get details of room: !mscvqgqpHYjBGDxNym:snpse.org, cause: Test NotFound"
            httpStatusCode shouldBe HttpStatusCode.BadGateway
        }
      }
    }
  })

fun MockRequestHandleScope.respondOkJson(content: String) =
  respond(
      content = content,
      status = OK,
      headers = headersOf(ContentType, "application/json")
  )
