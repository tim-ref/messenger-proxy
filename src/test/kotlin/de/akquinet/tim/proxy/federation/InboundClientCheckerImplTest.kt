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
package de.akquinet.tim.proxy.federation

import de.akquinet.tim.ErrorResponse
import de.akquinet.tim.proxy.InviteRejectionPolicy
import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.TimAuthorizationCheckConcept
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.client.AccessTokenToUserIdAuthenticationFunction
import de.akquinet.tim.proxy.client.InboundClientRoutesImpl
import de.akquinet.tim.proxy.client.UserIdPrincipal
import de.akquinet.tim.proxy.mocks.FederationListCacheMock
import de.akquinet.tim.proxy.rawdata.RawDataServiceImpl
import de.akquinet.tim.proxy.rawdata.model.RawDataMetaData
import de.akquinet.tim.proxy.validation.SendMessageValidationService
import de.akquinet.tim.shouldEqualJsonMatrixStandard
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.clientserverapi.model.rooms.DirectoryVisibility
import net.folivo.trixnity.clientserverapi.model.rooms.UpgradeRoom
import net.folivo.trixnity.clientserverapi.server.AccessTokenAuthenticationFunctionResult
import net.folivo.trixnity.clientserverapi.server.matrixAccessTokenAuth
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import kotlin.time.Duration.Companion.hours

