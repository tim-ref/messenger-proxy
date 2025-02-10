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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.mockk

class MatrixAuthorizationServiceImplTest : ShouldSpec({
    val authClient: MatrixOpenIdClient = mockk()

    val matrixAuthorizationService = MatrixAuthorizationServiceImpl(authClient)
    should("authorize should fail when mxid is missing") {
        val headers: Headers = headersOf(Pair("auhtorize", listOf("Bearer TOKENTOKENTOKEN")))
        matrixAuthorizationService.authorize(headers) shouldBe false
    }
    should("authorize should fail when authorization header is missing") {
        val headers: Headers = headersOf(Pair("mxId", listOf("@mxid:server.example.com")))
        matrixAuthorizationService.authorize(headers) shouldBe false
    }
    should("authorize should fail when authorization header is wrong") {
        val headers: Headers =
            headersOf(Pair("mxId", listOf("@mxid:server.example.com")), Pair("authorization", listOf("none")))
        matrixAuthorizationService.authorize(headers) shouldBe false
    }
    should("authorize with actual authorization header should pass") {
        val headers: Headers =
            headersOf(
                Pair("mxId", listOf("@mxid:server.example.com")),
                Pair("authorization", listOf("Bearer TOKENTOKENTOKEN"))
            )
        coEvery { authClient.authenticatedUser(any()) } returns "@mxid:server.example.com"
        matrixAuthorizationService.authorize(headers) shouldBe true
    }
    should("authorize should fail when mxid does not match") {
        val headers: Headers =
            headersOf(
                Pair("mxId", listOf("@other:server.example.com")),
                Pair("authorization", listOf("Bearer TOKENTOKENTOKEN"))
            )
        coEvery { authClient.authenticatedUser(any()) } returns "@mxid:server.example.com"
        matrixAuthorizationService.authorize(headers) shouldBe false
    }
    should("authorize should fail when openid auth fails") {
        val headers: Headers =
            headersOf(
                Pair("mxId", listOf("@mxid:server.example.com")),
                Pair("authorization", listOf("Bearer TOKENTOKENTOKEN"))
            )
        coEvery { authClient.authenticatedUser(any()) } returns null
        matrixAuthorizationService.authorize(headers) shouldBe false
    }
})
