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

import de.akquinet.tim.proxy.InviteRejectionPolicy
import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.TimAuthorizationCheckConcept
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.enforcer.RequestPolicyEnforcer
import de.akquinet.tim.proxy.mocks.FederationListCacheMock
import de.akquinet.tim.proxy.rawdata.RawDataServiceImpl
import de.akquinet.tim.proxy.util.customMatrixServer
import de.akquinet.tim.proxy.validation.RequestContentValidator
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.ktor.client.shouldHaveHeader
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.json.Json

class InboundClientRoutesSSORedirectTest :
  ShouldSpec({
    fun Application.testModule(client: HttpClient) {
      val inboundProxyConfig =
        ProxyConfiguration.InboundProxyConfiguration(
          enforceDomainList = true,
          homeserverUrl = "https://internal-matrix-server:8090",
          synapseHealthEndpoint = "/health",
          synapsePort = 443,
          port = 8090,
          accessTokenToUserIdCacheDuration = 1.hours,
        )
      val logInfoConfig =
        ProxyConfiguration.LogInfoConfig(
          url = "https://localhost:1234/add-performance-data",
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

      val rawDataService = RawDataServiceImpl(logInfoConfig, client)

      val bsEinsService = BerechtigungsstufeEinsService(FederationListCacheMock())

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
          synapseAdminAPIValidator = mockk(relaxed = true),
          requestPolicyEnforcer = RequestPolicyEnforcer(),
        )

      customMatrixServer(Json) {
        route("/") { inboundClientRoutes.apply { clientServerApiRoutes() } }
      }
    }

    fun ApplicationTestBuilder.homeserverWithRouting(configuration: Routing.() -> Unit) {
      externalServices {
        hosts("https://internal-matrix-server:8090") { routing { configuration() } }
      }
    }

    context("relay requests after SSO login") {
      should("forward correct request") {
        testApplication {
          val client = createClient { followRedirects = false }
          application { testModule(client) }
          homeserverWithRouting {
            get("/_matrix/client/v3/login/sso/redirect") { _ ->
              val redirectUrl = call.request.queryParameters["redirectUrl"]
              if (redirectUrl != null) {
                call.respondRedirect(redirectUrl)
              } else {
                call.respond(BadRequest)
              }
            }
          }

          val response =
            client.get("/_matrix/client/v3/login/sso/redirect") {
              url { parameters.append("redirectUrl", "https://www.gematik.de") }
            }

          assertSoftly {
            response shouldHaveStatus Found
            response.shouldHaveHeader("Location", "https://www.gematik.de")
          }
        }
      }

      should("forward request with missing redirectUrl") {
        testApplication {
          val client = createClient { followRedirects = false }
          application { testModule(client) }
          homeserverWithRouting {
            get("/_matrix/client/v3/login/sso/redirect") { _ ->
              val redirectUrl = call.request.queryParameters["redirectUrl"]
              if (redirectUrl != null) {
                call.respondRedirect(redirectUrl)
              } else {
                call.respond(BadRequest)
              }
            }
          }

          val response = client.get("/_matrix/client/v3/login/sso/redirect")

          response shouldHaveStatus BadRequest
        }
      }
    }

    context("relay requests after SSO login with path parameter") {
      should("forward correct request") {
        testApplication {
          val client = createClient { followRedirects = false }
          application { testModule(client) }
          homeserverWithRouting {
            get("/_matrix/client/v3/login/sso/redirect/{idpId}") { _ ->
              val redirectUrl = call.request.queryParameters["redirectUrl"]
              if (redirectUrl != null) {
                call.respondRedirect(redirectUrl)
              } else {
                call.respond(BadRequest)
              }
            }
          }

          val response =
            client.get("/_matrix/client/v3/login/sso/redirect/someIdpId") {
              url { parameters.append("redirectUrl", "https://www.gematik.de") }
            }

          assertSoftly {
            response shouldHaveStatus Found
            response.shouldHaveHeader("Location", "https://www.gematik.de")
          }
        }
      }

      // this previously answered NOT_FOUND because
      // "redirectUrl" was non-nullable in the definition of "SSORedirectTo.kt"
      should("forward request with missing redirectUrl") {
        testApplication {
          val client = createClient { followRedirects = false }
          application { testModule(client) }
          homeserverWithRouting {
            get("/_matrix/client/v3/login/sso/redirect/{idpId}") { _ ->
              val redirectUrl = call.request.queryParameters["redirectUrl"]
              if (redirectUrl != null) {
                call.respondRedirect(redirectUrl)
              } else {
                call.respond(BadRequest)
              }
            }
          }

          val response = client.get("/_matrix/client/v3/login/sso/redirect/someIdpId")

          response shouldHaveStatus BadRequest
        }
      }
    }
  })
