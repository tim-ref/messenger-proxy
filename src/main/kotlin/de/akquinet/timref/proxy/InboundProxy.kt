/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy

import de.akquinet.timref.proxy.client.AccessTokenToUserIdAuthenticationFunction
import de.akquinet.timref.proxy.client.InboundClientRoutes
import de.akquinet.timref.proxy.federation.FederationListCache
import de.akquinet.timref.proxy.federation.InboundFederationRoutes
import de.akquinet.timref.proxy.federation.MatrixFederationCheckAuth.Mode.INBOUND
import de.akquinet.timref.proxy.federation.matrixFederationCheckAuth
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
import net.folivo.trixnity.api.server.matrixApiServer
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


                matrixApiServer(json) {
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
                            if (!inboundProxyConfiguration.enforceDomainList) {
                                forwardRequest(call, httpClient, call.request.uri.mergeToUrl(inboundProxyConfiguration.homeserverUrl), null)
                            } else {
                                val requestBody = call.receive<String>()
                                val headerList = call.request.headers.entries().map { "${it.key}: ${it.value}" }

                                kLog.warn { "inbound request on unhandled path ${call.request.uri} with method ${call.request.httpMethod}, body $requestBody and headers $headerList" }
                                kLog.debug { "inbound request on unhandled path ${call.request.uri} with method ${call.request.httpMethod}, body $requestBody and headers $headerList" }
                                throw MatrixServerException(HttpStatusCode.NotFound, ErrorResponse.NotFound())
                            }
                        }
                    }
                }
            }
            env()
        }).start()

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
        }
    }
}
