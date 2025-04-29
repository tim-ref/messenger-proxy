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

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readAvailable
import org.slf4j.event.Level
import kotlin.random.Random

class ForwardMediaRequestTest : ShouldSpec({

    val httpClient = HttpClient(OkHttp)
    val destinationServer = embeddedServer(
        Netty,
        applicationEngineEnvironment {
            connector {
                port = 3101
            }
            module {
                install(CallLogging) {
                    level = Level.TRACE
                }
                routing {
                    get("/media") {
                        call.respond(object : OutgoingContent.WriteChannelContent() {
                            override val contentType = ContentType.Application.OctetStream

                            override suspend fun writeTo(channel: ByteWriteChannel) {
                                val buffer = ByteArray(8192)
                                val totalSize = 120 * 1024 * 1024
                                var sentBytes = 0

                                while (sentBytes < totalSize) {
                                    Random.nextBytes(buffer)
                                    val chunkSize = minOf(buffer.size, totalSize - sentBytes)
                                    channel.writeFully(buffer, 0, chunkSize)
                                    sentBytes += chunkSize
                                }
                                channel.close()
                            }
                        })
                    }
                }
            }
        }
    )

    val proxy = embeddedServer(
        Netty,
        applicationEngineEnvironment {
            connector {
                port = 3100
            }
            module {
                install(CallLogging) {
                    level = Level.TRACE
                }
                routing {
                    get("/media") {
                        forwardMediaRequest(
                            call,
                            httpClient,
                            call.request.uri.mergeToUrl("http://localhost:3101/media"),
                        )
                    }
                }
            }
        }
    )

    beforeSpec {
        destinationServer.start()
        proxy.start()
    }

    afterSpec {
        destinationServer.stop()
        proxy.stop()
    }

    context("forwardMediaRequest") {
        should("consume less than 5 % of transmitted bytes of memory when transferring 120 MB media content") {
            val response = httpClient.get("http://localhost:3100/media")

            assertSoftly(response) {
                status shouldBe HttpStatusCode.OK
                headers["Content-Type"] shouldBe ContentType.Application.OctetStream.toString()
                headers["Transfer-Encoding"] shouldBe "chunked"

                val channel: ByteReadChannel = response.bodyAsChannel()

                System.gc()
                val beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                var numberOfChunks = 0

                val totalBytesRead: Long = run {
                    val buffer = ByteArray(8 * 1024) // Default buffer size
                    var totalRead = 0L
                    while (!channel.isClosedForRead) {
                        val readBytes = channel.readAvailable(buffer)
                        if(readBytes > 0) {
                            totalRead += readBytes
                            numberOfChunks++
                        }
                    }
                    totalRead
                }

                totalBytesRead shouldBe 120 * 1024 * 1024L // 120 MB
                numberOfChunks shouldBeGreaterThan 1

                val afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                val memoryUsed = afterMemory - beforeMemory

                memoryUsed.shouldBeGreaterThanOrEqual(0L)
                memoryUsed.shouldBeLessThan(totalBytesRead)
            }
        }
    }
})
