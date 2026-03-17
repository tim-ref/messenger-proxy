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
package de.akquinet.tim.proxy.client

import arrow.core.left
import arrow.core.right
import de.akquinet.tim.proxy.InviteRejectionPolicy
import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.TimAuthorizationCheckConcept
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.enforcer.RequestPolicyEnforcer
import de.akquinet.tim.proxy.federation.FederationList
import de.akquinet.tim.proxy.mocks.FederationListCacheMock
import de.akquinet.tim.proxy.outcomes.ReferencedEventTooOld
import de.akquinet.tim.proxy.outcomes.ValidationSuccess
import de.akquinet.tim.proxy.rawdata.RawDataServiceImpl
import de.akquinet.tim.proxy.rawdata.model.RawDataMetaData
import de.akquinet.tim.proxy.util.customMatrixServer
import de.akquinet.tim.proxy.validation.RequestContentValidator
import de.akquinet.tim.proxy.validation.SynapseAdminAPIValidator
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMessageBuilder
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.json.Json
import net.folivo.trixnity.clientserverapi.server.AccessTokenAuthenticationFunctionResult
import net.folivo.trixnity.core.model.UserId

class InboundClientRoutesTest :
  ShouldSpec({
    val rawDataServiceUrl = "https://localhost:1234"
    val rawDataPath = "/add-performance-data"
    val synapseAdminAPIValidator = mockk<SynapseAdminAPIValidator>()

    fun Application.testModule(client: HttpClient) {
      val synapseDestinationUrl = "https://internal-matrix-server:8090"
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
          url = "$rawDataServiceUrl$rawDataPath",
          professionId = "doctor",
          telematikId = "2384234234",
          instanceId = "MP-1",
          homeFQDN = "home.de",
        )
      val timAuthorizationCheckConfiguration =
        ProxyConfiguration.TimAuthorizationCheckConfiguration(
          concept = TimAuthorizationCheckConcept.CLIENT,
          inviteRejectionPolicy = InviteRejectionPolicy.ALLOW_ALL,
        )

      val matrixTokenAuthMock: AccessTokenToUserIdAuthenticationFunction = mockk()
      coEvery { matrixTokenAuthMock.invoke(any()) } returns
        AccessTokenAuthenticationFunctionResult(
          principal = UserIdPrincipal(UserId(full = "@me:example.com")),
          cause = null,
        )

      val rawDataService = RawDataServiceImpl(logInfoConfig, client)

      val flMock =
        FederationListCacheMock().apply {
          domains.value =
            setOf(
              FederationList.FederationDomain(
                domain = "federated.org",
                isInsurance = true,
                telematikID = "telematik",
              )
            )
        }
      val bsEinsService = BerechtigungsstufeEinsService(flMock)

      val regServiceConfig =
        ProxyConfiguration.RegistrationServiceConfiguration(
          baseUrl = "https://reg-service",
          servicePort = "8080",
          healthPort = "8081",
          federationListEndpoint = "/backend/federation",
          readinessEndpoint = "/actuator/health/readiness",
          wellKnownSupportEndpoint = "/backend/well-known-support",
        )

      val inboundClientRoutes =
        InboundClientRoutesImpl(
          config = inboundProxyConfig,
          logConfiguration = logInfoConfig,
          timAuthorizationCheckConfiguration = timAuthorizationCheckConfiguration,
          httpClient = client,
          rawDataService = rawDataService,
          berechtigungsstufeEinsService = bsEinsService,
          regServiceConfig = regServiceConfig,
          requestContentValidator = RequestContentValidator(),
          synapseAdminAPIValidator = synapseAdminAPIValidator,
          requestPolicyEnforcer = RequestPolicyEnforcer(),
        )

      customMatrixServer(Json) {
        route("") {
          inboundClientRoutes.apply { clientServerApiRoutes() }
          inboundClientRoutes.apply { openClientServerApiRoutes() }
        }
      }
    }

    fun ApplicationTestBuilder.homeserverWithRouting(configuration: Routing.() -> Unit) {
      externalServices {
        hosts("https://internal-matrix-server:8090") {
          install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
          routing { configuration() }
        }
        hosts(rawDataServiceUrl) {
          routing {
            post(rawDataPath) {
              Json.decodeFromString<RawDataMetaData>(call.receiveText())
                .shouldBeTypeOf<RawDataMetaData>()
              call.request.contentType() shouldBe ContentType.Application.Json
            }
          }
        }
      }
    }

    fun HttpMessageBuilder.matrixAuthorizationHeader() {
      header(
        Authorization,
        """X-Matrix origin="fed",destination="otherHost:80",key="ed25519:ABC",sig="signature"""",
      )
    }

    suspend fun HttpClient.putRedact() =
      put("/_matrix/client/v3/rooms/{roomId}/redact/{eventId}/{txnId}") {
        matrixAuthorizationHeader()
        contentType(ContentType.Application.Json)
        setBody(
          """
          {
            "content": {
              "reason": "Spamming",
              "redacts": "${'$'}fukweghifu23:localhost"
            },
            "event_id": "$143273582443PhrSn:example.org",
            "origin_server_ts": 1432735824653,
            "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
            "sender": "@example:example.org",
            "type": "m.room.redaction",
            "unsigned": {
              "age": 1234,
              "membership": "join"
            }
          }
          """
            .trimIndent()
        )
      }

    context("custom") {
      context("event redaction") {
        should("forbid redaction for events older than 24 hours") {
          testApplication {
            val client = createClient { install(ContentNegotiation) { json() } }
            application { testModule(client) }
            externalServices {
              hosts(rawDataServiceUrl) {
                routing {
                  post(rawDataPath) {
                    Json.decodeFromString<RawDataMetaData>(call.receiveText())
                      .shouldBeTypeOf<RawDataMetaData>()
                    call.request.contentType() shouldBe ContentType.Application.Json
                  }
                }
              }
            }

            coEvery { synapseAdminAPIValidator.validateRedactEvent(any(), any()) } returns
              ReferencedEventTooOld.left()
            val response = client.putRedact()

            response shouldHaveStatus Forbidden
          }
        }

        should("allow redaction for events in the past 24 hours") {
          testApplication {
            val client = createClient { install(ContentNegotiation) { json() } }
            application { testModule(client) }

            homeserverWithRouting {
              put("/_matrix/client/v3/rooms/{roomId}/redact/{eventId}/{txnId}") { _ ->
                call.respond(OK)
              }
            }
            coEvery { synapseAdminAPIValidator.validateRedactEvent(any(), any()) } returns
              ValidationSuccess.right()

            val response = client.putRedact()

            response shouldHaveStatus OK
          }
        }
      }
    }

    context("media routes (/_matrix/media/v3/)") {
      context("deprecated routes") {

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
            response.bodyAsText() shouldEqualJson
              """
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
            homeserverWithRouting {
              get("/_matrix/media/v3/download/federated.org/1") { _ -> call.respond(OK, "blimey") }
            }

            val response = client.get("/_matrix/media/v3/download/federated.org/1")

            response shouldHaveStatus OK
            response.bodyAsText() shouldBe "blimey"
          }
        }

        // gematik test cases 10X.01.03, 10X.01.04
        should("relay requests for media on federated servers (renaming endpoint)") {
          testApplication {
            val client = createClient { install(ContentNegotiation) { json() } }
            application { testModule(client) }
            homeserverWithRouting {
              get("/_matrix/media/v3/download/federated.org/1/new-filename.exe") { _ ->
                call.respond(OK, "blimey")
              }
            }

            val response = client.get("/_matrix/media/v3/download/federated.org/1/new-filename.exe")

            response shouldHaveStatus OK
            response.bodyAsText() shouldBe "blimey"
          }
        }

        should("relay requests for thumbnail on federated servers") {
          testApplication {
            val client = createClient { install(ContentNegotiation) { json() } }
            application { testModule(client) }
            homeserverWithRouting {
              get("/_matrix/media/v3/thumbnail/federated.org/1") { _ ->
                if (
                  call.request.queryParameters["height"] == "100" &&
                    call.request.queryParameters["width"] == "200"
                ) {
                  call.respond(OK, "blimey")
                } else {
                  call.respond(BadRequest)
                }
              }
            }

            val response =
              client.get("/_matrix/media/v3/thumbnail/federated.org/1") {
                url {
                  parameters.append("height", "100")
                  parameters.append("width", "200")
                }
              }

            assertSoftly {
              response shouldHaveStatus OK
              response.bodyAsText() shouldBe "blimey"
            }
          }
        }

        should("block requests for unauthenticated media config") {
          testApplication {
            val client = createClient { install(ContentNegotiation) { json() } }
            application { testModule(client) }

            val response = client.get("/_matrix/media/v3/config")

            response shouldHaveStatus NotFound
          }
        }
      }

      // A_26328 - Prüfung eingehender Medienanfragen
      // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#A_26328
      // A_25534 - Fehlschlag Föderationsprüfung
      // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#A_25534
      should("reject requests for media on unfederated servers") {
        testApplication {
          val client = createClient { install(ContentNegotiation) { json() } }
          application { testModule(client) }

          val response = client.get("/_matrix/client/v1/media/download/unfederatedServer/1")

          response shouldHaveStatus Forbidden
          response.bodyAsText() shouldEqualJson
            """
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
          homeserverWithRouting {
            get("/_matrix/client/v1/media/download/federated.org/1") { _ ->
              call.respond(OK, "blimey")
            }
          }

          val response = client.get("/_matrix/client/v1/media/download/federated.org/1")

          response shouldHaveStatus OK
          response.bodyAsText() shouldBe "blimey"
        }
      }

      // gematik test cases 10X.01.03, 10X.01.04
      should("relay requests for media on federated servers (renaming endpoint)") {
        testApplication {
          val client = createClient { install(ContentNegotiation) { json() } }
          application { testModule(client) }
          homeserverWithRouting {
            get("/_matrix/client/v1/media/download/federated.org/1/new-filename.exe") { _ ->
              call.respond(OK, "blimey")
            }
          }

          val response =
            client.get("/_matrix/client/v1/media/download/federated.org/1/new-filename.exe")

          response shouldHaveStatus OK
          response.bodyAsText() shouldBe "blimey"
        }
      }

      should("relay requests for thumbnail on federated servers") {
        testApplication {
          val client = createClient { install(ContentNegotiation) { json() } }
          application { testModule(client) }
          homeserverWithRouting {
            get("/_matrix/client/v1/media/thumbnail/federated.org/1") { _ ->
              if (
                call.request.queryParameters["height"] == "100" &&
                  call.request.queryParameters["width"] == "200"
              ) {
                call.respond(OK, "blimey")
              } else {
                call.respond(BadRequest)
              }
            }
          }

          val response =
            client.get("/_matrix/client/v1/media/thumbnail/federated.org/1") {
              url {
                parameters.append("height", "100")
                parameters.append("width", "200")
              }
            }

          assertSoftly {
            response shouldHaveStatus OK
            response.bodyAsText() shouldBe "blimey"
          }
        }
      }

      should("forward authenticated requests for media config") {
        testApplication {
          val client = createClient { install(ContentNegotiation) { json() } }
          application { testModule(client) }
          homeserverWithRouting {
            get("/_matrix/client/v1/media/config") { _ ->
              call.respond(OK, """{"m.upload.size": 50000000}""")
            }
          }

          val response = client.get("/_matrix/client/v1/media/config")

          response shouldHaveStatus OK
          response.bodyAsText() shouldEqualJson """{"m.upload.size": 50000000}"""
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
                get("/backend/well-known-support/home") { _ ->
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
              routing { get("/backend/well-known-support/home") { _ -> call.respond(NotFound) } }
            }
          }

          val response = client.get("/.well-known/matrix/support")

          response shouldHaveStatus OK
          response.bodyAsText() shouldContain
            """{
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

    // A_26574 - Entschlüsseln von Nachrichten nach Wiederanmeldung
    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.2/#A_26574
    // A_26575 - Ablage von Schlüsseln zum Entschlüsseln von Nachrichten nach Wiederanmeldung
    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.2/#A_26575
    // Weiterleiten der notwendigen Routen für MSC3814 - Dehydrated Devices
    context("support dehydrated devices") {
      should("forward request to get Dehydrated Device") {
        testApplication {
          val client = createClient { install(ContentNegotiation) { json() } }
          application { testModule(client) }
          homeserverWithRouting {
            get("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device") { _ ->
              call.respond(OK, "blimey")
            }
          }

          val response =
            client.get("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device")

          response shouldHaveStatus OK
          response.bodyAsText() shouldBe "blimey"
        }
      }

      should("forward request to get Dehydrated Device events") {
        testApplication {
          val client = createClient { install(ContentNegotiation) { json() } }
          application { testModule(client) }
          homeserverWithRouting {
            post("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device/1/events") { _ ->
              call.respond(OK, "blimey")
            }
          }

          val response =
            client.post("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device/1/events")

          response shouldHaveStatus OK
          response.bodyAsText() shouldBe "blimey"
        }
      }

      should("forward request to create Dehydrated Device") {
        testApplication {
          val client = createClient { install(ContentNegotiation) { json() } }
          application { testModule(client) }
          homeserverWithRouting {
            put("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device") { _ ->
              call.respond(OK, "blimey")
            }
          }

          val response =
            client.put("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device")

          response shouldHaveStatus OK
          response.bodyAsText() shouldBe "blimey"
        }
      }

      should("forward request to delete Dehydrated Device") {
        testApplication {
          val client = createClient { install(ContentNegotiation) { json() } }
          application { testModule(client) }
          homeserverWithRouting {
            delete("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device") { _ ->
              call.respond(OK, "blimey")
            }
          }

          val response =
            client.delete("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device")

          response shouldHaveStatus OK
          response.bodyAsText() shouldBe "blimey"
        }
      }
    }
  })
