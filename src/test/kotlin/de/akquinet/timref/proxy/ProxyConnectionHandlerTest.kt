/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.network.tls.certificates.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import org.slf4j.event.Level
import java.io.File
import java.io.OutputStreamWriter
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.text.toCharArray

class ProxyConnectionHandlerTest : ShouldSpec({
    var testPort = 9000
    val externalUrl = "https://localhost:8090"
    val fakeFileSystem = FakeFileSystem()
    val config = ProxyConfiguration.OutboundProxyConfiguration(true, 8093, "certificates", "certificates/ca.crt", "certificates/key.pem", "", "")

    lateinit var httpClient: HttpClient
    lateinit var applicationEngine: ApplicationEngine

    val keyStoreFile = File("target/keystore.jks")
    val keyStore = generateCertificate(
        file = keyStoreFile,
        keyAlias = "ca",
        keyPassword = "password",
        algorithm = "SHA256withECDSA",
        keySizeInBits = 256,
        keyType = KeyType.CA,
    )

    beforeTest {
        testPort++
        fakeFileSystem.createDirectories(config.baseDirectory.toPath())
        fakeFileSystem.write(config.caCertificateFile.toPath()) {
            PemWriter(OutputStreamWriter(outputStream()))
                .apply {
                    writeObject(PemObject("X509 CERTIFICATE", keyStore.getCertificate("cacert").encoded))
                    flush()
                }
        }
        fakeFileSystem.write(config.caPrivateKeyFile.toPath()) {
            PemWriter(OutputStreamWriter(outputStream()))
                .apply {
                    writeObject(PemObject("PRIVATE KEY", keyStore.getKey("ca", "password".toCharArray()).encoded))
                    flush()
                }
        }
        val outboundProxyCertificateManager = OutboundProxyCertificateManagerImpl(config, fakeFileSystem)
        val env = applicationEngineEnvironment {
            connector {
                port = testPort
            }
            module {
                install(CallLogging) {
                    level = Level.TRACE
                }
                routing {
                    route("{...}") {
                        handle {
                            call.request.uri shouldBe "/hello"
                            call.request.headers[HttpHeaders.Host] shouldBe externalUrl.removePrefix("https://")
                            call.request.httpMethod shouldBe HttpMethod.Post
                            call.request.receiveChannel().toByteArray().decodeToString() shouldBe "world!"
                            call.respond("echo!")
                        }
                    }
                }
            }
        }
        applicationEngine = embeddedServer(Netty, env) {
            channelPipelineConfig = {
                addBefore("http1", "tunnel", ProxyConnectionHandler(outboundProxyCertificateManager))
            }
        }.start()
        httpClient = HttpClient(OkHttp) {
            install(Logging) {
                level = LogLevel.ALL
            }
            engine {
                proxy = ProxyBuilder.http("http://0.0.0.0:$testPort/")
                config {
                    sslSocketFactory(
                        SSLContext.getInstance("TLS")
                        .apply {
                            init(
                                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                                    .apply { init(keyStore, "password".toCharArray())
                                }.keyManagers,
                                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                                    .apply { init(keyStore) }.trustManagers,
                                SecureRandom()
                            )
                        }.socketFactory,
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                            .apply { init(keyStore) }.trustManagers[0] as X509TrustManager
                    )
                }
            }
        }
    }

    afterTest {
        applicationEngine.stop()
        fakeFileSystem.checkNoOpenFiles()
        fakeFileSystem.deleteRecursively(config.baseDirectory.toPath())
    }

    should("tunnel request") {
        assertSoftly(
            httpClient.post("$externalUrl/hello") {
                setBody("world!")
            }
        ) {
            status shouldBe HttpStatusCode.OK
            bodyAsText() shouldBe "echo!"
        }
    }
})
