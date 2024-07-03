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

import de.akquinet.tim.proxy.federation.Destination
import de.akquinet.tim.proxy.federation.FederationListCache
import de.akquinet.tim.proxy.federation.MatrixFederationCheckAuth.Mode.OUTBOUND
import de.akquinet.tim.proxy.federation.OutboundFederationRoutes
import de.akquinet.tim.proxy.federation.matrixFederationCheckAuth
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.takeFrom
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineEnvironmentBuilder
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException

private val kLog = KotlinLogging.logger { }

interface OutboundProxy {
    suspend fun start(env: ApplicationEngineEnvironmentBuilder.() -> Unit = {}): ApplicationEngine
}

@Suppress("ExtractKtorModule")
class OutboundProxyImpl(
    private val outboundProxyConfiguration: ProxyConfiguration.OutboundProxyConfiguration,
    private val federationListCache: FederationListCache,
    private val outboundFederationRoutes: OutboundFederationRoutes,
    private val outboundProxyCertificateManager: OutboundProxyCertificateManager,
    private val httpClient: HttpClient
) : OutboundProxy {

    private fun getDestinationUrl(call: ApplicationRequest): Url = URLBuilder().apply {
        takeFrom(call.uri)
        val destination = call.headers[HttpHeaders.Host]?.let { Destination.from(it) }
            ?: throw IllegalArgumentException("host header was not set")
        protocol = URLProtocol.HTTPS
        host = destination.host
        port = destination.port
    }.build()

    override suspend fun start(env: ApplicationEngineEnvironmentBuilder.() -> Unit): ApplicationEngine =
        embeddedServer(Netty, applicationEngineEnvironment {
            connector {
                port = this@OutboundProxyImpl.outboundProxyConfiguration.port
            }
            module {
                configureMatrixFederationCheckAuth()

                matrixApiServer(Json) {
                    authenticate("federation-check") {
                        outboundFederationRoutes.apply { serverServerApiRoutes() }
                        outboundFederationRoutes.apply { serverServerRawDataRoutes() }
                    }

                    route("/actuator/health") {
                        handle {
                            call.respond(HttpStatusCode.OK)
                        }
                    }

                    route("/_matrix/push/{...}") {
                        handle {
                            forwardRequest(call, httpClient, getDestinationUrl(call = call.request), null)
                        }
                    }

                    route("/recaptcha/api/siteverify") {
                        handle {
                            forwardRequest(call, httpClient, getDestinationUrl(call = call.request), null)
                        }
                    }

                    route("{...}") {
                        handle {
                            val host = getDestinationUrl(call = call.request).host
                            if (host == outboundProxyConfiguration.ssoDomain || "https://$host/" == outboundProxyConfiguration.ssoDomain) {
                                forwardRequest(call, httpClient, getDestinationUrl(call = call.request), null)
                            } else {
                                val requestBody = call.receive<String>()
                                val headerList = call.request.headers.entries().map { "${it.key}: ${it.value}" }

                                kLog.warn("outbound request on unhandled path {} with method {} from host {}, body {} and headers {}",
                                    call.request.uri, call.request.httpMethod, host, requestBody, headerList)
                                kLog.debug("outbound request on unhandled path {} with method {} from host {}, body {} and headers {}",
                                    call.request.uri, call.request.httpMethod, host, requestBody, headerList)
                                throw MatrixServerException(HttpStatusCode.NotFound, ErrorResponse.NotFound())
                            }

                        }
                    }
                }
            }
            env()
        }) {
            channelPipelineConfig = {
                addBefore("http1", "tunnel", ProxyConnectionHandler(outboundProxyCertificateManager))
            }
        }.start()

    private fun Application.configureMatrixFederationCheckAuth() {
        install(Authentication) {
            matrixFederationCheckAuth("federation-check") {
                federationAllowed = federationListCache.domains
                mode = OUTBOUND
                enforceDomainList = outboundProxyConfiguration.enforceDomainList
            }
        }
    }
}
