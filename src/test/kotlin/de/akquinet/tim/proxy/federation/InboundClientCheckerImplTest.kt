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
package de.akquinet.tim.proxy.federation

import arrow.core.left
import arrow.core.right
import de.akquinet.tim.ErrorResponse
import de.akquinet.tim.proxy.InviteRejectionPolicy
import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.TimAuthorizationCheckConcept
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.client.AccessTokenToUserIdAuthenticationFunction
import de.akquinet.tim.proxy.client.InboundClientRoutesImpl
import de.akquinet.tim.proxy.client.UserIdPrincipal
import de.akquinet.tim.proxy.enforcer.RequestPolicyEnforcer
import de.akquinet.tim.proxy.mocks.FederationListCacheMock
import de.akquinet.tim.proxy.outcomes.InviteBlocked
import de.akquinet.tim.proxy.outcomes.ValidationSuccess
import de.akquinet.tim.proxy.rawdata.RawDataServiceImpl
import de.akquinet.tim.proxy.rawdata.model.RawDataMetaData
import de.akquinet.tim.proxy.validation.RequestContentValidator
import de.akquinet.tim.proxy.validation.SynapseAdminAPIValidator
import de.akquinet.tim.shouldEqualJsonMatrixStandard
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.clientserverapi.model.rooms.InviteUser
import net.folivo.trixnity.clientserverapi.server.AccessTokenAuthenticationFunctionResult
import net.folivo.trixnity.clientserverapi.server.matrixAccessTokenAuth
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

