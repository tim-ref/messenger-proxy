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
import de.akquinet.tim.proxy.client.AccessTokenToUserIdAuthenticationFunctionImpl
import de.akquinet.tim.proxy.client.AccessTokenToUserIdImpl
import de.akquinet.tim.proxy.mocks.ContactManagementStub
import de.akquinet.tim.proxy.mocks.FederationListCacheMock
import de.akquinet.tim.proxy.mocks.VZDPublicIDCheckMock
import de.akquinet.tim.proxy.rawdata.RawDataServiceImpl
import de.akquinet.tim.proxy.rawdata.model.RawDataMetaData
import de.akquinet.tim.proxy.util.customMatrixServer
import de.akquinet.tim.shouldEqualJsonMatrixStandard
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.MethodNotAllowed
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
import io.mockk.spyk
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.clientserverapi.server.matrixAccessTokenAuth
import kotlin.time.Duration.Companion.hours

class InboundFederationCheckerImplTest : ShouldSpec({
    val destinationUrl = "https://internal-matrix-server:8090"
    val rawDataServiceUrl = "https://localhost:1234"
    val rawDataPath = "/add-performance-data"
    val federationListCacheMock = FederationListCacheMock()
    val proxyInboundHostPort = 8090
    val hostname = "localhost"
    val matrixHttpPort = 8093
    val inboundHomeserverUrl = "$hostname:$matrixHttpPort"
    val inboundProxyConfig = ProxyConfiguration.InboundProxyConfiguration(
        homeserverUrl = "http://$inboundHomeserverUrl",
        port = proxyInboundHostPort,
        synapseHealthEndpoint = "/health",
        synapsePort = 443,
        enforceDomainList = true,
        accessTokenToUserIdCacheDuration = 1.hours
    )
    val logInfoConfig = ProxyConfiguration.LogInfoConfig(
        "$rawDataServiceUrl$rawDataPath",
        "doctor",
        "2384234234",
        "MP-1",
        "home.de"
    )
    lateinit var rawDataService: RawDataServiceImpl
    val contactManagementService = ContactManagementStub()
    val vzdPublicMock = VZDPublicIDCheckMock()
    val eventResponseString = """{
                                  "origin": "fed",
                                  "origin_server_ts": 1234567890,
                                  "pdus": [
                                    {
                                      "content": {
                                        "see_room_version_spec": "The event format changes depending on the room version."
                                      },
                                      "room_id": "!somewhere:otherHost",
                                      "type": "m.room.minimal_pdu"
                                    }
                                  ]
                                }
                                """
    val defaultTimAuthorizationCheckConfiguration = ProxyConfiguration.TimAuthorizationCheckConfiguration(
        TimAuthorizationCheckConcept.PROXY,
        InviteRejectionPolicy.ALLOW_ALL
    )
    val bsEinsService = BerechtigungsstufeEinsService(federationListCacheMock)

    val publicRooms = """
                    {
                      "chunk": [
                        {
                            "room_id": "publicRoom:myServer.com",
                            "name": "publicRoom",
                            "canonical_alias": "#publicRoom:myServer.com",
                            "num_joined_members": 2,
                            "world_readable": false,
                            "guest_can_join": false,
                            "join_rule": "public",
                            "room_type": "m.space"
                        }
                      ],
                      "total_room_count_estimate": 1
                    }
                """.trimIndent()

    beforeTest {
        federationListCacheMock.domains.value = setOf()
    }

    fun withCut(
        // gemSpec_TI-M_Basis_V1.0.0, AFO_25046: override configuration for test
        timAuthorizationCheckConfiguration: ProxyConfiguration.TimAuthorizationCheckConfiguration = defaultTimAuthorizationCheckConfiguration,
        block: suspend ApplicationTestBuilder.() -> Unit
    ) {
        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }
            rawDataService = spyk(RawDataServiceImpl(logInfoConfig, client))

            application {
                install(Authentication) {
                    berechtigungsstufeEinsCheck(checkerService = bsEinsService) {
                        proxyMode = BerechtigungsstufeEinsAuthenticationProvider.ProxyMode.INBOUND
                    }
                    matrixAccessTokenAuth("matrix-access-token-auth") {
                        authenticationFunction = AccessTokenToUserIdAuthenticationFunctionImpl(
                            AccessTokenToUserIdImpl(inboundProxyConfig, MatrixApiClient())
                        )
                    }
                }

                customMatrixServer(Json) {
                    authenticate(BerechtigungsstufeEinsAuthenticationProvider.IDENTIFIER) {
                        with(
                            InboundFederationRoutesImpl(
                                config = ProxyConfiguration.InboundProxyConfiguration(
                                    enforceDomainList = true,
                                    homeserverUrl = destinationUrl,
                                    synapseHealthEndpoint = "/health",
                                    synapsePort = 8090,
                                    port = 443,
                                    accessTokenToUserIdCacheDuration = 1.hours
                                ),
                                httpClient = this@testApplication.client,
                                rawDataService = rawDataService,
                                contactManagementService = contactManagementService,
                                vzdPublicIDCheck = vzdPublicMock,
                                timAuthorizationCheckConfiguration = timAuthorizationCheckConfiguration,
                                berechtigungsstufeEinsService = bsEinsService,
                            )
                        ) {
                            serverServerApiRoutes()
                            serverServerRawDataRoutes()
                        }
                    }
                }
            }
            externalServices {
                hosts(destinationUrl) {
                    routing {
                        get("/_matrix/federation/v1/event/1234") {
                            call.respondText(eventResponseString, Application.Json)
                        }
                        post("/_matrix/federation/v1/user/keys/claim") {
                            call.request.uri shouldBe "/_matrix/federation/v1/user/keys/claim?test=test"
                            call.receiveText() shouldEqualJson """{"one_time_keys":{}}"""
                            call.request.contentType() shouldBe Application.Json

                            call.respondText("""{"one_time_keys":{}}""", Application.Json)
                        }
                        put("/_matrix/federation/v2/invite/{roomId}/{eventId}") {
                            //horribly complicated event structure omitted for testing
                            call.respondText("{}", Application.Json)
                        }
                        put("/_matrix/federation/v2/send_join/{roomId}/{eventId}") {
                            call.respondText("{}", Application.Json)
                        }
                        get("/_matrix/federation/v1/make_join/{roomId}/{userId}") {
                            call.respondText("{}", Application.Json)
                        }
                        get("/_matrix/client/v3/publicRooms") {
                            call.respondText(
                                contentType = Application.Json,
                                status = OK,
                                text = publicRooms
                            )
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

    fun HttpMessageBuilder.matrixAuthorizationHeader() {
        header(
            Authorization,
            """X-Matrix origin="fed",destination="otherHost:80",key="ed25519:ABC",sig="signature""""
        )
    }

    context("authorization needed") {
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

        should("forward federated domain") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.postKeyClaimAuthenticated()

                assertSoftly(response) {
                    response shouldHaveStatus OK
                    bodyAsText() shouldEqualJson """{"one_time_keys":{}}"""
                }
            }
        }

        should("should forward get event with raw data send") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.getEventAuthenticated()

                assertSoftly(response) {
                    response shouldHaveStatus OK
                    bodyAsText() shouldEqualJson eventResponseString
                }
            }
        }

        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-Messenger-Dienst/gemSpec_TI-Messenger-Dienst_V1.1.1/#8.3
        should("deny unfederated domain") {
            withCut {
                val response = client.postKeyClaimAuthenticated()

                response shouldHaveStatus Forbidden
                response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                    errcode = "M_FORBIDDEN",
                    error = "not part of federation"
                )
            }
        }

        /**
         * A_26331
         * Der TI-M Fachdienst MUSS nicht authentisierte Requests zum Endpunkt /_matrix/federation/v1/version mit
         * einer HTTP 401 Response ablehnen.
         */
        should("reject unauthenticated request for server version") {
            withCut {
                val response = client.get("/_matrix/federation/v1/version")

                response shouldHaveStatus Unauthorized
            }
        }

        should("forward authenticated request for server version") {
            withCut {
                externalServices {
                    hosts(destinationUrl) {
                        routing {
                            get("/_matrix/federation/v1/version") {
                                call.respondText("""{ "id": "success" }""", Application.Json)
                            }
                        }
                    }
                }
                federationListCacheMock.domains.value += insuranceDomainFed()

                val response = client.get("/_matrix/federation/v1/version") {
                    matrixAuthorizationHeader()
                }

                response.bodyAsText() shouldEqualJson """{ "id": "success" }"""
            }
        }
    }

    should("ignore unknown url") {
        withCut {
            val response = client.get("/blubs")

            response shouldHaveStatus NotFound
            response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                errcode = "M_NOT_FOUND",
                error = "unsupported (or unknown) endpoint",
            )
        }
    }

    /*
     * Similarly, a 405 M_UNRECOGNIZED error is used to denote an unsupported
     * method to a known endpoint.
     *
     * See https://spec.matrix.org/v1.11/server-server-api/#unsupported-endpoints
     */
    context("respond with M_UNRECOGNIZED for unsupported method of known endpoint") {
        should("POST") {
            withCut {
                val response = client.post("/.well-known/matrix/server")

                assertSoftly {
                    response shouldHaveStatus MethodNotAllowed
                    response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                        errcode = "M_UNRECOGNIZED",
                        error = "This endpoint is implemented, but the method is not supported.",
                    )
                }
            }
        }

        should("PUT") {
            withCut {
                val response = client.put("/.well-known/matrix/server")

                assertSoftly {
                    response shouldHaveStatus MethodNotAllowed
                    response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                        errcode = "M_UNRECOGNIZED",
                        error = "This endpoint is implemented, but the method is not supported.",
                    )
                }
            }
        }

        should("DELETE") {
            withCut {
                val response = client.delete("/.well-known/matrix/server")

                assertSoftly {
                    response shouldHaveStatus MethodNotAllowed
                    response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                        errcode = "M_UNRECOGNIZED",
                        error = "This endpoint is implemented, but the method is not supported.",
                    )
                }
            }
        }

        should("PATCH") {
            withCut {
                val response = client.patch("/.well-known/matrix/server")

                assertSoftly {
                    response shouldHaveStatus MethodNotAllowed
                    response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                        errcode = "M_UNRECOGNIZED",
                        error = "This endpoint is implemented, but the method is not supported.",
                    )
                }
            }
        }
    }

    context("federation invite") {
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

        // gemSpec_TI-Messenger-Dienst_V1.1.1, 3.5.2.2+: after federation check, check invite permission on proxy

        should("fail if neither contact nor FHIR entry if check on proxy") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.putInvite(sender = "@someone:fed", invited = "@joe:fed")

                response shouldHaveStatus Forbidden
                response.bodyAsText() shouldEqualJson """{
                        "errcode":"M_FORBIDDEN",
                        "error":"can not invite this user"
                    }"""
            }
        }

        should("succeed for valid contact") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.putInvite(sender = "@4444:fed", invited = "@joe:fed")

                response shouldHaveStatus OK
            }
        }

        should("succeed for FHIR entry") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                vzdPublicMock.expectedResult = true
                val response = client.putInvite(sender = "@someone:fed", invited = "@joe:fed")

                response shouldHaveStatus OK
            }
        }

        // gemSpec_TI-M_Basis_V1.0.0, AFO_25046: after federation check, send invite to client to check invite permission

        should("succeed if invite permission check on client") {
            val clientTimAuthorizationCheckConfiguration = ProxyConfiguration.TimAuthorizationCheckConfiguration(
                TimAuthorizationCheckConcept.CLIENT,
                InviteRejectionPolicy.ALLOW_ALL
            )
            withCut(clientTimAuthorizationCheckConfiguration) {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.putInvite(sender = "@someone:fed", invited = "@joe:fed")

                response shouldHaveStatus OK
            }
        }

        // AF_10064-02 - Föderationszugehörigkeit eines Messenger-Service prüfen
        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#AF_10064-02
        // A_25534 - Fehlschlag Föderationsprüfung
        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#A_25534
        should("reject invites to invitees outside of federation") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.putInvite(sender = "@someone:fed", invited = "@invitee:invitee.org")

                response shouldHaveStatus Forbidden
                response.bodyAsText() shouldEqualJson """{
                        "errcode": "M_FORBIDDEN" ,
                        "error": "invitee.org kann nicht in der Föderation gefunden werden"
                    }"""
            }
        }

        // AF_10064-02 - Föderationszugehörigkeit eines Messenger-Service prüfen
        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#AF_10064-02
        // A_25534 - Fehlschlag Föderationsprüfung
        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#A_25534
        should("reject invites from senders outside of federation") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.putInvite(sender = "@someone:sender.org", invited = "@invitee:fed")

                response shouldHaveStatus Forbidden
                response.bodyAsText() shouldEqualJson """{
                        "errcode": "M_FORBIDDEN" ,
                        "error": "sender.org kann nicht in der Föderation gefunden werden"
                    }"""
            }
        }
    }

    context("join public rooms") {

        suspend fun HttpClient.sendJoin(roomAlias: String) =
            put("/_matrix/federation/v2/send_join/$roomAlias:myServer.com/1234") {
                header(
                    Authorization,
                    """X-Matrix origin="fed",destination="myServer:80",key="ed25519:ABC",sig="signature""""
                )
                contentType(Application.Json)
                setBody(
                    """{
                              "content": {
                                "membership": "join"
                              },
                              "origin": "matrix.org",
                              "origin_server_ts": 1234567890,
                              "sender": "@someone:example.org",
                              "state_key": "@someone:example.org",
                              "type": "m.room.member"
                            }""".trimIndent()
                )
            }

        suspend fun HttpClient.makeJoin() =
            put("/_matrix/federation/v2/send_join/publicRoom:myServer.com/1234") {
                header(
                    Authorization,
                    """X-Matrix origin="fed",destination="myServer:80",key="ed25519:ABC",sig="signature""""
                )
            }

        should("fail, if user from other homeserver trys to join public room via send_join") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.sendJoin("publicRoom")
                response.status shouldBe Forbidden
                response.bodyAsText() shouldBe """{"errcode":"M_FORBIDDEN","error":"Cannot join public rooms owned by other home servers"}"""
            }
        }

        should("succeed if user from other homeserver trys to join a private room via invite") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.sendJoin("privateRoom")
                response.status shouldBe OK
            }
        }

        should("fail, if user from other homeserver trys to join public room via make_join") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.makeJoin()
                response.status shouldBe Forbidden
                response.bodyAsText() shouldBe """{"errcode":"M_FORBIDDEN","error":"Cannot join public rooms owned by other home servers"}"""
            }
        }
    }
})

private fun insuranceDomainFed() = FederationList.FederationDomain(
    domain = "fed",
    isInsurance = true,
    telematikID = "telematik"
)
