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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import de.akquinet.tim.proxy.client.AccessTokenToUserIdImpl
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.core.model.UserId
import kotlin.time.Duration.Companion.hours

class AccessTokenToUserIdImplTest : ShouldSpec({
    lateinit var cut: AccessTokenToUserIdImpl
    var whoAmICalled = 0
    beforeTest {
        whoAmICalled = 0
        cut = AccessTokenToUserIdImpl(config = ProxyConfiguration.InboundProxyConfiguration(
            enforceDomainList = false,
            homeserverUrl = "http://localhost:8083",
            synapseHealthEndpoint = "/health",
            synapsePort = 443,
            port = 8090,
            accessTokenToUserIdCacheDuration = 1.hours
        ), matrixApiClient = MatrixApiClient {
            HttpClient(MockEngine {
                it.url.toString() shouldBe "http://localhost:8083/_matrix/client/v3/account/whoami"
                whoAmICalled++
                respond(
                    """{"user_id":"@user:server","device_id":"ABCDEF","is_guest":false}""",
                    headers = headersOf(
                        HttpHeaders.ContentType to
                                listOf(ContentType.Application.Json.withCharset(Charsets.UTF_8).toString())
                    )
                )
            }) { it() }
        })
    }
    should("call WhoAmI when not in cache") {
        cut("accessToken") shouldBe UserId("@user:server")
        whoAmICalled shouldBe 1
    }
    should("return value from cache") {
        cut("accessToken") shouldBe UserId("@user:server")
        cut("accessToken") shouldBe UserId("@user:server")
        whoAmICalled shouldBe 1
    }
})