class InboundClientCheckerImplTest :
  ShouldSpec({
    val synapseDestinationUrl = "https://internal-matrix-server:8090"
    val rawDataServiceUrl = "https://localhost:1234"
    val rawDataPath = "/add-performance-data"
    val loginRequestString =
      """ {
                              "identifier": {
                                "type": "m.id.user",
                                "user": "cheeky_monkey"
                              },
                              "initial_device_display_name": "Jungle Phone",
                              "password": "ilovebananas",
                              "type": "m.login.password"
                            }
                            """
    val loginResponseString =
      """{
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
    val inboundProxyConfig =
      ProxyConfiguration.InboundProxyConfiguration(
        enforceDomainList = true,
        homeserverUrl = synapseDestinationUrl,
        synapseHealthEndpoint = "/health",
        synapsePort = 443,
        port = 8090,
        accessTokenToUserIdCacheDuration = 1.hours,
      )
    val logInfoConfig =
      ProxyConfiguration.LogInfoConfig(
        "$rawDataServiceUrl$rawDataPath",
        "doctor",
        "2384234234",
        "MP-1",
        "example.com",
      )
    val timAuthorizationCheckConfiguration =
      ProxyConfiguration.TimAuthorizationCheckConfiguration(
        concept = TimAuthorizationCheckConcept.PROXY,
        inviteRejectionPolicy = InviteRejectionPolicy.ALLOW_ALL,
      )
    val synapseAdminAPIValidator = mockk<SynapseAdminAPIValidator>(relaxed = true)
    lateinit var rawDataService: RawDataServiceImpl
    lateinit var bsEinsService: BerechtigungsstufeEinsService

    val matrixTokenAuthMock: AccessTokenToUserIdAuthenticationFunction = mockk {}
    val supportedRoomVersions = setOf("9", "10")

    beforeTest {
      coEvery { matrixTokenAuthMock.invoke(any()) } returns
        AccessTokenAuthenticationFunctionResult(
          principal = UserIdPrincipal(UserId(full = "@me:example.com")),
          cause = null,
        )
    }

    fun withCut(block: suspend ApplicationTestBuilder.() -> Unit) {
      testApplication {
        val client = createClient { install(ContentNegotiation) { json() } }

        rawDataService = spyk(RawDataServiceImpl(logInfoConfig, client))

        val flMock = FederationListCacheMock()
        flMock.domains.value =
          setOf(
            FederationList.FederationDomain(
              domain = "example.com",
              isInsurance = true,
              telematikID = "telematik",
            )
          )
        bsEinsService = BerechtigungsstufeEinsService(flMock)

        val inboundClientRoutes =
          InboundClientRoutesImpl(
            config = inboundProxyConfig,
            logConfiguration = logInfoConfig,
            timAuthorizationCheckConfiguration = timAuthorizationCheckConfiguration,
            httpClient = client,
            rawDataService = rawDataService,
            berechtigungsstufeEinsService = bsEinsService,
            regServiceConfig =
              ProxyConfiguration.RegistrationServiceConfiguration(
                baseUrl = "https://reg-service",
                servicePort = "8080",
                healthPort = "8081",
                federationListEndpoint = "/backend/federation",
                readinessEndpoint = "/actuator/health/readiness",
                wellKnownSupportEndpoint = "/backend/well-known-support",
              ),
            requestContentValidator = RequestContentValidator(),
            synapseAdminAPIValidator = synapseAdminAPIValidator,
            requestPolicyEnforcer = RequestPolicyEnforcer(),
          )
        application {
          install(Authentication) {
            berechtigungsstufeEinsCheck(checkerService = bsEinsService) {
              proxyMode = BerechtigungsstufeEinsAuthenticationProvider.ProxyMode.INBOUND
              enforceDomainList = true
            }
            matrixAccessTokenAuth("matrix-access-token-auth") {
              authenticationFunction = matrixTokenAuthMock
            }
          }
          matrixApiServer(Json) {
            authenticate("matrix-access-token-auth") {
              inboundClientRoutes.apply { clientServerApiRoutes() }
            }
          }
        }
        externalServices {
          hosts(synapseDestinationUrl) {
            routing {
              get("/.well-known/matrix/client") { _ ->
                call.respondText(
                  """{"m.homeserver": "homeserver.example.com:1234"}""",
                  Application.Json,
                )
              }
              post("/_matrix/client/v3/login") { _ ->
                call.receiveText() shouldBe loginRequestString
                call.request.contentType() shouldBe Application.Json

                call.respondText(loginResponseString, Application.Json)
              }
              post("/_matrix/client/v3/createRoom") { _ ->
                val response = CreateRoom.Response(roomId = RoomId("123:example.com"))
                call.respondText(Json.encodeToString(response), Application.Json)
              }
              post("/_matrix/client/v3/rooms/123:example.com/invite") { _ ->
                call.respondText("{}", Application.Json)
              }
              post("/_matrix/client/v3/rooms/123:example.com/upgrade") { _ ->
                call.respondText("{}", Application.Json)
              }
              get("/_matrix/client/v3/publicRooms") { _ ->
                call.respondText("{}", Application.Json)
              }
              get("/_matrix/client/v3/directory/list/room/{roomId}") { _ ->
                call.respondText("{}", Application.Json)
              }
            }
          }
          hosts(rawDataServiceUrl) {
            routing {
              post(rawDataPath) {
                Json.decodeFromString<RawDataMetaData>(call.receiveText())
                  .shouldBeTypeOf<RawDataMetaData>()
                call.request.contentType() shouldBe Application.Json
              }
            }
          }
        }
        block()
      }
    }
    context("client routes test") {
      should("get well known") {
        withCut {
          val response = client.get("/.well-known/matrix/client")

          assertSoftly {
            response shouldHaveStatus OK
            response.bodyAsText() shouldEqualJson
              """{"m.homeserver": "homeserver.example.com:1234"}"""
          }
        }
      }

      should("should login with raw data send") {
        withCut {
          val response =
            client.post("/_matrix/client/v3/login") {
              contentType(Application.Json)
              setBody(loginRequestString)
            }

          assertSoftly {
            response shouldHaveStatus OK
            response.bodyAsText() shouldEqualJson loginResponseString
            //                    coVerify (exactly = 1) {
            // rawDataService.sendMessageLog(any<RawDataMetaData>()) }
          }
        }
      }

      should("ignore unknown url") {
        withCut {
          val response = client.get("/blubs")
          response shouldHaveStatus NotFound
          response.bodyAsText() shouldEqualJsonMatrixStandard
            ErrorResponse(errcode = "M_UNRECOGNIZED", error = "unsupported (or unknown) endpoint")
        }
      }
    }

    context("client routes with berechtigungsstufe 1 tests: CreateRoom") {
      val inviter = UserId(full = "@me:example.com")
      val invited = UserId(full = "@you:example.com")
      val createRoomRequest =
        """
        {
            "visibility": "private",
            "creation_content": {
              "creator":"$inviter"
            },
            "invite": ["$invited"],
            "name": "my room"
        }"""
          .trimIndent()

      should("post create room should succeed") {
        withCut {
          val response =
            client.post("/_matrix/client/v3/createRoom") {
              bearerAuth("some.token")
              accept(Application.Json)
              contentType(Application.Json)
              setBody(createRoomRequest)
            }

          assertSoftly {
            response shouldHaveStatus OK
            response.bodyAsText() shouldEqualJson """{"room_id":"123:example.com"}"""
          }
        }
      }

      should("post create room should fail without token") {
        withCut {
          val response = client.post("/_matrix/client/v3/createRoom") { setBody(createRoomRequest) }

          response shouldHaveStatus Unauthorized
        }
      }

      should("post create room should fail with invalid token") {
        coEvery { matrixTokenAuthMock.invoke(any()) } returns
          AccessTokenAuthenticationFunctionResult(
            principal = UserIdPrincipal(UserId(full = "@me:unfederated.com")),
            cause = null,
          )

        withCut {
          val response =
            client.post("/_matrix/client/v3/createRoom") {
              bearerAuth("some.token")
              setBody(createRoomRequest)
            }

          response shouldHaveStatus Forbidden
        }
      }
    }

    context("A_26515-01 - Zutrittsbeschränkung für öffentliche Räume") {
      val inviter = UserId(full = "@me:example.com")
      val invited = UserId(full = "@you:example.com")

      should("forward create public room request containing m.federate=false as it is") {
        val createRoomRequest =
          """
          {
              "visibility": "private",
              "creation_content": {
                "creator":"$inviter",
                "federate":false
              },
              "invite": ["$invited"],
              "name": "my room",
              "preset": "public_chat"
          }"""
            .trimIndent()

        withCut {
          val response =
            client.post("/_matrix/client/v3/createRoom") {
              bearerAuth("some.token")
              accept(Application.Json)
              contentType(Application.Json)
              setBody(createRoomRequest)
            }

          assertSoftly {
            response shouldHaveStatus OK
            response.bodyAsText() shouldEqualJson """{"room_id":"123:example.com"}"""
          }
        }
      }

      should("forward create public room request missing m.federate=false by adding it") {
        val createRoomRequest =
          """
          {
              "visibility": "public",
              "invite": [],
              "name": "my room",
              "preset": "private_chat"
              "topic": "Topic-2",
              "room_version":"9",
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
              "is_direct": true
          }
          """
            .trimIndent()

        withCut {
          val response =
            client.post("/_matrix/client/v3/createRoom") {
              bearerAuth("some.token")
              accept(Application.Json)
              contentType(Application.Json)
              setBody(createRoomRequest)
            }

          assertSoftly {
            response shouldHaveStatus OK
            response.bodyAsText() shouldEqualJson """{"room_id":"123:example.com"}"""
          }
        }
      }

      should("forward create private room request with m.federate=true") {
        val createRoomRequest =
          """
          {
              "invite": ["$invited"],
              "name": "my room",
              "preset": "private_chat",
              "creation_content": {
                "creator":"@test:example.org"
              }
          }"""
            .trimIndent()

        withCut {
          val response =
            client.post("/_matrix/client/v3/createRoom") {
              bearerAuth("some.token")
              accept(Application.Json)
              contentType(Application.Json)
              setBody(createRoomRequest)
            }

          assertSoftly {
            response shouldHaveStatus OK
            response.bodyAsText() shouldEqualJson """{"room_id":"123:example.com"}"""
          }
        }
      }
    }

    context("room type validation") {
      val inviter = UserId(full = "@me:example.com")
      val invited = UserId(full = "@you:example.com")

      should("not create room with gematik v2 room type") {
        withCut {
          val request =
            """
            {
                "visibility": "private",
                "invite": ["$invited"],
                "creation_content": {
                  "creator":"$inviter",
                  "type": "de.gematik.tim.roomtype.default.v2"
                },
                "name": "my room"              
            }"""
              .trimIndent()

          val response =
            client.post("/_matrix/client/v3/createRoom") {
              bearerAuth("some.token")
              accept(Application.Json)
              contentType(Application.Json)
              setBody(request)
            }

          assertSoftly {
            response shouldHaveStatus BadRequest
            response.bodyAsText() shouldContain
              "Room type not permitted: de.gematik.tim.roomtype.default.v2"
          }
        }
      }

      should("create room with gematik v1 room type") {
        withCut {
          val request =
            """
            {
                "visibility": "private",
                "invite": ["$invited"],
                "creation_content": {
                  "creator":"$inviter",
                  "type": "de.gematik.tim.roomtype.default.v1"
                },
                "name": "my room"              
            }"""
              .trimIndent()

          val response =
            client.post("/_matrix/client/v3/createRoom") {
              bearerAuth("some.token")
              accept(Application.Json)
              contentType(Application.Json)
              setBody(request)
            }

          assertSoftly { response shouldHaveStatus OK }
        }
      }

      should("create room without defined room type") {
        withCut {
          val request =
            """
            {
                "visibility": "private",
                "invite": ["$invited"],
                "creation_content": {
                  "creator":"$inviter"                },
                "name": "my room"              
            }"""
              .trimIndent()

          val response =
            client.post("/_matrix/client/v3/createRoom") {
              bearerAuth("some.token")
              accept(Application.Json)
              contentType(Application.Json)
              setBody(request)
            }

          assertSoftly { response shouldHaveStatus OK }
        }
      }
    }

    context("room version validation tests") {
      val inviter = UserId(full = "@me:example.com")
      val invited = UserId(full = "@you:example.com")
      val createRoomRequest =
        """
            {
                "visibility": "private",
                "invite": ["$invited"],
                "creation_content": {
                  "creator":"$inviter"
                },
                "name": "my room",
                "room_version": "to_be_replaced"
            }"""
          .trimIndent()

      val upgradeRoomRequest =
        """
        {
          "new_version": "to_be_replaced"
        }
        """
          .trimIndent()

      should("not create room with invalid room version type") {
        withCut {
          val request = createRoomRequest.replace("\"to_be_replaced\"", "123")

          val response =
            client.post("/_matrix/client/v3/createRoom") {
              bearerAuth("some.token")
              accept(Application.Json)
              contentType(Application.Json)
              setBody(request)
            }

          assertSoftly {
            response shouldHaveStatus BadRequest
            response.bodyAsText() shouldContain "Failed to deserialize request body"
          }
        }
      }

      supportedRoomVersions.forEach { roomVersion ->
        should("post create room should succeed in case of $roomVersion") {
          val validCreateRoomRequest = createRoomRequest.replace("to_be_replaced", roomVersion)
          withCut {
            val response =
              client.post("/_matrix/client/v3/createRoom") {
                bearerAuth("some.token")
                accept(Application.Json)
                contentType(Application.Json)
                setBody(validCreateRoomRequest)
              }

            assertSoftly {
              response shouldHaveStatus OK
              response.bodyAsText() shouldEqualJson """{"room_id":"123:example.com"}"""
            }
          }
        }
      }

      should("post create room should not succeed in case of invalid room version") {
        val invalidRoomVersion = "11"
        val invalidRoomRequest = createRoomRequest.replace("to_be_replaced", invalidRoomVersion)
        withCut {
          val response =
            client.post("/_matrix/client/v3/createRoom") {
              bearerAuth("some.token")
              accept(Application.Json)
              contentType(Application.Json)
              setBody(invalidRoomRequest)
            }

          assertSoftly {
            response shouldHaveStatus BadRequest
            response.bodyAsText() shouldEqualJson
              """{
                        "errcode":"M_UNSUPPORTED_ROOM_VERSION",
                        "error":"Room version $invalidRoomVersion is not supported. Allowed versions are: ${supportedRoomVersions.joinToString()}. - see A_26202, A_26203"
                        }"""
          }
        }
      }

      supportedRoomVersions.forEach { roomVersion ->
        should("post upgrade room should succeed $roomVersion") {
          val validUpgradeRoomRequest = upgradeRoomRequest.replace("to_be_replaced", roomVersion)
          withCut {
            val response =
              client.post("/_matrix/client/v3/rooms/123:example.com/upgrade") {
                bearerAuth("some.token")
                accept(Application.Json)
                contentType(Application.Json)
                setBody(validUpgradeRoomRequest)
              }

            response shouldHaveStatus OK
          }
        }
      }

      should("post upgrade room should not succeed in case of invalid room version") {
        val invalidNewVersion = "11"
        val invalidUpgradeRoomRequest =
          upgradeRoomRequest.replace("to_be_replaced", invalidNewVersion)
        withCut {
          val response =
            client.post("/_matrix/client/v3/rooms/123:example.com/upgrade") {
              bearerAuth("some.token")
              accept(Application.Json)
              contentType(Application.Json)
              setBody(invalidUpgradeRoomRequest)
            }

          assertSoftly {
            response shouldHaveStatus BadRequest
            response.bodyAsText() shouldEqualJson
              """{
                        "errcode":"M_UNSUPPORTED_ROOM_VERSION",
                        "error":"Room version $invalidNewVersion is not supported. Allowed versions are: ${supportedRoomVersions.joinToString()}. - see A_26202, A_26203"
                        }"""
          }
        }
      }
    }
    context("invite") {
      val invited = UserId(full = "@you:example.com")
      val requestContent = InviteUser.Request(userId = invited, reason = null)
      should("succeed if invitation policy of invited user allows request") {
        withCut {
          coEvery { synapseAdminAPIValidator.validateInvitePermission(any(), any()) } returns
            ValidationSuccess.right()
          val response =
            client.post("/_matrix/client/v3/rooms/123:example.com/invite") {
              bearerAuth("some.token")
              accept(Application.Json)
              contentType(Application.Json)
              setBody(Json.encodeToString(requestContent))
            }

          assertSoftly { response shouldHaveStatus OK }
        }
      }

      should("fail if invitation policy of invited user blocks request") {
        withCut {
          coEvery { synapseAdminAPIValidator.validateInvitePermission(any(), any()) } returns
            InviteBlocked.left()
          val response =
            client.post("/_matrix/client/v3/rooms/123:example.com/invite") {
              bearerAuth("some.token")
              accept(Application.Json)
              contentType(Application.Json)
              setBody(Json.encodeToString(requestContent))
            }

          assertSoftly { response shouldHaveStatus Forbidden }
        }
      }
    }

    context("matrix open endpoints should be authorized") {
      context("getPublicRooms") {
        should("return missing token") {
          withCut {
            val response = client.get("/_matrix/client/v3/publicRooms")

            response.status shouldBe Unauthorized
            response.bodyAsText() shouldEqualJson
              """
                        {
                          "errcode": "M_MISSING_TOKEN",
                          "error": "missing token"
                        }"""
          }
        }

        should("return unknown token") {
          coEvery { matrixTokenAuthMock.invoke(any()) } returns
            AccessTokenAuthenticationFunctionResult(principal = null, cause = null)
          withCut {
            val response =
              client.get("/_matrix/client/v3/publicRooms") { bearerAuth("invalid.token") }

            response.status shouldBe Unauthorized
            response.bodyAsText() shouldEqualJson
              """
                        {
                          "errcode": "M_UNKNOWN_TOKEN",
                          "error": "invalid token"
                        }"""
          }
        }

        should("should return OK with authenticated User") {
          withCut {
            val response = client.get("/_matrix/client/v3/publicRooms") { bearerAuth("some.token") }

            response.status shouldBe OK
          }
        }
      }

      context("GetDirectoryVisibility") {
        should("return missing token") {
          withCut {
            val response = client.get("/_matrix/client/v3/directory/list/room/xxx")

            response.status shouldBe Unauthorized
            response.bodyAsText() shouldEqualJson
              """
                        {
                          "errcode": "M_MISSING_TOKEN",
                          "error": "missing token"
                        }"""
          }
        }

        should("return unknown token") {
          coEvery { matrixTokenAuthMock.invoke(any()) } returns
            AccessTokenAuthenticationFunctionResult(principal = null, cause = null)
          withCut {
            val response =
              client.get("/_matrix/client/v3/directory/list/room/xxx") {
                bearerAuth("invalid.token")
              }

            response.status shouldBe Unauthorized
            response.bodyAsText() shouldEqualJson
              """
                        {
                          "errcode": "M_UNKNOWN_TOKEN",
                          "error": "invalid token"
                        }"""
          }
        }

        should("should return OK with authenticated User") {
          withCut {
            val response =
              client.get("/_matrix/client/v3/directory/list/room/xxxx") { bearerAuth("some.token") }

            response.status shouldBe OK
          }
        }
      }
    }
  })
