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
package de.akquinet.tim.proxy.synapse.client

import com.sksamuel.hoplite.Secret
import de.akquinet.tim.proxy.config.SynapseClientConfig
import de.akquinet.tim.proxy.outcomes.CouldNotGetAccountData
import de.akquinet.tim.proxy.outcomes.CouldNotGetEventDetails
import de.akquinet.tim.proxy.outcomes.GetAccountDataApiFailure
import de.akquinet.tim.proxy.outcomes.GetEventApiFailure
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminAccountData
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.matchers.uri.shouldHaveHost
import io.kotest.matchers.uri.shouldHavePath
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.headersOf
import io.ktor.http.toURI
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject

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

    context("getEvent") {
      should("return event of a room") {
        runBlocking {
          val mockEngine = MockEngine { request ->
            val uri = request.url.toURI()

            request.method shouldBe Get
            uri shouldHaveHost "snpse.org"
            uri shouldHavePath
              "/_synapse/admin/v1/rooms/!mscvqgqpHYjBGDxNym:snpse.org/context/$143273582443PhrSn:example.org"
            request.headers.contains(Authorization, "Bearer access token").shouldBeTrue()

            respondOkJson(
              """
                              {
                "events_before": [],
                "event": {
                  "type": "m.room.encrypted",
                  "room_id": "!lkIbXQckzVHBCviOHc:pro-1.ru.tim.akquinet.nx2.dev",
                  "sender": "@test6:pro-1.ru.tim.akquinet.nx2.dev",
                  "content": {},
                  "origin_server_ts": 1764087110403,
                  "unsigned": {
                    "redacted_by": "${'$'}Q5jLZzzKLqk7UqyCChhtqb-PZKNilihWuIa3KMPXIHU",
                    "redacted_because": {
                      "type": "m.room.redaction",
                      "room_id": "!lkIbXQckzVHBCviOHc:pro-1.ru.tim.akquinet.nx2.dev",
                      "sender": "@test6:pro-1.ru.tim.akquinet.nx2.dev",
                      "content": {
                        "redacts": "${'$'}zyKK6S40XMTQldjKtDOjrRs9TTlNoCE5lHCvOiR920I"
                      },
                      "redacts": "${'$'}zyKK6S40XMTQldjKtDOjrRs9TTlNoCE5lHCvOiR920I",
                      "origin_server_ts": 1764087549041,
                      "unsigned": {
                        "age": 80721768
                      },
                      "event_id": "${'$'}Q5jLZzzKLqk7UqyCChhtqb-PZKNilihWuIa3KMPXIHU",
                      "user_id": "@test6:pro-1.ru.tim.akquinet.nx2.dev",
                      "age": 80721768
                    },
                    "age": 81160406
                  },
                  "event_id": "${'$'}zyKK6S40XMTQldjKtDOjrRs9TTlNoCE5lHCvOiR920I",
                  "user_id": "@test6:pro-1.ru.tim.akquinet.nx2.dev",
                  "age": 81160406,
                  "redacted_because": {
                    "type": "m.room.redaction",
                    "room_id": "!lkIbXQckzVHBCviOHc:pro-1.ru.tim.akquinet.nx2.dev",
                    "sender": "@test6:pro-1.ru.tim.akquinet.nx2.dev",
                    "content": {
                      "redacts": "${'$'}zyKK6S40XMTQldjKtDOjrRs9TTlNoCE5lHCvOiR920I"
                    },
                    "redacts": "${'$'}zyKK6S40XMTQldjKtDOjrRs9TTlNoCE5lHCvOiR920I",
                    "origin_server_ts": 1764087549041,
                    "unsigned": {
                      "age": 80721768
                    },
                    "event_id": "${'$'}Q5jLZzzKLqk7UqyCChhtqb-PZKNilihWuIa3KMPXIHU",
                    "user_id": "@test6:pro-1.ru.tim.akquinet.nx2.dev",
                    "age": 80721768
                  }
                },
                "events_after": [],
                "state": [
                  {
                    "type": "m.room.create",
                    "room_id": "!lkIbXQckzVHBCviOHc:pro-1.ru.tim.akquinet.nx2.dev",
                    "sender": "@test6:pro-1.ru.tim.akquinet.nx2.dev",
                    "content": {
                      "type": "de.gematik.tim.roomtype.default.v1",
                      "room_version": "10",
                      "creator": "@test6:pro-1.ru.tim.akquinet.nx2.dev"
                    },
                    "state_key": "",
                    "origin_server_ts": 1764087101298,
                    "unsigned": {
                      "age": 81169511
                    },
                    "event_id": "${'$'}jiQZ270SOfl6fytDvcw2HWCMlzD6MJkICvQa-epZ5_0",
                    "user_id": "@test6:pro-1.ru.tim.akquinet.nx2.dev",
                    "age": 81169511
                  }
                ],
                "start": "t9-8377_0_0_0_0_0_0_0_0_0",
                "end": "t10-8377_0_0_0_0_0_0_0_0_0"
              }
              """
                .trimIndent()
            )
          }
          val apiClient = SynapseClient(mockEngine, clientConfig)
          apiClient.bearerToken = "access token"

          val result =
            apiClient.getEventTimestamp(
              "!mscvqgqpHYjBGDxNym:snpse.org",
              "$143273582443PhrSn:example.org",
            )
          result shouldBeRight 1764087110403
        }
      }

      should("return error if something goes wrong") {
        val mockEngine = MockEngine { _ -> respond(content = "", status = OK) }
        val apiClient = SynapseClient(mockEngine, clientConfig)

        val result =
          apiClient.getEventTimestamp(
            "!mscvqgqpHYjBGDxNym:snpse.org",
            "$143273582443PhrSn:example.org",
          )

        result.shouldBeLeft().apply { shouldBeTypeOf<GetEventApiFailure>() }
      }

      should("return error if event not found") {
        val mockEngine = MockEngine { _ ->
          respond(content = "Test NotFound", status = HttpStatusCode.NotFound)
        }
        val apiClient = SynapseClient(mockEngine, clientConfig)
        apiClient.bearerToken = "access token"

        val result =
          apiClient.getEventTimestamp(
            "!mscvqgqpHYjBGDxNym:snpse.org",
            "$143273582443PhrSn:example.org",
          )

        result.shouldBeLeft().apply {
          shouldBeTypeOf<CouldNotGetEventDetails>()
          httpStatusCode shouldBe HttpStatusCode.BadGateway
        }
      }
    }

    context("Room Admin API (A_28564-01)") {
      should("list rooms with pagination") {
        runBlocking {
          val mockEngine = MockEngine { request ->
            val uri = request.url.toURI()

            request.method shouldBe Get
            uri shouldHaveHost "snpse.org"
            uri shouldHavePath "/_synapse/admin/v1/rooms"
            request.headers.contains(Authorization, "Bearer test-token").shouldBeTrue()

            respondOkJson(
              """
              {
                "rooms": [
                  {
                    "room_id": "!orphaned:snpse.org",
                    "name": "Orphaned Room",
                    "creator": "@creator:snpse.org",
                    "joined_members": 1
                  }
                ],
                "next_batch": null,
                "total_rooms": 1
              }
              """
            )
          }

          val apiClient = SynapseClient(mockEngine, clientConfig)
          apiClient.bearerToken = "test-token"

          val result = apiClient.listRooms()
          result.isRight() shouldBe true
          val response = result.getOrNull()!!
          response.rooms.size shouldBe 1
          response.totalRooms shouldBe 1
        }
      }

      should("get room state") {
        runBlocking {
          val mockEngine = MockEngine { request ->
            val uri = request.url.toURI()

            request.method shouldBe Get
            uri shouldHaveHost "snpse.org"
            uri shouldHavePath "/_synapse/admin/v1/rooms/!test:snpse.org/state"
            request.headers.contains(Authorization, "Bearer test-token").shouldBeTrue()

            respondOkJson(
              """
              {
                "state": [
                  {
                    "type": "m.room.create",
                    "state_key": "",
                    "content": {"creator": "@creator:snpse.org"},
                    "origin_server_ts": 1707868800000,
                    "sender": "@creator:snpse.org"
                  },
                  {
                    "type": "m.room.member",
                    "state_key": "@creator:snpse.org",
                    "content": {"membership": "join"},
                    "origin_server_ts": 1707868800000,
                    "sender": "@creator:snpse.org"
                  }
                ]
              }
              """
            )
          }

          val apiClient = SynapseClient(mockEngine, clientConfig)
          apiClient.bearerToken = "test-token"

          val result = apiClient.getRoomState("!test:snpse.org")
          result.isRight() shouldBe true
          result.getOrNull()!!.state.size shouldBe 2
        }
      }

      should("get room messages") {
        runBlocking {
          val mockEngine = MockEngine { request ->
            val uri = request.url.toURI()

            request.method shouldBe Get
            uri shouldHaveHost "snpse.org"
            uri shouldHavePath "/_synapse/admin/v1/rooms/!test:snpse.org/messages"
            request.headers.contains(Authorization, "Bearer test-token").shouldBeTrue()

            respondOkJson("""{"chunk": [], "start": null, "end": null}""")
          }

          val apiClient = SynapseClient(mockEngine, clientConfig)
          apiClient.bearerToken = "test-token"

          val result = apiClient.getRoomMessages("!test:snpse.org")
          result.isRight() shouldBe true
          result.getOrNull()!!.chunk shouldBe emptyList()
        }
      }

      should("delete room") {
        runBlocking {
          val mockEngine = MockEngine { request ->
            val uri = request.url.toURI()

            request.method shouldBe Delete
            uri shouldHaveHost "snpse.org"
            uri shouldHavePath "/_synapse/admin/v1/rooms/!test:snpse.org"
            request.headers.contains(Authorization, "Bearer test-token").shouldBeTrue()

            respondOkJson("""{"kicked_users": ["@creator:snpse.org"], "local_aliases": []}""")
          }

          val apiClient = SynapseClient(mockEngine, clientConfig)
          apiClient.bearerToken = "test-token"

          val result = apiClient.deleteRoom("!test:snpse.org")
          result.isRight() shouldBe true
          result.getOrNull()!!.kickedUsers shouldBe listOf("@creator:snpse.org")
        }
      }

      should("return error on failed room listing") {
        runBlocking {
          val mockEngine = MockEngine { _ ->
            respond(content = "Unauthorized", status = HttpStatusCode.Unauthorized)
          }

          val apiClient = SynapseClient(mockEngine, clientConfig)
          apiClient.bearerToken = "invalid-token"

          val result = apiClient.listRooms()
          result.isLeft() shouldBe true
        }
      }
    }

    context("getAccountData") {
      val userId = "@test:snpse.org"
      should("return account data for user") {
        // TODO: Put more permission config details into string
        val responseBodyOK =
          """
                                        {
              "account_data": {
                  "global": {},
                  "rooms": {},
                  "de.gematik.tim.account.permissionconfig.pro.v1": {}
              }
          }
          """
            .trimIndent()
        val mockEngine = MockEngine { request ->
          val uri = request.url.toURI()

          request.method shouldBe Get
          uri shouldHaveHost "snpse.org"
          uri shouldHavePath "/_synapse/admin/v1/users/${userId}/accountdata"
          request.headers.contains(Authorization, "Bearer access token").shouldBeTrue()

          respondOkJson(responseBodyOK)
        }
        val apiClient = SynapseClient(mockEngine, clientConfig)
        apiClient.bearerToken = "access token"

        val result = apiClient.getAccountData(userId)
        result.shouldBeRight().apply {
          shouldBeTypeOf<SynapseAdminAccountData.Response>()
          accountData shouldNotBe null
          accountData["global"] shouldNotBe null
          accountData["rooms"] shouldNotBe null
          val gematikPermissionConfig =
            accountData["de.gematik.tim.account.permissionconfig.pro.v1"]?.jsonObject
          gematikPermissionConfig shouldNotBe null
        }
      }

      should("return GetAccountDataApiFailure if api throws an exception") {
        val mockEngine = MockEngine { _ -> respond(content = "", status = OK) }
        val apiClient = SynapseClient(mockEngine, clientConfig)

        val result = apiClient.getAccountData(userId)

        result.shouldBeLeft().apply { shouldBeTypeOf<GetAccountDataApiFailure>() }
      }

      should("return CouldNotGetAccountData if response is not successful") {
        val mockEngine = MockEngine { _ ->
          respond(content = "Test NotFound", status = HttpStatusCode.NotFound)
        }
        val apiClient = SynapseClient(mockEngine, clientConfig)
        apiClient.bearerToken = "access token"

        val result = apiClient.getAccountData(userId)

        result.shouldBeLeft().apply {
          shouldBeTypeOf<CouldNotGetAccountData>()
          httpStatusCode shouldBe HttpStatusCode.BadGateway
        }
      }
    }
  })

fun MockRequestHandleScope.respondOkJson(content: String) =
  respond(content = content, status = OK, headers = headersOf(ContentType, "application/json"))
