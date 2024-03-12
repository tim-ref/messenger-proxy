/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy

import de.akquinet.timref.proxy.federation.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import de.akquinet.timref.proxy.federation.MatrixFederationCheckAuth.Mode.OUTBOUND
import io.ktor.client.*
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
                install(Authentication) {
                    matrixFederationCheckAuth("federation-check") {
                        federationAllowed = federationListCache.domains
                        mode = OUTBOUND
                        enforceDomainList = outboundProxyConfiguration.enforceDomainList
                    }
                }

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

                                kLog.warn { "outbound request on unhandled path ${call.request.uri} with method ${call.request.httpMethod} from host $host, body $requestBody and headers $headerList" }
                                kLog.debug { "outbound request on unhandled path ${call.request.uri} with method ${call.request.httpMethod} from host $host, body $requestBody and headers $headerList" }
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
}
