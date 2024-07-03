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

package de.akquinet.tim.proxy

import de.akquinet.tim.proxy.client.AccessTokenToUserIdAuthenticationFunction
import de.akquinet.tim.proxy.client.InboundClientRoutes
import de.akquinet.tim.proxy.federation.FederationListCache
import de.akquinet.tim.proxy.federation.InboundFederationRoutes
import de.akquinet.tim.proxy.federation.MatrixFederationCheckAuth.Mode.INBOUND
import de.akquinet.tim.proxy.federation.matrixFederationCheckAuth
import de.akquinet.tim.proxy.client.model.route.installPushrulesRoutesForBadRequest
import de.akquinet.tim.proxy.util.customMatrixServer
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging
import net.folivo.trixnity.clientserverapi.server.ConvertMediaPlugin
import net.folivo.trixnity.clientserverapi.server.matrixAccessTokenAuth
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.serialization.createMatrixEventJson

private val kLog = KotlinLogging.logger { }

interface InboundProxy {
    suspend fun start(env: ApplicationEngineEnvironmentBuilder.() -> Unit = {}): ApplicationEngine
}

class InboundProxyImpl(
    private val inboundProxyConfiguration: ProxyConfiguration.InboundProxyConfiguration,
    private val federationListCache: FederationListCache,
    private val accessTokenToUserIdAuthenticationFunction: AccessTokenToUserIdAuthenticationFunction,
    private val inboundClientRoutes: InboundClientRoutes,
    private val inboundFederationRoutes: InboundFederationRoutes,
    private val httpClient: HttpClient
) : InboundProxy {
    override suspend fun start(env: ApplicationEngineEnvironmentBuilder.() -> Unit): ApplicationEngine =
        embeddedServer(Netty, applicationEngineEnvironment {
            connector {
                port = this@InboundProxyImpl.inboundProxyConfiguration.port
            }
            module {
                val json = createMatrixEventJson()
                configureMatrixFederationCheckAuth()


                customMatrixServer(json) {
                    installPushrulesRoutesForBadRequest()

                    route("/_matrix/federation/v1/openid/userinfo") {
                        handle {
                            forwardRequest(call, httpClient, call.request.uri.mergeToUrl(inboundProxyConfiguration.homeserverUrl), null)
                        }
                    }
                    with(inboundClientRoutes) {
                        clientServerApiRoutes()
                    }
                    authenticate("federation-check") {
                        inboundFederationRoutes.apply { serverServerApiRoutes() }
                        inboundFederationRoutes.apply { serverServerRawDataRoutes() }
                    }

                    route("/_synapse/admin/{...}") {
                        handle {
                            forwardRequest(call, httpClient, call.request.uri.mergeToUrl(inboundProxyConfiguration.homeserverUrl), null)
                        }
                    }

                    route("/actuator/health") {
                        handle {
                            call.respond(HttpStatusCode.OK)
                        }
                    }

                    route("{...}") {
                        customInstallMatrixClientServerApiServer()
                        handle {
                            val requestBody = call.receive<String>()
                            val headerList = call.request.headers.entries().map { "${it.key}: ${it.value}" }

                            kLog.warn { "inbound request on unhandled path ${call.request.uri} with method ${call.request.httpMethod.value}, body $requestBody and headers $headerList" }
                            throw MatrixServerException(HttpStatusCode.NotFound, ErrorResponse.NotFound())
                        }
                    }
                }
            }
            env()
        }).start()

    private fun Application.configureMatrixFederationCheckAuth() {
        install(Authentication) {
            matrixFederationCheckAuth("federation-check") {
                federationAllowed = federationListCache.domains
                mode = INBOUND
                enforceDomainList = inboundProxyConfiguration.enforceDomainList
            }
            matrixAccessTokenAuth("matrix-access-token-auth") {
                authenticationFunction = accessTokenToUserIdAuthenticationFunction
            }
        }
    }

    // this function is copied from net.folivo.trixnity.clientserverapi.server.installMatrixClientServerApiServer
    // and complemented by the allowance of the custom header "Useragent"
    private fun Route.customInstallMatrixClientServerApiServer() {
        // TODO implement rate limiting
        install(ConvertMediaPlugin)
        // see also https://spec.matrix.org/v1.6/client-server-api/#web-browser-clients
        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowHeader("X-Requested-With")
            // TODO this can be removed or changed because of future changes in the raw data specification
            allowHeader("Useragent")
            allowHeader("x-tim-user-agent")
        }
    }
}
