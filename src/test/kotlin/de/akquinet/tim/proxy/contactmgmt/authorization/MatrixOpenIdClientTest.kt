/*
 * Copyright © 2023 - 2024 akquinet GmbH (https://www.akquinet.de)
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

import de.akquinet.tim.proxy.ProxyConfiguration
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlin.time.Duration.Companion.hours

class MatrixOpenIdClientTest : ShouldSpec({
    should("get authenticatedUser") {
        val matrixAuthClient = matrixOpenIdClient("@mxid:server.example.com")
        matrixAuthClient.authenticatedUser("TOKEN") shouldBe "@mxid:server.example.com"
    }
    should("get no mxid when user is not authenticated") {
        val matrixAuthClient = matrixOpenIdClient("", statusCode = HttpStatusCode.Forbidden)
        matrixAuthClient.authenticatedUser("TOKEN") shouldBe null
    }
    should("should use token") {
        val matrixAuthClient =
            matrixOpenIdClient(verifyUrl = {
                it.fullPath shouldEndWith "/_matrix/federation/v1/openid/userinfo?access_token=ACCESS_TOKEN"
                it.hostWithPort shouldBe "localhost:7070"
            })
        matrixAuthClient.authenticatedUser("ACCESS_TOKEN") shouldBe ""
    }
})

private fun matrixOpenIdClient(
    authenticatedUser: String = "",
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    verifyUrl: (Url) -> Unit = {}
): MatrixOpenIdClient {
    val mockClient = HttpClient(MockEngine { request ->
        verifyUrl(request.url)
        respond(
            content = ByteReadChannel("""{"sub": "$authenticatedUser"}"""),
            status = statusCode,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }) {
        install(ContentNegotiation) {
            json()
        }
    }
    return MatrixOpenIdClient(
        mockClient, aProxyConfig("http://localhost:7070")
    )
}

fun aProxyConfig(homeserver: String) = ProxyConfiguration.InboundProxyConfiguration(
    homeserverUrl = homeserver,
    synapseHealthEndpoint = "/health",
    synapsePort = 443,
    port = 8070,
    enforceDomainList = true,
    accessTokenToUserIdCacheDuration = 1.hours
)
