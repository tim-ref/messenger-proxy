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
import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.mocks.FederationListCacheMock
import de.akquinet.tim.proxy.rawdata.RawDataServiceImpl
import de.akquinet.tim.proxy.rawdata.model.RawDataMetaData
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
import io.ktor.http.HttpHeaders.Host
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
import io.mockk.spyk
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixApiServer

class OutboundFederationCheckerImplTest : ShouldSpec({
    val externalHost = "external-matrix-server:8090"
    val externalUrl = "https://$externalHost"
    val rawDataServiceUrl = "https://localhost:1234"
    val rawDataPath = "/add-performance-data"
    val federationListCacheMock = FederationListCacheMock()
    val logInfoConfig = ProxyConfiguration.LogInfoConfig(
        "$rawDataServiceUrl$rawDataPath",
        "doctor",
        "2384234234",
        "MP-1",
        "home.de"
    )
    lateinit var rawDataService: RawDataServiceImpl
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

    lateinit var bsEinsService: BerechtigungsstufeEinsService

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
            bsEinsService = BerechtigungsstufeEinsService(federationListCacheMock)

            application {
                install(Authentication) {
                    berechtigungsstufeEinsCheck(checkerService = bsEinsService) {
                        proxyMode = BerechtigungsstufeEinsAuthenticationProvider.ProxyMode.OUTBOUND
                    }
                }
                matrixApiServer(Json) {
                    authenticate(BerechtigungsstufeEinsAuthenticationProvider.IDENTIFIER) {
                        with(
                            OutboundFederationRoutesImpl(
                                this@testApplication.client,
                                rawDataService
                            )
                        ) {
                            serverServerApiRoutes()
                            serverServerRawDataRoutes()
                        }
                    }
                }
            }
            externalServices {
                hosts(externalUrl) {
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
            """X-Matrix origin="thisHost",destination="fed",key="ed25519:ABC",sig="signature""""
        )
    }

    context("authorization needed") {
        suspend fun HttpClient.postKeyClaimAuthenticated() =
            post("/_matrix/federation/v1/user/keys/claim?test=test") {
                header(Host, externalHost)
                matrixAuthorizationHeader()
                contentType(Application.Json)
                setBody("""{"one_time_keys":{}}""")
            }

        suspend fun HttpClient.getEventAuthenticated() =
            get("/_matrix/federation/v1/event/1234") {
                header(Host, externalHost)
                matrixAuthorizationHeader()
            }

        should("should forward get event with raw data send") {
            withCut {
                federationListCacheMock.domains.value += FederationList.FederationDomain(
                    domain = "fed",
                    isInsurance = true,
                    telematikID = "telematik"
                )
                val response = client.getEventAuthenticated()

                assertSoftly(response) {
                    response shouldHaveStatus OK
                    bodyAsText() shouldEqualJson eventResponseString
//                    coVerify (exactly = 1) { rawDataService.sendMessageLog(any<RawDataMetaData>()) }
                }
            }
        }

        should("forward federated domain") {
            withCut {
                federationListCacheMock.domains.value += FederationList.FederationDomain(
                    domain = "fed",
                    isInsurance = true,
                    telematikID = "telematik"
                )
                val response = client.postKeyClaimAuthenticated()

                response shouldHaveStatus OK
                response.bodyAsText() shouldEqualJson """{"one_time_keys":{}}"""
            }
        }
        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-Messenger-Dienst/gemSpec_TI-Messenger-Dienst_V1.1.1/#8.3
        should("deny unfederated domain") {
            withCut {
                federationListCacheMock.domains.value -= FederationList.FederationDomain(
                    domain = "fed",
                    isInsurance = true,
                    telematikID = "telematik"
                )
                val response = client.postKeyClaimAuthenticated()

                response shouldHaveStatus Forbidden
                response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                    errcode = "M_FORBIDDEN",
                    error = "not part of federation"
                )
            }
        }

        /**
         * A_26330
         * Der Matrix Homeserver MUSS ausgehende Requests zum Endpunkt /_matrix/federation/v1/version gemäß
         * [Server-Server API/#request-authentication] authentisieren.
         */
        should("forward requests for server version with authentication") {
            withCut {
                externalServices {
                    hosts(externalUrl) {
                        routing {
                            get("/_matrix/federation/v1/version") {
                                val authorizationHeader = call.request.authorization()
                                val isMatrixAuthHeader = authorizationHeader?.startsWith("X-Matrix ") ?: false
                                if (isMatrixAuthHeader) {
                                    call.respondText("""{"msg":"ok"}""", Application.Json)
                                } else {
                                    call.respond(Unauthorized)
                                }
                            }
                        }
                    }
                }
                federationListCacheMock.domains.value += FederationList.FederationDomain(
                    domain = "fed",
                    isInsurance = true,
                    telematikID = "telematik"
                )

                val response = client.get("/_matrix/federation/v1/version") {
                    header(Host, externalHost)
                    matrixAuthorizationHeader()
                }

                response.bodyAsText() shouldBe """{"msg":"ok"}"""
            }
        }
    }

    should("ignore unknown url") {
        withCut {
            val response = client.get("/blubs") { header(Host, externalHost) }

            response shouldHaveStatus NotFound
            response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                errcode = "M_UNRECOGNIZED",
                error = "unsupported (or unknown) endpoint"
            )
        }
    }
})
