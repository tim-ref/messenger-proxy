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
/**
 * Implementiert die Berechtigungsstufe 1 (Föderationsprüfung) als Ktor-Authentication-Modul
 */
package de.akquinet.tim.proxy.federation

import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.auth.AuthenticationFailedCause.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging
import net.folivo.trixnity.api.server.AuthRequired
import net.folivo.trixnity.api.server.withoutAuthAttributeKey
import net.folivo.trixnity.core.ErrorResponse

private val log = KotlinLogging.logger { }

class BerechtigungsstufeEinsAuthenticationProvider internal constructor(
    private val berechtigungsstufeEinsService: BerechtigungsstufeEinsService,
    private val config: Config,
) : AuthenticationProvider(config) {

    companion object {
        const val IDENTIFIER = "bs1-federation-check"
    }

    enum class ProxyMode(val domainParameterName: String) {
        INBOUND("origin"),
        OUTBOUND("destination")
    }

    class Config internal constructor() : AuthenticationProvider.Config(IDENTIFIER) {
        var proxyMode: ProxyMode = ProxyMode.INBOUND
        var enforceDomainList: Boolean = true
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val parsedAuthorizationHeader = call.request.parseAuthorizationHeader()
        val origin = (parsedAuthorizationHeader as? HttpAuthHeader.Parameterized)?.let { authHeader ->
            authHeader.takeIf { it.authScheme.equals("X-Matrix", ignoreCase = true) }
                ?.parameter(config.proxyMode.domainParameterName)
        }

        val cause = if (origin == null) {
            NoCredentials
        } else {
            if (berechtigungsstufeEinsService.isUnfederatedDomain(origin)) {
                InvalidCredentials
            } else {
                null
            }
        }

        if (config.enforceDomainList && cause != null)
            log.info { """authorization failed: url="${call.request.uri}" mode=${config.proxyMode} origin=$origin cause=$cause (authHeader=${call.request.headers[HttpHeaders.Authorization]})""" }
        else
            log.trace { """url="${call.request.uri}" mode=${config.proxyMode} origin=$origin (authHeader=${call.request.headers[HttpHeaders.Authorization]})""" }

        if (config.enforceDomainList && cause != null) {
            context.challenge("MatrixAccessTokenAuth", cause) { challenge, challengeCall ->
                when (cause) {
                    NoCredentials ->
                        challengeCall.respond<ErrorResponse>(
                            HttpStatusCode.Unauthorized,
                            ErrorResponse.Unauthorized("missing X-Matrix authorization header with ${config.proxyMode.domainParameterName} field")
                        )

                    InvalidCredentials -> challengeCall.respond<ErrorResponse>(
                        HttpStatusCode.Forbidden,
                        ErrorResponse.Forbidden("not part of federation")
                    )

                    is Error -> challengeCall.respond<ErrorResponse>(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse.Unauthorized(cause.message)
                    )
                }
                challenge.complete()
            }
        }
    }
}

fun AuthenticationConfig.berechtigungsstufeEinsCheck(
    checkerService: BerechtigungsstufeEinsService,
    configure: BerechtigungsstufeEinsAuthenticationProvider.Config.() -> Unit
) {
    val provider = BerechtigungsstufeEinsAuthenticationProvider(
        berechtigungsstufeEinsService = checkerService,
        config = BerechtigungsstufeEinsAuthenticationProvider.Config()
            .apply(configure)
            .apply {
                skipWhen {
                    it.attributes.getOrNull(withoutAuthAttributeKey) in listOf(AuthRequired.NO, AuthRequired.OPTIONAL)
                }
            }
    )
    register(provider)
}
