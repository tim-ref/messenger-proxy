/*
 * Copyright © 2023 - 2024 akquinet GmbH (https://www.akquinet.de)
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
import de.akquinet.tim.shouldEqualJsonMatrixStandard
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
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
import net.folivo.trixnity.api.server.matrixApiServer
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

                matrixApiServer(Json) {
                    authenticate(BerechtigungsstufeEinsAuthenticationProvider.IDENTIFIER) {
                        with(
                            InboundFederationRoutesImpl(
                                ProxyConfiguration.InboundProxyConfiguration(
                                    enforceDomainList = true,
                                    homeserverUrl = destinationUrl,
                                    synapseHealthEndpoint = "/health",
                                    synapsePort = 8090,
                                    port = 443,
                                    accessTokenToUserIdCacheDuration = 1.hours
                                ),
                                this@testApplication.client,
                                rawDataService,
                                contactManagementService,
                                vzdPublicMock,
                                timAuthorizationCheckConfiguration
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
                            call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            call.respond(eventResponseString)
                        }
                        post("/_matrix/federation/v1/user/keys/claim") {
                            call.request.uri shouldBe "/_matrix/federation/v1/user/keys/claim?test=test"
                            call.receiveText() shouldBe """{"one_time_keys":{}}"""
                            call.request.headers[HttpHeaders.ContentType] shouldBe ContentType.Application.Json.toString()
                            call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            call.respond("""{"one_time_keys":{}}""")
                        }
                        put("/_matrix/federation/v2/invite/{roomId}/{eventId}") {
                            call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            call.respond("""{}""") //horribly complicated event structure omitted for testing
                        }
                    }
                }
                hosts(rawDataServiceUrl) {
                    routing {
                        post(rawDataPath) {
                            Json.decodeFromString<RawDataMetaData>(call.receiveText()).shouldBeTypeOf<RawDataMetaData>()
                            call.request.headers[HttpHeaders.ContentType] shouldBe ContentType.Application.Json.toString()
                        }
                    }
                }
            }
            block()
        }
    }

    context("authorization needed") {
        suspend fun HttpClient.postKeyClaimAuthenticated() =
            post("/_matrix/federation/v1/user/keys/claim?test=test") {
                header(
                    HttpHeaders.Authorization,
                    """X-Matrix origin="fed",destination="otherHost:80",key="ed25519:ABC",sig="signature""""
                )
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"one_time_keys":{}}""")
            }

        suspend fun HttpClient.getEventAuthenticated() =
            get("/_matrix/federation/v1/event/1234") {
                header(
                    HttpHeaders.Authorization,
                    """X-Matrix origin="fed",destination="otherHost:80",key="ed25519:ABC",sig="signature""""
                )
            }

        should("forward federated domain") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    "fed"
                )
                val response = client.postKeyClaimAuthenticated()
                assertSoftly(response) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe """{"one_time_keys":{}}"""
                }
            }
        }

        should("should forward get event with raw data send") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    "fed"
                )
                val response = client.getEventAuthenticated()

                assertSoftly(response) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe eventResponseString
                }
            }
        }

        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-Messenger-Dienst/gemSpec_TI-Messenger-Dienst_V1.1.1/#8.3
        should("deny unfederated domain") {
            withCut {
                val response = client.postKeyClaimAuthenticated()
                response.status shouldBe HttpStatusCode.Forbidden
                response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                    errcode = "M_FORBIDDEN",
                    error = "not part of federation"
                )
            }
        }
    }
    should("ignore unknown url") {
        withCut {
            val response = client.get("/blubs")
            response.status shouldBe HttpStatusCode.NotFound
            response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                errcode = "M_UNRECOGNIZED",
                error = "unsupported (or unknown) endpoint",
            )
        }
    }

    context("federation invite") {
        suspend fun HttpClient.putInvite(inviting: String) =
            put("/_matrix/federation/v2/invite/{roomId}/{eventId}") {
                header(
                    HttpHeaders.Authorization,
                    """X-Matrix origin="fed",destination="otherHost:80",key="ed25519:ABC",sig="signature""""
                )
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    """
                    {
                      "event": {
                        "content": {
                          "membership": "invite"
                        },
                        "origin": "matrix.org",
                        "origin_server_ts": 1234567890,
                        "sender": "$inviting",
                        "state_key": "@joe:elsewhere.com",
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
                federationListCacheMock.domains.value = setOf("fed")
                val response = client.putInvite("@someone:example.org")
                response.status shouldBe HttpStatusCode.Forbidden
                response.bodyAsText() shouldBe """{"errcode":"M_FORBIDDEN","error":"can not invite this user"}"""
            }
        }

        should("succeed for valid contact") {
            withCut {
                federationListCacheMock.domains.value = setOf("fed")
                val response = client.putInvite("4444")
                response.status shouldBe HttpStatusCode.OK
            }
        }

        should("succeed for FHIR entry") {
            withCut {
                federationListCacheMock.domains.value = setOf("fed")
                vzdPublicMock.expectedResult = true
                val response = client.putInvite("@someone:example.org")
                response.status shouldBe HttpStatusCode.OK
            }
        }

        // gemSpec_TI-M_Basis_V1.0.0, AFO_25046: after federation check, send invite to client to check invite permission

        should("succeed if invite permission check on client") {
            val clientTimAuthorizationCheckConfiguration = ProxyConfiguration.TimAuthorizationCheckConfiguration(
                TimAuthorizationCheckConcept.CLIENT,
                InviteRejectionPolicy.ALLOW_ALL
            )
            withCut(clientTimAuthorizationCheckConfiguration) {
                federationListCacheMock.domains.value = setOf("fed")
                val response = client.putInvite("@someone:example.org")
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }
})
