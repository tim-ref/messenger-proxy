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
package de.akquinet.tim.proxy.integrationtests

import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.federation.InboundFederationRoutesImpl
import de.akquinet.tim.proxy.federation.OutboundFederationRoutesImpl
import de.akquinet.tim.proxy.homeserverWithRouting
import de.akquinet.tim.proxy.inboundProxyWithServerServerRoutes
import de.akquinet.tim.proxy.mocks.RawDataServiceStub
import de.akquinet.tim.proxy.outboundProxyWithServerServerRoutes
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlin.time.Duration.Companion.hours

// see A_26224 resp. TIMREF-2045
class PublicKeysEndpointsIT :
  DescribeSpec({
    context("inbound") {
      fun inboundFederationRoutes(client: HttpClient) =
        InboundFederationRoutesImpl(
          config =
            ProxyConfiguration.InboundProxyConfiguration(
              homeserverUrl = "http://localhost:8083",
              port = 8090,
              synapseHealthEndpoint = "/health",
              synapsePort = 443,
              enforceDomainList = true,
              accessTokenToUserIdCacheDuration = 1.hours,
            ),
          httpClient = client,
          rawDataService = RawDataServiceStub(),
          timAuthorizationCheckConfiguration = mockk(),
          berechtigungsstufeEinsService = mockk(),
          synapseAdminAPIValidator = mockk(),
        )

      it("should answer /_matrix/key/v2/server/{keyId}") {
        testApplication {
          inboundProxyWithServerServerRoutes(inboundFederationRoutes(client))
          homeserverWithRouting {
            get("/_matrix/key/v2/server/someKeyId") { _ ->
              call.respondText(
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
                text = "blubb",
              )
            }
          }

          val response = client.get("/_matrix/key/v2/server/someKeyId")
          response.bodyAsText() shouldBe "blubb"
        }
      }

      it("should answer /_matrix/key/v2/server") {
        testApplication {
          inboundProxyWithServerServerRoutes(inboundFederationRoutes(client))
          homeserverWithRouting {
            get("/_matrix/key/v2/server") { _ ->
              call.respondText(
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
                text = "blubb",
              )
            }
          }

          val response = client.get("/_matrix/key/v2/server")
          response.bodyAsText() shouldBe "blubb"
        }
      }

      it("should answer /_matrix/key/v2/query/{serverName}/{keyId}") {
        testApplication {
          inboundProxyWithServerServerRoutes(inboundFederationRoutes(client))
          homeserverWithRouting {
            get("/_matrix/key/v2/query/someServer/someKeyId") { _ ->
              call.respondText(
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
                text = "blubb",
              )
            }
          }

          val response = client.get("/_matrix/key/v2/query/someServer/someKeyId")
          response.bodyAsText() shouldBe "blubb"
        }
      }

      it("should answer /_matrix/key/v2/query/{serverName}") {
        testApplication {
          inboundProxyWithServerServerRoutes(inboundFederationRoutes(client))
          homeserverWithRouting {
            get("/_matrix/key/v2/query/someServer") { _ ->
              call.respondText(
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
                text = "blubb",
              )
            }
          }

          val response = client.get("/_matrix/key/v2/query/someServer")
          response.bodyAsText() shouldBe "blubb"
        }
      }
    }

    context("outbound") {
      it("should NOT answer /_matrix/key/v2/server/{keyId}") {
        testApplication {
          outboundProxyWithServerServerRoutes(
            OutboundFederationRoutesImpl(client, RawDataServiceStub())
          )

          val response = client.get("/_matrix/key/v2/server/someKeyId")
          response.status shouldBe HttpStatusCode.NotFound
        }
      }

      it("should answer /_matrix/key/v2/server") {
        testApplication {
          outboundProxyWithServerServerRoutes(
            OutboundFederationRoutesImpl(client, RawDataServiceStub())
          )

          val response = client.get("/_matrix/key/v2/server")
          response.status shouldNotBe HttpStatusCode.NotFound
        }
      }

      it("should NOT answer /_matrix/key/v2/query/{serverName}/{keyId}") {
        testApplication {
          outboundProxyWithServerServerRoutes(
            OutboundFederationRoutesImpl(client, RawDataServiceStub())
          )

          val response = client.get("/_matrix/key/v2/query/someServer/someKeyId")
          response.status shouldBe HttpStatusCode.NotFound
        }
      }

      it("should answer /_matrix/key/v2/query/{serverName}") {
        testApplication {
          outboundProxyWithServerServerRoutes(
            OutboundFederationRoutesImpl(client, RawDataServiceStub())
          )

          val response = client.get("/_matrix/key/v2/query/someServer")
          response.status shouldNotBe HttpStatusCode.NotFound
        }
      }
    }
  })
