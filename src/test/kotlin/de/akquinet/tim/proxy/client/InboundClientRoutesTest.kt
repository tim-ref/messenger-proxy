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

package de.akquinet.tim.proxy.client

import de.akquinet.tim.proxy.InviteRejectionPolicy
import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.TimAuthorizationCheckConcept
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.federation.FederationList
import de.akquinet.tim.proxy.mocks.FederationListCacheMock
import de.akquinet.tim.proxy.rawdata.RawDataServiceImpl
import de.akquinet.tim.proxy.util.customMatrixServer
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import net.folivo.trixnity.clientserverapi.server.AccessTokenAuthenticationFunctionResult
import net.folivo.trixnity.core.model.UserId
import kotlin.time.Duration.Companion.hours

class InboundClientRoutesTest : ShouldSpec({

    fun Application.testModule(client: HttpClient) {
        val synapseDestinationUrl = "https://internal-matrix-server:8090"
        val rawDataServiceUrl = "https://localhost:1234"
        val rawDataPath = "/add-performance-data"
        val inboundProxyConfig = ProxyConfiguration.InboundProxyConfiguration(
            enforceDomainList = true,
            homeserverUrl = synapseDestinationUrl,
            synapseHealthEndpoint = "/health",
            synapsePort = 443,
            port = 8090,
            accessTokenToUserIdCacheDuration = 1.hours
        )
        val logInfoConfig = ProxyConfiguration.LogInfoConfig(
            url = "$rawDataServiceUrl$rawDataPath",
            professionId = "doctor",
            telematikId = "2384234234",
            instanceId = "MP-1",
            homeFQDN = "home.de"
        )
        val timAuthorizationCheckConfiguration = ProxyConfiguration.TimAuthorizationCheckConfiguration(
            concept = TimAuthorizationCheckConcept.CLIENT,
            inviteRejectionPolicy = InviteRejectionPolicy.ALLOW_ALL
        )

        val matrixTokenAuthMock: AccessTokenToUserIdAuthenticationFunction = mockk()
        coEvery { matrixTokenAuthMock.invoke(any()) } returns AccessTokenAuthenticationFunctionResult(
            principal = UserIdPrincipal(
                UserId(full = "@me:example.com")
            ),
            cause = null
        )

        val rawDataService = RawDataServiceImpl(logInfoConfig, client)

        val flMock = FederationListCacheMock().apply {
            domains.value = setOf(
                FederationList.FederationDomain(
                    domain = "federated.org",
                    isInsurance = true,
                    telematikID = "telematik"
                )
            )
        }
        val bsEinsService = BerechtigungsstufeEinsService(flMock)

        val regServiceConfig = ProxyConfiguration.RegistrationServiceConfiguration(
            baseUrl = "https://reg-service",
            servicePort = "8080",
            healthPort = "8081",
            federationListEndpoint = "/backend/federation",
            invitePermissionCheckEndpoint = "/backend/vzd/invite",
            readinessEndpoint = "/actuator/health/readiness",
            wellKnownSupportEndpoint = "/backend/well-known-support"
        )

        val inboundClientRoutes = InboundClientRoutesImpl(
            config = inboundProxyConfig,
            logConfiguration = logInfoConfig,
            timAuthorizationCheckConfiguration = timAuthorizationCheckConfiguration,
            httpClient = client,
            rawDataService = rawDataService,
            berechtigungsstufeEinsService = bsEinsService,
            regServiceConfig = regServiceConfig
        )

        customMatrixServer(Json) {
            route("") {
                inboundClientRoutes.apply { clientServerApiRoutes() }
                inboundClientRoutes.apply { openClientServerApiRoutes() }
            }
        }
    }

    context("media routes") {

        // A_26328 - Prüfung eingehender Medienanfragen
        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#A_26328
        // A_25534 - Fehlschlag Föderationsprüfung
        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#A_25534
        should("reject requests for media on unfederated servers") {
            testApplication {
                val client = createClient { install(ContentNegotiation) { json() } }
                application { testModule(client) }

                val response = client.get("/_matrix/media/v3/download/unfederatedServer/1")

                response shouldHaveStatus Forbidden
                response.bodyAsText() shouldEqualJson """
                    { 
                      "errcode": "M_FORBIDDEN",  
                      "error": "unfederatedServer kann nicht in der Föderation gefunden werden"  
                    }
                """
            }
        }

        should("relay requests for media on federated servers") {
            testApplication {
                val client = createClient { install(ContentNegotiation) { json() } }
                application { testModule(client) }
                externalServices {
                    hosts("https://internal-matrix-server:8090") {
                        routing {
                            get("/_matrix/media/v3/download/federated.org/1") {
                                call.respond(OK, "blimey")
                            }
                        }
                    }
                }

                val response = client.get("/_matrix/media/v3/download/federated.org/1")

                response shouldHaveStatus OK
                response.bodyAsText() shouldBe "blimey"
            }
        }

    }

    // A_26265 - TI-M FD Org-Admin Support
    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#A_26265
    context("support routes") {

        should("forward support request to registration service") {
            testApplication {
                val client = createClient { install(ContentNegotiation) { json() } }
                application { testModule(client) }
                externalServices {
                    hosts("https://reg-service:8080") {
                        routing {
                            get("/backend/well-known-support/home") {
                                call.respond(OK, """{"support_page":"https://support.example.com"}""")
                            }
                        }
                    }
                }

                val response = client.get("/.well-known/matrix/support")

                response shouldHaveStatus OK
                response.bodyAsText() shouldContain """{"support_page":"https://support.example.com"}"""
            }
        }

        should("respond with default if no data for the server is available") {
            testApplication {
                val client = createClient { install(ContentNegotiation) { json() } }
                application { testModule(client) }
                externalServices {
                    hosts("https://reg-service:8080") {
                        routing {
                            get("/backend/well-known-support/home") {
                                call.respond(NotFound)
                            }
                        }
                    }
                }

                val response = client.get("/.well-known/matrix/support")

                response shouldHaveStatus OK
                response.bodyAsText() shouldContain """{
  "contacts": [
    {
      "email_address": "Referenzimplementierung",
      "matrix_id": "Referenzimplementierung",
      "role": "Referenzimplementierung"
    }
  ],
  "support_page": "Referenzimplementierung"
}"""
            }
        }
    }

})
