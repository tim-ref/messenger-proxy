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
package de.akquinet.tim.proxy

import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.federation.BerechtigungsstufeEinsAuthenticationProvider
import de.akquinet.tim.proxy.federation.BerechtigungsstufeEinsAuthenticationProvider.ProxyMode
import de.akquinet.tim.proxy.federation.Destination
import de.akquinet.tim.proxy.federation.OutboundFederationRoutes
import de.akquinet.tim.proxy.federation.berechtigungsstufeEinsCheck
import de.akquinet.tim.proxy.util.metricsModule
import io.ktor.client.*
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
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException

private val kLog = KotlinLogging.logger { }

interface OutboundProxy {
    suspend fun start(): ApplicationEngine
}

@Suppress("ExtractKtorModule")
class OutboundProxyImpl(
    private val outboundProxyConfiguration: ProxyConfiguration.OutboundProxyConfiguration,
    private val berechtigungsstufeEinsService: BerechtigungsstufeEinsService,
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

    override suspend fun start(): ApplicationEngine =
        embeddedServer(
            Netty,
            port = this@OutboundProxyImpl.outboundProxyConfiguration.port,
            configure = {
                channelPipelineConfig = {
                    addBefore("http1", "tunnel", ProxyConnectionHandler(outboundProxyCertificateManager))
                }
            }) {
            metricsModule()
            configureMatrixFederationCheckAuth()

            matrixApiServer(Json) {
                authenticate(BerechtigungsstufeEinsAuthenticationProvider.IDENTIFIER) {
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

                            kLog.warn(
                                "outbound request on unhandled path {} with method {} from host {}, body {} and headers {}",
                                call.request.uri, call.request.httpMethod, host, requestBody, headerList
                            )

                            throw MatrixServerException(HttpStatusCode.NotFound, ErrorResponse.NotFound(""))
                        }

                    }
                }
            }
        }.start()

    private fun Application.configureMatrixFederationCheckAuth() {
        install(Authentication) {
            berechtigungsstufeEinsCheck(checkerService = berechtigungsstufeEinsService) {
                proxyMode = ProxyMode.OUTBOUND
                enforceDomainList = outboundProxyConfiguration.enforceDomainList
            }
        }
    }
}
