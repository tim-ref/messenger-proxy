/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.federation

import de.akquinet.timref.proxy.ProxyConfiguration
import de.akquinet.timref.proxy.client.InboundClientRoutesImpl
import de.akquinet.timref.proxy.rawdata.RawDataServiceImpl
import de.akquinet.timref.proxy.rawdata.model.RawDataMetaData
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.spyk
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixApiServer
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
        enforceDomainList = false,
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
    lateinit var rawDataService: RawDataServiceImpl

    fun withCut(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            rawDataService = spyk(RawDataServiceImpl(logInfoConfig, client))

            application {
                matrixApiServer(Json) {
                    with(
                        InboundClientRoutesImpl(
                            inboundProxyConfig,
                            logInfoConfig,
                            client,
                            rawDataService
                        )
                    ) {
                        clientServerApiRoutes()
                    }
                }
            }
            externalServices {
                hosts(synapseDestinationUrl) {
                    routing {
                        get("/.well-known/matrix/client") {
                            call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            call.respond("""{"m.homeserver": "homeserver.example.com:1234"}""")
                        }
                        post("/_matrix/client/v3/login") {
                            call.receiveText() shouldBe loginRequestString
                            call.request.headers[HttpHeaders.ContentType] shouldBe ContentType.Application.Json.toString()
                            call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            call.respond(loginResponseString)
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
    context("client routes test") {
        should("get well known") {
            withCut {
                val response = client.get("/.well-known/matrix/client")
                assertSoftly(response) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe """{"m.homeserver": "homeserver.example.com:1234"}"""
                }
            }
        }

        should("should login with raw data send") {
            withCut {
                val response = client.post("/_matrix/client/v3/login") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(loginRequestString)
                }
                assertSoftly(response) {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe loginResponseString
//                    coVerify (exactly = 1) { rawDataService.sendMessageLog(any<RawDataMetaData>()) }
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
    }
})
