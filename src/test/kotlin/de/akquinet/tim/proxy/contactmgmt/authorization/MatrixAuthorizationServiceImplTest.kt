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
package de.akquinet.tim.proxy.contactmgmt.authorization

import de.akquinet.tim.proxy.authorization.MatrixAuthorizationError
import de.akquinet.tim.proxy.authorization.MatrixAuthorizationServiceImpl
import de.akquinet.tim.proxy.authorization.MatrixOpenIdClient
import de.akquinet.tim.proxy.authorization.UserAuthenticationResult
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.mockk

class MatrixAuthorizationServiceImplTest : FunSpec({
    val authClient: MatrixOpenIdClient = mockk()

    val matrixAuthorizationService = MatrixAuthorizationServiceImpl(authClient)

    context("authorize") {
        test("authorize should fail when mxid is missing") {
            val headers: Headers = headersOf(Pair("authorize", listOf("Bearer TOKENTOKENTOKEN")))
            matrixAuthorizationService.authorize(headers) shouldBeLeft MatrixAuthorizationError.MissingMxidHeader
        }

        test("authorize should fail when authorization header is missing") {
            val headers: Headers = headersOf(Pair("mxId", listOf("@mxid:server.example.com")))
            matrixAuthorizationService.authorize(headers) shouldBeLeft MatrixAuthorizationError.MissingAuthorizationHeader
        }

        test("authorize should fail when authorization header is malformed") {
            val headers: Headers =
                headersOf(Pair("mxId", listOf("@mxid:server.example.com")), Pair("authorization", listOf("none")))
            matrixAuthorizationService.authorize(headers) shouldBeLeft MatrixAuthorizationError.MalformedBearerToken("none")
        }

        test("authorize with actual authorization header should pass") {
            val headers: Headers = headersOf(
                Pair("mxId", listOf("@mxid:server.example.com")),
                Pair("authorization", listOf("Bearer TOKENTOKENTOKEN"))
            )

            coEvery { authClient.authenticatedUser(any()) } returns UserAuthenticationResult.Success(
                "TOKENTOKENTOKEN",
                "@mxid:server.example.com"
            )

            matrixAuthorizationService.authorize(headers) shouldBeRight "@mxid:server.example.com"
        }

        test("authorize should fail when mxid does not match") {
            val headers: Headers = headersOf(
                Pair("mxId", listOf("@other:server.example.com")),
                Pair("authorization", listOf("Bearer TOKENTOKENTOKEN"))
            )

            coEvery { authClient.authenticatedUser(any()) } returns UserAuthenticationResult.Success(
                "TOKENTOKENTOKEN",
                "@mxid:server.example.com"
            )

            matrixAuthorizationService.authorize(headers) shouldBeLeft MatrixAuthorizationError.MxidsDoNotMatch(
                "@other:server.example.com",
                "@mxid:server.example.com"
            )
        }

        test("authorize should fail when openid auth fails") {
            val headers: Headers = headersOf(
                Pair("mxId", listOf("@mxid:server.example.com")),
                Pair("authorization", listOf("Bearer TOKENTOKENTOKEN"))
            )

            coEvery { authClient.authenticatedUser(any()) } returns UserAuthenticationResult.Failure("TOKENTOKENTOKEN")

            matrixAuthorizationService.authorize(headers) shouldBeLeft MatrixAuthorizationError.AuthenticationFailed("TOKENTOKENTOKEN")
        }
    }

    context("authorizeWithoutMxid") {
        test("authorizeWithoutMxid should fail when authorization header is missing") {
            // Even if a mxid is provided, the method only cares about the authorization header.
            val headers = headersOf("mxid", listOf("@mxid:server.example.com"))
            matrixAuthorizationService.authorizeWithoutMxid(headers) shouldBeLeft MatrixAuthorizationError.MissingAuthorizationHeader
        }

        test("authorizeWithoutMxid should fail when the authorization header does not match the expected format") {
            val headers = headersOf("authorization", listOf("none"))
            matrixAuthorizationService.authorizeWithoutMxid(headers) shouldBeLeft MatrixAuthorizationError.MalformedBearerToken(
                "none"
            )
        }

        test("authorizeWithoutMxid should succeed with a valid authorization header and no mxid header") {
            val headers = headersOf("authorization", listOf("Bearer TOKENTOKENTOKEN"))

            coEvery { authClient.authenticatedUser(any()) } returns UserAuthenticationResult.Success(
                "TOKENTOKENTOKEN",
                "@mxid:server.example.com"
            )

            matrixAuthorizationService.authorizeWithoutMxid(headers) shouldBeRight Unit
        }

        test("authorizeWithoutMxid should succeed with a valid authorization header even when an extraneous mxid header is present") {
            val headers = headersOf(
                Pair("mxId", listOf("@mxid:server.example.com")),
                Pair("authorization", listOf("Bearer TOKENTOKENTOKEN"))
            )

            coEvery { authClient.authenticatedUser(any()) } returns UserAuthenticationResult.Success(
                "TOKENTOKENTOKEN",
                "@mxid:server.example.com"
            )

            matrixAuthorizationService.authorizeWithoutMxid(headers) shouldBeRight Unit
        }

        test("authorizeWithoutMxid should fail when openid auth fails") {
            val headers = headersOf("authorization", listOf("Bearer TOKENTOKENTOKEN"))

            coEvery { authClient.authenticatedUser(any()) } returns UserAuthenticationResult.Failure("TOKENTOKENTOKEN")

            matrixAuthorizationService.authorizeWithoutMxid(headers) shouldBeLeft MatrixAuthorizationError.AuthenticationFailed(
                "TOKENTOKENTOKEN"
            )
        }
    }
})
