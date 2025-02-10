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

import de.akquinet.tim.proxy.defaultConfig
import de.akquinet.tim.proxy.homeserverWithRouting
import de.akquinet.tim.proxy.proxyWithClientServerRoutes
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test

class PresenceIT {

    @Test
    fun `should return forbidden on putting too long status message`() = testApplication {
        proxyWithClientServerRoutes(defaultConfig(httpClient = client))

        val response = client.put("/_matrix/client/v3/presence/@test:synapse/status")
        {
            accept(Json)
            setBody(
                """{
                    "presence": "online",
                    "status_msg": "thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage.thisIsALongStatusMessage."
                }"""
            )
        }

        response.status shouldBe Forbidden
        response.bodyAsText() shouldEqualJson
                """{
                    "errcode": "M_TOO_LARGE",
                    "error": "'status_msg' is longer than 250 characters."
                }"""
    }

    @Test
    fun `should forward request on including short status message`() = testApplication {
        proxyWithClientServerRoutes(defaultConfig(httpClient = client))
        homeserverWithRouting {
            put("/_matrix/client/v3/presence/@test:synapse/status") {
                call.respondText(
                    contentType = Json,
                    status = OK,
                    text = """{"origin":"homeserver"}"""
                )
            }
        }

        val response = client.put("/_matrix/client/v3/presence/@test:synapse/status") {
            accept(Json)
            setBody(
                """{
                    "presence": "online",
                    "status_msg": "thisIsAShortStatusMessage"
                }"""
            )
        }

        response.status shouldBe OK
        response.bodyAsText() shouldEqualJson
                """{ "origin": "homeserver" }"""
    }

    @Test
    fun `should forward request on not including status message`() = testApplication {
        proxyWithClientServerRoutes(defaultConfig(httpClient = client))
        homeserverWithRouting {
            put("/_matrix/client/v3/presence/@test:synapse/status") {
                call.respondText(
                    contentType = Json,
                    status = OK,
                    text = """{"origin":"homeserver"}"""
                )
            }
        }

        val response = client.put("/_matrix/client/v3/presence/@test:synapse/status") {
            accept(Json)
            setBody(
                """{
                    "presence": "online"
                }"""
            )
        }

        response.status shouldBe OK
        response.bodyAsText() shouldEqualJson
                """{ "origin":"homeserver" }"""
    }

}
