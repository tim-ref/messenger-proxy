/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.client

import io.ktor.server.auth.*
import net.folivo.trixnity.clientserverapi.server.AccessTokenAuthenticationFunctionResult
import net.folivo.trixnity.clientserverapi.server.UserAccessTokenCredentials
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.UserId

interface AccessTokenToUserIdAuthenticationFunction :
    suspend (UserAccessTokenCredentials) -> AccessTokenAuthenticationFunctionResult

@JvmInline
value class UserIdPrincipal(val userId: UserId) : Principal

class AccessTokenToUserIdAuthenticationFunctionImpl(private val accessTokenToUserId: AccessTokenToUserId) :
    AccessTokenToUserIdAuthenticationFunction {
    override suspend operator fun invoke(credentials: UserAccessTokenCredentials): AccessTokenAuthenticationFunctionResult {
        val userId = kotlin.runCatching { accessTokenToUserId(credentials.accessToken) }
        return AccessTokenAuthenticationFunctionResult(
            principal = userId.getOrNull()?.let { UserIdPrincipal(it) },
            cause = when (val cause = userId.exceptionOrNull()) {
                null -> null
                is MatrixServerException -> {
                    when (cause.errorResponse) {
                        is ErrorResponse.MissingToken -> AuthenticationFailedCause.NoCredentials
                        is ErrorResponse.UnknownToken -> AuthenticationFailedCause.InvalidCredentials
                        else -> AuthenticationFailedCause.Error(cause.errorResponse.error ?: "unknown")
                    }
                }

                else -> AuthenticationFailedCause.Error(cause.message ?: "unknown")
            }
        )
    }
}
