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
package de.akquinet.tim.proxy.integrationtests

import de.akquinet.tim.proxy.*
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import net.folivo.trixnity.core.ErrorResponse
import kotlin.test.Test

class AccountDataIT {

    @Test
    fun `should forward non-permissionconfig requests to homeserver without responding with default permissions`() =
        testApplication {
            proxyWith(InviteRejectionPolicy.BLOCK_ALL)
            homeserverWithRouting {
                get("/_matrix/client/v3/user/@test:synapse/account_data/clientbackground") {
                    call.respondText(
                        contentType = Json,
                        status = OK,
                        text = ""
                    )
                }
            }

            val response =
                client.get("/_matrix/client/v3/user/@test:synapse/account_data/clientbackground") {
                    accept(Json)
                }

            assertSoftly(response) {
                status shouldBe OK
                bodyAsText().shouldBeEmpty()
            }
        }

    @Test
    fun `should forward account data requests to homeserver`() = testApplication {
        proxyWith(InviteRejectionPolicy.BLOCK_ALL)
        homeserverWithRouting {
            get("/_matrix/client/v3/user/@test:synapse/account_data/de.gematik.tim.account.permissionconfig.v1") {
                call.respondText(
                    contentType = Json,
                    status = OK,
                    text = """{"defaultSetting":"allow all"}"""
                )
            }
        }

        val response =
            client.get("/_matrix/client/v3/user/@test:synapse/account_data/de.gematik.tim.account.permissionconfig.v1") {
                accept(Json)
            }

        assertSoftly(response) {
            status shouldBe OK
            bodyAsText() shouldEqualJson """{ "defaultSetting": "allow all" }"""
        }
    }

    @Test
    fun `should replace empty permissionconfig with default (allow all)`() = testApplication {
        proxyWith(InviteRejectionPolicy.ALLOW_ALL)
        homeserverWithRouting {
            get("/_matrix/client/v3/user/@test:synapse/account_data/de.gematik.tim.account.permissionconfig.v1") {
                call.respondText(
                    contentType = Json,
                    status = NotFound,
                    text = ""
                )
            }
        }

        val response =
            client.get("/_matrix/client/v3/user/@test:synapse/account_data/de.gematik.tim.account.permissionconfig.v1") {
                accept(Json)
            }

        assertSoftly(response) {
            status shouldBe OK
            bodyAsText() shouldEqualJson
                    """{
                        "defaultSetting": "allow all"
                    }"""
        }
    }

    @Test
    fun `should replace not found permissionconfig with default (block all)`() = testApplication {
        proxyWith(InviteRejectionPolicy.BLOCK_ALL)
        homeserverWithRouting {
            get("/_matrix/client/v3/user/@test:synapse/account_data/de.gematik.tim.account.permissionconfig.v1") {
                call.respondText(
                    contentType = Json,
                    status = NotFound,
                    text = ""
                )
            }
        }

        val response =
            client.get("/_matrix/client/v3/user/@test:synapse/account_data/de.gematik.tim.account.permissionconfig.v1") {
                accept(Json)
            }

        assertSoftly(response) {
            status shouldBe OK
            bodyAsText() shouldEqualJson
                    """{
                        "defaultSetting": "block all"
                    }"""
        }
    }

    private fun ApplicationTestBuilder.proxyWith(inviteRejectionPolicy: InviteRejectionPolicy) {
        proxyWithClientServerRoutes(
            defaultConfig(
                httpClient = client,
                timAuthorizationCheckConfiguration = ProxyConfiguration.TimAuthorizationCheckConfiguration(
                    concept = TimAuthorizationCheckConcept.CLIENT,
                    inviteRejectionPolicy = inviteRejectionPolicy
                )
            )
        )
    }

}
