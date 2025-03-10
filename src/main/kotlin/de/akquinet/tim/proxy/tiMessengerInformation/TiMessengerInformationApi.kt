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
package de.akquinet.tim.proxy.tiMessengerInformation

import arrow.core.Either
import arrow.core.merge
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.authorization.MatrixAuthorizationService
import de.akquinet.tim.proxy.federation.FederationListCache
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

class TiMessengerInformationApi(
    val configuration: ProxyConfiguration.TiMessengerInformationConfiguration,
    private val federationListCache: FederationListCache,
    private val matrixAuthorizationService: MatrixAuthorizationService,
) {
    private val basePath = "/tim-information"

    fun start(): ApplicationEngine =
        embeddedServer(Netty, applicationEngineEnvironment {
            connector {
                port = configuration.port
            }
            module {
                routing {
                    tiMessengerInformationRoutes()
                }
            }
        }).start()

    fun Route.tiMessengerInformationRoutes() {
        route(basePath) {
            get("/") {
                tiMessengerInformation(call.request).handle(call)
            }

            get("/v1/server/findByIk") {
                getServerNameByIkNumber(call.request).handle(call)
            }

            get("/v1/server/isInsurance") {
                getIsInsuranceByServerName(call.request).handle(call)
            }
        }
    }

    private suspend fun Either<TiMessengerInformationError, TiMessengerInformationResult>.handle(call: ApplicationCall) {
        val status = fold(ifLeft = { error -> error.statusCode() }, ifRight = { HttpStatusCode.OK })
        val result = mapLeft { error -> error.toErrorResult() }.merge()
        call.respondText(result.encodeToString(), ContentType.Application.Json, status)
    }

    private suspend fun authorize(headers: Headers): Either<TiMessengerInformationError.Unauthorized, Unit> =
        matrixAuthorizationService.authorizeWithoutMxid(headers)
            .mapLeft { matrixAuthorizationError -> TiMessengerInformationError.Unauthorized(matrixAuthorizationError) }

    private suspend fun tiMessengerInformation(request: ApplicationRequest): Either<TiMessengerInformationError, TiMessengerInformation> =
        either {
            authorize(request.headers).bind()

            TiMessengerInformation(
                title = "Contact Information API des TI-Messengers",
                version = "1.0.0",
            )
        }

    private suspend fun getServerNameByIkNumber(request: ApplicationRequest): Either<TiMessengerInformationError, ServerNameResult> =
        either {
            authorize(request.headers).bind()

            val ikNumber = request.queryParameters["ikNumber"]

            ensureNotNull(ikNumber) {
                TiMessengerInformationError.MissingParameter("missing query parameter 'ikNumber'")
            }

            val domain = federationListCache.domains.value.firstOrNull { it.ik?.contains(ikNumber) ?: false }

            ensureNotNull(domain) {
                TiMessengerInformationError.NoMatch("no domain associated with ikNumber=$ikNumber")
            }

            ServerNameResult(domain.domain)
        }

    private suspend fun getIsInsuranceByServerName(request: ApplicationRequest): Either<TiMessengerInformationError, IsInsuranceResult> =
        either {
            authorize(request.headers).bind()

            val serverName = request.queryParameters["serverName"]

            ensureNotNull(serverName) {
                TiMessengerInformationError.MissingParameter("missing query parameter 'serverName'")
            }

            val domain = federationListCache.domains.value.firstOrNull { it.domain == serverName }

            ensureNotNull(domain) {
                TiMessengerInformationError.NoMatch("no domain associated with serverName=$serverName")
            }

            IsInsuranceResult(domain.isInsurance)
        }
}