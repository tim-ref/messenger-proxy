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
package de.akquinet.tim.proxy.authorization

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.ktor.http.*

interface MatrixAuthorizationService {
    suspend fun authorize(headers: Headers): Either<MatrixAuthorizationError, String>

    suspend fun authorizeWithoutMxid(headers: Headers): Either<MatrixAuthorizationError, Unit>
}

class MatrixAuthorizationServiceImpl(private val matrixOpenIdClient: MatrixOpenIdClient) :
    MatrixAuthorizationService {

    override suspend fun authorize(headers: Headers): Either<MatrixAuthorizationError, String> = either {
        val mxid = ensureNotNull(headers["mxid"]) {
            MatrixAuthorizationError.MissingMxidHeader
        }

        val auth = ensureNotNull(headers["authorization"]) {
            MatrixAuthorizationError.MissingAuthorizationHeader
        }

        val (token) = ensureNotNull(Regex("^Bearer (.+)").find(auth)) {
            MatrixAuthorizationError.MalformedBearerToken(auth)
        }.destructured

        val authenticatedMxid = when (val authenticationResult = matrixOpenIdClient.authenticatedUser(token)) {
            is UserAuthenticationResult.Success -> authenticationResult.mxid
            is UserAuthenticationResult.Failure -> raise(MatrixAuthorizationError.AuthenticationFailed(token))
        }

        ensure(mxid == authenticatedMxid) {
            MatrixAuthorizationError.MxidsDoNotMatch(givenMxid = mxid, authenticatedMxid = authenticatedMxid)
        }

        mxid
    }

    override suspend fun authorizeWithoutMxid(headers: Headers): Either<MatrixAuthorizationError, Unit> = either {
        val auth = ensureNotNull(headers["authorization"]) {
            MatrixAuthorizationError.MissingAuthorizationHeader
        }

        val (token) = ensureNotNull(Regex("^Bearer (.+)").find(auth)) {
            MatrixAuthorizationError.MalformedBearerToken(auth)
        }.destructured

        when (matrixOpenIdClient.authenticatedUser(token)) {
            is UserAuthenticationResult.Success -> Unit
            is UserAuthenticationResult.Failure -> raise(MatrixAuthorizationError.AuthenticationFailed(token))
        }
    }
}
