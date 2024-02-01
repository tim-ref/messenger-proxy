/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.engine.java.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.event.Level

class ForwardRequestTest : ShouldSpec({
    should("set host-header correctly") {
        val destinationServer = embeddedServer(Netty, applicationEngineEnvironment {
            connector {
                port = 3101
            }
            module {
                install(CallLogging) {
                    level = Level.TRACE
                }
                routing {
                    get("/") {
                        call.request.host() shouldBe "unicorn"
                        call.respond("ok")
                    }
                }
            }
        }).start()
        val proxy = embeddedServer(Netty, applicationEngineEnvironment {
            connector {
                port = 3100
            }
            module {
                install(CallLogging) {
                    level = Level.TRACE
                }
                routing {
                    get("/") {
                        call.request.host() shouldBe "unicorn"
                        forwardRedirect(call, call.request.uri.mergeToUrl("http://localhost:3101"), "unicorn")
                    }
                }
            }
        }).start()

        val response = HttpClient(Apache5).get("http://localhost:3100") {
            headers.append("Host", "unicorn")
        }
        assertSoftly(response) {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe "ok"
        }

        destinationServer.stop()
        proxy.stop()
    }
    should("set host-header should fail") {
        val destinationServer = embeddedServer(Netty, applicationEngineEnvironment {
            connector {
                port = 3101
            }
            module {
                install(CallLogging) {
                    level = Level.TRACE
                }
                routing {
                    get("/") {
                        call.request.host() shouldBe "unicorn"
                        call.respond("ok")
                    }
                }
            }
        }).start()
        val proxy = embeddedServer(Netty, applicationEngineEnvironment {
            connector {
                port = 3100
            }
            module {
                install(CallLogging) {
                    level = Level.TRACE
                }
                routing {
                    get("/") {
                        call.request.host() shouldNotBe "unicorn"
                        forwardRedirect(call, call.request.uri.mergeToUrl("http://localhost:3101"), "unicorn")
                    }
                }
            }
        }).start()

        val response = HttpClient(Java).get("http://localhost:3100") {
            headers.append("Host", "unicorn")
        }
        assertSoftly(response) {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe "ok"
        }

        destinationServer.stop()
        proxy.stop()
    }
})