class InboundClientCheckerImplTest : ShouldSpec({
    val synapseDestinationUrl = "https://internal-matrix-server:8090"
    val rawDataServiceUrl = "https://localhost:1234"
    val rawDataPath = "/add-performance-data"
    val loginRequestString = """ {
                              "identifier": {
                                "type": "m.id.user",
                                "user": "cheeky_monkey"
                              },
                              "initial_device_display_name": "Jungle Phone",
                              "password": "ilovebananas",
                              "type": "m.login.password"
                            }
                            """
    val loginResponseString = """{
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
    val inboundProxyConfig = ProxyConfiguration.InboundProxyConfiguration(
        enforceDomainList = true,
        homeserverUrl = synapseDestinationUrl,
        synapseHealthEndpoint = "/health",
        synapsePort = 443,
        port = 8090,
        accessTokenToUserIdCacheDuration = 1.hours
    )
    val logInfoConfig = ProxyConfiguration.LogInfoConfig(
        "$rawDataServiceUrl$rawDataPath",
        "doctor",
        "2384234234",
        "MP-1",
        "home.de"
    )
    val timAuthorizationCheckConfiguration = ProxyConfiguration.TimAuthorizationCheckConfiguration(
        concept = TimAuthorizationCheckConcept.CLIENT,
        inviteRejectionPolicy = InviteRejectionPolicy.ALLOW_ALL
    )
    lateinit var rawDataService: RawDataServiceImpl
    lateinit var bsEinsService: BerechtigungsstufeEinsService

    val matrixTokenAuthMock: AccessTokenToUserIdAuthenticationFunction = mockk { }
    val supportedRoomVersions = setOf("9", "10")

    beforeTest {
        coEvery { matrixTokenAuthMock.invoke(any()) } returns AccessTokenAuthenticationFunctionResult(
            principal = UserIdPrincipal(
                UserId(full = "@me:example.com")
            ),
            cause = null
        )
    }

    fun withCut(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            rawDataService = spyk(RawDataServiceImpl(logInfoConfig, client))

            val flMock = FederationListCacheMock()
            flMock.domains.value = setOf(
                FederationList.FederationDomain(
                    domain = "example.com",
                    isInsurance = true,
                    telematikID = "telematik"
                )
            )
            bsEinsService = BerechtigungsstufeEinsService(flMock)

            val inboundClientRoutes = InboundClientRoutesImpl(
                config = inboundProxyConfig,
                logConfiguration = logInfoConfig,
                timAuthorizationCheckConfiguration = timAuthorizationCheckConfiguration,
                httpClient = client,
                rawDataService = rawDataService,
                berechtigungsstufeEinsService = bsEinsService,
                regServiceConfig = ProxyConfiguration.RegistrationServiceConfiguration(
                    baseUrl = "https://reg-service",
                    servicePort = "8080",
                    healthPort = "8081",
                    federationListEndpoint = "/backend/federation",
                    invitePermissionCheckEndpoint = "/backend/vzd/invite",
                    readinessEndpoint = "/actuator/health/readiness",
                    wellKnownSupportEndpoint = "/backend/well-known-support"
                ),
                sendMessageValidationService = SendMessageValidationService(),
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
                        get("/.well-known/matrix/client") {
                            call.respondText("""{"m.homeserver": "homeserver.example.com:1234"}""", Application.Json)
                        }
                        post("/_matrix/client/v3/login") {
                            call.receiveText() shouldBe loginRequestString
                            call.request.contentType() shouldBe Application.Json

                            call.respondText(loginResponseString, Application.Json)
                        }
                        post("/_matrix/client/v3/createRoom") {
                            val response = CreateRoom.Response(roomId = RoomId("123:example.com"))
                            call.respondText(Json.encodeToString(response), Application.Json)
                        }
                        post("/_matrix/client/v3/rooms/123:example.com/invite") {
                            call.respondText("{}", Application.Json)
                        }
                        post("/_matrix/client/v3/rooms/123:example.com/upgrade") {
                            call.respondText("{}", Application.Json)
                        }
                        get("/_matrix/client/v3/publicRooms") {
                            call.respondText("{}", Application.Json)
                        }
                        get("/_matrix/client/v3/directory/list/room/{roomId}") {
                            call.respondText("{}", Application.Json)
                        }
                    }
                }
                hosts(rawDataServiceUrl) {
                    routing {
                        post(rawDataPath) {
                            Json.decodeFromString<RawDataMetaData>(call.receiveText()).shouldBeTypeOf<RawDataMetaData>()
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
                    response.bodyAsText() shouldEqualJson """{"m.homeserver": "homeserver.example.com:1234"}"""
                }
            }
        }

        should("should login with raw data send") {
            withCut {
                val response = client.post("/_matrix/client/v3/login") {
                    contentType(Application.Json)
                    setBody(loginRequestString)
                }

                assertSoftly {
                    response shouldHaveStatus OK
                    response.bodyAsText() shouldEqualJson loginResponseString
//                    coVerify (exactly = 1) { rawDataService.sendMessageLog(any<RawDataMetaData>()) }
                }
            }
        }

        should("ignore unknown url") {
            withCut {
                val response = client.get("/blubs")
                response shouldHaveStatus NotFound
                response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                    errcode = "M_UNRECOGNIZED",
                    error = "unsupported (or unknown) endpoint"
                )
            }
        }
    }

    context("client routes with berechtigungsstufe 1 tests: CreateRoom") {
        val inviter = UserId(full = "@me:example.com")
        val invited = UserId(full = "@you:example.com")
        val createRoomRequest = CreateRoom.Request(
            visibility = DirectoryVisibility.PRIVATE,
            creationContent = CreateEventContent(creator = inviter),
            roomVersion = null,
            initialState = null,
            invite = setOf(invited),
            inviteThirdPid = null,
            roomAliasLocalPart = null,
            name = "my room",
            topic = null,
            isDirect = null,
            powerLevelContentOverride = null,
            preset = null
        )

        should("post create room should succeed") {
            withCut {
                val response = client.post("/_matrix/client/v3/createRoom") {
                    bearerAuth("some.token")
                    accept(Application.Json)
                    contentType(Application.Json)
                    setBody(Json.encodeToString(createRoomRequest))
                }

                assertSoftly {
                    response shouldHaveStatus OK
                    response.bodyAsText() shouldEqualJson """{"room_id":"123:example.com"}"""
                }
            }
        }

        should("post create room should fail without token") {
            withCut {
                val response = client.post("/_matrix/client/v3/createRoom") {
                    setBody(Json.encodeToString(createRoomRequest))
                }

                response shouldHaveStatus Unauthorized
            }
        }
        should("post create room should fail with invalid token") {
            coEvery { matrixTokenAuthMock.invoke(any()) } returns AccessTokenAuthenticationFunctionResult(
                principal = UserIdPrincipal(
                    UserId(full = "@me:unfederated.com")
                ),
                cause = null
            )

            withCut {
                val response = client.post("/_matrix/client/v3/createRoom") {
                    bearerAuth("some.token")
                    setBody(Json.encodeToString(createRoomRequest))
                }

                response shouldHaveStatus Forbidden
            }
        }
    }

    context("room version validation tests") {
        val inviter = UserId(full = "@me:example.com")
        val invited = UserId(full = "@you:example.com")
        val createRoomRequest = CreateRoom.Request(
            visibility = DirectoryVisibility.PRIVATE,
            creationContent = CreateEventContent(creator = inviter),
            roomVersion = null,
            initialState = null,
            invite = setOf(invited),
            inviteThirdPid = null,
            roomAliasLocalPart = null,
            name = "my room",
            topic = null,
            isDirect = null,
            powerLevelContentOverride = null,
            preset = null
        )

        val upgradeRoomRequest = UpgradeRoom.Request(
            newVersion = ""
        )

        should("not create room with invalid room version type") {
            withCut {
                val request = createRoomRequest.copy(
                    roomVersion = "to_be_replaced"
                )

                val body = Json.encodeToString(request).replace("\"to_be_replaced\"", "123")

                val response = client.post("/_matrix/client/v3/createRoom") {
                    bearerAuth("some.token")
                    accept(Application.Json)
                    contentType(Application.Json)
                    setBody(body)
                }

                assertSoftly {
                    response shouldHaveStatus BadRequest
                    response.bodyAsText() shouldContain "muss ein String sein"
                }
            }
        }

        supportedRoomVersions.forEach { roomVersion ->
            should("post create room should succeed in case of $roomVersion") {
                val validCreateRoomRequest = createRoomRequest.copy(roomVersion = roomVersion)
                withCut {
                    val response = client.post("/_matrix/client/v3/createRoom") {
                        bearerAuth("some.token")
                        accept(Application.Json)
                        contentType(Application.Json)
                        setBody(Json.encodeToString(validCreateRoomRequest))
                    }

                    assertSoftly {
                        response shouldHaveStatus OK
                        response.bodyAsText() shouldEqualJson """{"room_id":"123:example.com"}"""
                    }
                }
            }
        }

        should("post create room should not succeed in case of invalid room version") {
            val invalidRoomRequest = createRoomRequest.copy(
                roomVersion = "11"
            )
            withCut {
                val response = client.post("/_matrix/client/v3/createRoom") {
                    bearerAuth("some.token")
                    accept(Application.Json)
                    contentType(Application.Json)
                    setBody(Json.encodeToString(invalidRoomRequest))
                }

                assertSoftly {
                    response shouldHaveStatus BadRequest
                    response.bodyAsText() shouldEqualJson """{
                        "errcode":"M_UNSUPPORTED_ROOM_VERSION",
                        "error":"Ungültige Raumversion: ${invalidRoomRequest.roomVersion} ist keine gültige Raumversion. Es werden nur die Versionen ${supportedRoomVersions.joinToString()} unterstützt."
                        }"""
                }
            }
        }

        supportedRoomVersions.forEach { roomVersion ->
            should("post upgrade room should succeed $roomVersion") {
                val validUpgradeRoomRequest = upgradeRoomRequest.copy(newVersion = roomVersion)
                withCut {
                    val response = client.post("/_matrix/client/v3/rooms/123:example.com/upgrade") {
                        bearerAuth("some.token")
                        accept(Application.Json)
                        contentType(Application.Json)
                        setBody(Json.encodeToString(validUpgradeRoomRequest))
                    }

                    response shouldHaveStatus OK
                }
            }
        }

        should("post upgrade room should not succeed in case of invalid room version") {
            val invalidUpgradeRoomRequest = upgradeRoomRequest.copy(
                newVersion = "11"
            )
            withCut {
                val response = client.post("/_matrix/client/v3/rooms/123:example.com/upgrade") {
                    bearerAuth("some.token")
                    accept(Application.Json)
                    contentType(Application.Json)
                    setBody(Json.encodeToString(invalidUpgradeRoomRequest))
                }

                assertSoftly {
                    response shouldHaveStatus BadRequest
                    response.bodyAsText() shouldEqualJson """{
                        "errcode":"M_UNSUPPORTED_ROOM_VERSION",
                        "error":"Ungültige Raumversion: ${invalidUpgradeRoomRequest.newVersion} ist keine gültige Raumversion. Es werden nur die Versionen ${supportedRoomVersions.joinToString()} unterstützt."
                        }"""
                }
            }
        }


    }

    context("matrix open endpoints should be authorized") {
        context("getPublicRooms") {
            should("return missing token") {
                withCut {
                    val response = client.get("/_matrix/client/v3/publicRooms")

                    response.status shouldBe Unauthorized
                    response.bodyAsText() shouldEqualJson """
                        {
                          "errcode": "M_MISSING_TOKEN",
                          "error": "missing token"
                        }"""
                }
            }

            should("return unknown token") {
                coEvery { matrixTokenAuthMock.invoke(any()) } returns AccessTokenAuthenticationFunctionResult(
                    principal = null,
                    cause = null
                )
                withCut {
                    val response = client.get("/_matrix/client/v3/publicRooms") {
                        bearerAuth("invalid.token")
                    }

                    response.status shouldBe Unauthorized
                    response.bodyAsText() shouldEqualJson """
                        {
                          "errcode": "M_UNKNOWN_TOKEN",
                          "error": "invalid token"
                        }"""
                }
            }

            should("should return OK with authenticated User") {
                withCut {
                    val response = client.get("/_matrix/client/v3/publicRooms") {
                        bearerAuth("some.token")
                    }

                    response.status shouldBe OK
                }
            }
        }

        context("GetDirectoryVisibility") {
            should("return missing token") {
                withCut {
                    val response = client.get("/_matrix/client/v3/directory/list/room/xxx")

                    response.status shouldBe Unauthorized
                    response.bodyAsText() shouldEqualJson """
                        {
                          "errcode": "M_MISSING_TOKEN",
                          "error": "missing token"
                        }"""
                }
            }

            should("return unknown token") {
                coEvery { matrixTokenAuthMock.invoke(any()) } returns AccessTokenAuthenticationFunctionResult(
                    principal = null,
                    cause = null
                )
                withCut {
                    val response = client.get("/_matrix/client/v3/directory/list/room/xxx") {
                        bearerAuth("invalid.token")
                    }

                    response.status shouldBe Unauthorized
                    response.bodyAsText() shouldEqualJson """
                        {
                          "errcode": "M_UNKNOWN_TOKEN",
                          "error": "invalid token"
                        }"""
                }
            }

            should("should return OK with authenticated User") {
                withCut {
                    val response = client.get("/_matrix/client/v3/directory/list/room/xxxx") {
                        bearerAuth("some.token")
                    }

                    response.status shouldBe OK
                }
            }
        }
    }

})
