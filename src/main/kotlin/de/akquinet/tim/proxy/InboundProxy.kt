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
import de.akquinet.tim.proxy.client.AccessTokenToUserIdAuthenticationFunction
import de.akquinet.tim.proxy.client.InboundClientRoutes
import de.akquinet.tim.proxy.client.model.route.installPushrulesRoutesForBadRequest
import de.akquinet.tim.proxy.federation.BerechtigungsstufeEinsAuthenticationProvider
import de.akquinet.tim.proxy.federation.InboundFederationRoutes
import de.akquinet.tim.proxy.federation.berechtigungsstufeEinsCheck
import de.akquinet.tim.proxy.util.customMatrixServer
import de.akquinet.tim.proxy.util.metricsModule
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.folivo.trixnity.clientserverapi.server.matrixAccessTokenAuth
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.slf4j.event.Level

interface InboundProxy {
    suspend fun start(): ApplicationEngine
}

class InboundProxyImpl(
    private val inboundProxyConfiguration: ProxyConfiguration.InboundProxyConfiguration,
    private val berechtigungsstufeEinsService: BerechtigungsstufeEinsService,
    private val accessTokenToUserIdAuthenticationFunction: AccessTokenToUserIdAuthenticationFunction,
    private val inboundClientRoutes: InboundClientRoutes,
    private val inboundFederationRoutes: InboundFederationRoutes,
    private val httpClient: HttpClient
) : InboundProxy {
    override suspend fun start(): ApplicationEngine =
        embeddedServer(Netty, port = this@InboundProxyImpl.inboundProxyConfiguration.port) {
            metricsModule()
            install(CallLogging) {
                filter { call -> call.response.status() == HttpStatusCode.NotFound }
                level = Level.WARN
                format { call ->
                    val headerList = call.request.headers.entries().map { "${it.key}: ${it.value}" }
                    val uri = call.request.uri
                    val method = call.request.httpMethod.value

                    "inbound request on unhandled path $uri with method $method and headers $headerList"
                }
            }

            val json = createMatrixEventJson()
            configureMatrixFederationCheckAuth()


            customMatrixServer(json) {
                installPushrulesRoutesForBadRequest()

                route("/_matrix/federation/v1/openid/userinfo") {
                    handle {
                        forwardRequest(
                            call,
                            httpClient,
                            call.request.uri.mergeToUrl(inboundProxyConfiguration.homeserverUrl),
                            null
                        )
                    }
                }

                inboundClientRoutes.apply { openClientServerApiRoutes() }
                authenticate("matrix-access-token-auth") {
                    inboundClientRoutes.apply { clientServerApiRoutes() }
                }

                authenticate(BerechtigungsstufeEinsAuthenticationProvider.IDENTIFIER) {
                    inboundFederationRoutes.apply { serverServerApiRoutes() }
                    inboundFederationRoutes.apply { serverServerRawDataRoutes() }
                }

                route("/_matrix/client/v1/login/get_token") {
                    handle {
                        throw MatrixServerException(
                            HttpStatusCode.NotFound,
                            ErrorResponse.NotFound("No resource was found for this request.")
                        )
                    }
                }

                route("/_matrix/federation/v1/publicRooms") {
                    handle {
                        throw MatrixServerException(
                            HttpStatusCode.Forbidden,
                            ErrorResponse.Forbidden("A_26520")
                        )
                    }
                }

                route("/_synapse/admin/{...}") {
                    handle {
                        forwardRequest(
                            call,
                            httpClient,
                            call.request.uri.mergeToUrl(inboundProxyConfiguration.homeserverUrl),
                            null
                        )
                    }
                }

                route("/actuator/health") {
                    handle {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }.start()

    private fun Application.configureMatrixFederationCheckAuth() {
        install(Authentication) {
            berechtigungsstufeEinsCheck(checkerService = berechtigungsstufeEinsService) {
                proxyMode = BerechtigungsstufeEinsAuthenticationProvider.ProxyMode.INBOUND
                enforceDomainList = inboundProxyConfiguration.enforceDomainList
            }
            matrixAccessTokenAuth("matrix-access-token-auth") {
                authenticationFunction = accessTokenToUserIdAuthenticationFunction
            }
        }
    }

}
