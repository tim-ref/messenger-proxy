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
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.pipeline.*
import kotlin.test.Test

class AccountDataIT {

    @Test
    fun `should forward account data requests to homeserver without responding with default permissions`() =
        testApplication {
            proxyWith(InviteRejectionPolicy.BLOCK_ALL)
            homeserverWithAccountSetting("clientbackground") {
                call.respondText("""{ "origin": "homeserver" }""", contentType = Application.Json)
            }

            val response =
                client.get("/_matrix/client/v3/user/@test:synapse/account_data/clientbackground") {
                    accept(Application.Json)
                }

            response.bodyAsText() shouldEqualJson """{ "origin": "homeserver" }"""
        }

    @Test
    fun `should forward permissionconfig requests to homeserver`() = testApplication {
        proxyWith(InviteRejectionPolicy.BLOCK_ALL)
        homeserverWithAccountSetting("de.gematik.tim.account.permissionconfig.v1") {
            call.respondText("""{ "origin": "homeserver" }""", contentType = Application.Json)
        }

        val response =
            client.get("/_matrix/client/v3/user/@test:synapse/account_data/de.gematik.tim.account.permissionconfig.v1") {
                accept(Application.Json)
            }

        response.bodyAsText() shouldEqualJson """{ "origin": "homeserver" }"""
    }

    @Test
    fun `should forward permissionconfig_pro requests to homeserver`() = testApplication {
        proxyWith(InviteRejectionPolicy.BLOCK_ALL)
        homeserverWithAccountSetting("de.gematik.tim.account.permissionconfig.pro.v1") {
            call.respondText("""{ "origin": "homeserver" }""", contentType = Application.Json)
        }

        val response =
            client.get("/_matrix/client/v3/user/@test:synapse/account_data/de.gematik.tim.account.permissionconfig.pro.v1") {
                accept(Application.Json)
            }

        response.bodyAsText() shouldEqualJson """{ "origin": "homeserver" }"""
    }

    @Test
    fun `should replace empty permissionconfig with default (allow all)`() = testApplication {
        proxyWith(InviteRejectionPolicy.ALLOW_ALL)
        homeserverWithAccountSetting("de.gematik.tim.account.permissionconfig.pro.v1") {
            call.respondText("", status = NotFound)
        }

        val response =
            client.get("/_matrix/client/v3/user/@test:synapse/account_data/de.gematik.tim.account.permissionconfig.v1") {
                accept(Application.Json)
            }

        assertSoftly {
            response shouldHaveStatus OK
            response.bodyAsText() shouldEqualJson """{ "defaultSetting": "allow all" }"""
        }
    }

    @Test
    fun `should replace empty permissionconfig_pro with default (allow all)`() = testApplication {
        proxyWith(InviteRejectionPolicy.ALLOW_ALL)
        homeserverWithAccountSetting("de.gematik.tim.account.permissionconfig.pro.v1") {
            call.respondText("", status = NotFound)
        }

        val response =
            client.get("/_matrix/client/v3/user/@test:synapse/account_data/de.gematik.tim.account.permissionconfig.pro.v1") {
                accept(Application.Json)
            }

        assertSoftly {
            response shouldHaveStatus OK
            response.bodyAsText() shouldEqualJson """{ "defaultSetting": "allow all" }"""
        }
    }

    @Test
    fun `should replace not found permissionconfig with default (block all)`() = testApplication {
        proxyWith(InviteRejectionPolicy.BLOCK_ALL)
        homeserverWithAccountSetting("de.gematik.tim.account.permissionconfig.pro.v1") {
            call.respondText("", status = NotFound)
        }

        val response =
            client.get("/_matrix/client/v3/user/@test:synapse/account_data/de.gematik.tim.account.permissionconfig.v1") {
                accept(Application.Json)
            }

        assertSoftly {
            response shouldHaveStatus OK
            response.bodyAsText() shouldEqualJson """{ "defaultSetting": "block all" }"""
        }
    }

    @Test
    fun `should replace not found permissionconfig_pro with default (block all)`() = testApplication {
        proxyWith(InviteRejectionPolicy.BLOCK_ALL)
        homeserverWithAccountSetting("de.gematik.tim.account.permissionconfig.pro.v1") {
            call.respondText("", status = NotFound)
        }

        val response =
            client.get("/_matrix/client/v3/user/@test:synapse/account_data/de.gematik.tim.account.permissionconfig.pro.v1") {
                accept(Application.Json)
            }

        assertSoftly {
            response shouldHaveStatus OK
            response.bodyAsText() shouldEqualJson """{ "defaultSetting": "block all" }"""
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

    private fun ApplicationTestBuilder.homeserverWithAccountSetting(
        eventType: String,
        configuration: suspend PipelineContext<Unit, ApplicationCall>.() -> Unit
    ) {
        homeserverWithRouting {
            get("/_matrix/client/v3/user/@test:synapse/account_data/$eventType") {
                configuration()
            }
        }
    }

}
