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

import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.client.AccessTokenToUserIdAuthenticationFunctionImpl
import de.akquinet.tim.proxy.client.AccessTokenToUserIdImpl
import de.akquinet.tim.proxy.federation.MatrixFederationCheckAuth.Mode.INBOUND
import de.akquinet.tim.proxy.mocks.ContactManagementStub
import de.akquinet.tim.proxy.mocks.FederationListCacheMock
import de.akquinet.tim.proxy.mocks.VZDPublicIDCheckMock
import de.akquinet.tim.proxy.rawdata.RawDataServiceImpl
import de.akquinet.tim.proxy.rawdata.model.RawDataMetaData
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

    beforeTest {
        federationListCacheMock.domains.value = setOf()
    }

    fun withCut(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }
            rawDataService = spyk(RawDataServiceImpl(logInfoConfig, client))

            application {
                install(Authentication) {
                    matrixFederationCheckAuth("federation-check") {
                        federationAllowed = federationListCacheMock.domains
                        mode = INBOUND
                    }
                    matrixAccessTokenAuth("matrix-access-token-auth") {
                        authenticationFunction = AccessTokenToUserIdAuthenticationFunctionImpl(
                            AccessTokenToUserIdImpl(inboundProxyConfig, MatrixApiClient())
                        )
                    }
                }

                matrixApiServer(Json) {
                    authenticate("federation-check") {
                        with(
                            InboundFederationRoutesImpl(
                                ProxyConfiguration.InboundProxyConfiguration(
                                    enforceDomainList = false,
                                    homeserverUrl = destinationUrl,
                                    synapseHealthEndpoint = "/health",
                                    synapsePort = 8090,
                                    port = 443,
                                    accessTokenToUserIdCacheDuration = 1.hours
                                ),
                                this@testApplication.client,
                                rawDataService,
                                contactManagementService,
                                vzdPublicMock
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

        should("deny unfederated domain") {
            withCut {
                val response = client.postKeyClaimAuthenticated()
                response.status shouldBe HttpStatusCode.Unauthorized
                response.bodyAsText() shouldBe """{"errcode":"M_UNAUTHORIZED","error":"not part of federation"}"""
            }
        }
    }
    should("ignore unknown url") {
        withCut {
            val response = client.get("/blubs")
            response.status shouldBe HttpStatusCode.NotFound
            response.bodyAsText() shouldBe """{"errcode":"M_UNRECOGNIZED","error":"unsupported (or unknown) endpoint"}"""
        }
    }
})
