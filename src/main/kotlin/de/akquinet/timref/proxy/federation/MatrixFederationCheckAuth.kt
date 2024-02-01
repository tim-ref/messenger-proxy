/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.federation

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.auth.AuthenticationFailedCause.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mu.KotlinLogging
import net.folivo.trixnity.api.server.withoutAuthAttributeKey
import net.folivo.trixnity.core.ErrorResponse

private val log = KotlinLogging.logger { }

class MatrixFederationCheckAuth internal constructor(
    private val config: Config,
) : AuthenticationProvider(config) {

    enum class Mode(val headerCheck: String) {
        INBOUND("origin"), OUTBOUND("destination")
    }

    class Config internal constructor(name: String? = null) : AuthenticationProvider.Config(name) {
        var federationAllowed: StateFlow<Set<String>> = MutableStateFlow(emptySet())
        var mode: Mode = Mode.INBOUND
        var enforceDomainList: Boolean = true
    }

    private fun cleanUpURL(url: String): String = url.replace(Regex("/['\"]+/g"), "").removePrefix("https://").removePrefix("http://").trim()

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val authHeader = call.request.parseAuthorizationHeader()
        val origin =
            if (authHeader is HttpAuthHeader.Parameterized) {
                if (!authHeader.authScheme.equals("X-Matrix", ignoreCase = true)) null
                else authHeader.parameter(config.mode.headerCheck)
            } else null
        val cause =
            if (origin == null) NoCredentials
            else {
                val isRequestAllowed = config.federationAllowed.value.contains(cleanUpURL(origin))
                if (isRequestAllowed) null
                else InvalidCredentials
            }
        if (config.enforceDomainList && cause != null)
            log.info { """authorization failed: url="${call.request.uri}" mode=${config.mode} origin=$origin cause=$cause (authHeader=${call.request.headers[HttpHeaders.Authorization]})""" }
        else
            log.trace { """url="${call.request.uri}" mode=${config.mode} origin=$origin (authHeader=${call.request.headers[HttpHeaders.Authorization]})""" }

        if (config.enforceDomainList && cause != null) {
            context.challenge("MatrixAccessTokenAuth", cause) { challenge, challengeCall ->
                when (cause) {
                    NoCredentials ->
                        challengeCall.respond<ErrorResponse>(
                            HttpStatusCode.Unauthorized,
                            ErrorResponse.Unauthorized("missing X-Matrix authorization header with ${config.mode.headerCheck} field")
                        )

                    InvalidCredentials -> challengeCall.respond<ErrorResponse>(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse.Unauthorized("not part of federation")
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

fun AuthenticationConfig.matrixFederationCheckAuth(
    name: String? = null,
    configure: MatrixFederationCheckAuth.Config.() -> Unit
) {
    val provider = MatrixFederationCheckAuth(
        MatrixFederationCheckAuth.Config(name)
            .apply(configure)
            .apply {
                skipWhen {
                    it.attributes.getOrNull(withoutAuthAttributeKey) == true
                }
            }
    )
    register(provider)
}
