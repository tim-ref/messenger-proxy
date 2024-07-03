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

package de.akquinet.tim.proxy.integrationtests

import de.akquinet.tim.proxy.FileCacheMeta
import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.federation.FederationListCacheImpl
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class FileCacheIT {
    private val regServerConfig = ProxyConfiguration.RegistrationServiceConfiguration(
        baseUrl = "http://localhost",
        servicePort = "8070",
        healthPort = "8071",
        readinessEndpoint = "/actuator/health/readiness",
        federationListEndpoint = "/backend/federation",
        invitePermissionCheckEndpoint = "/backend/vzd/invite"
    )
    private val baseDirectory = "federationList"
    private val filePath  = "federationList/federationList.json"
    private val metaFilePath = "federationList/federationList-meta.json"
    private val fakeFileSystem = FakeFileSystem()
    private val federationListConfig = ProxyConfiguration.FederationListCacheConfiguration(baseDirectory, filePath, metaFilePath)
    private val responseString = """{
                                      "version": 0,
                                      "domainList": [
                                        {
                                          "domain": "451251f755f5e045cca963229cb0f3c5fa01af815cda7f5de44cb0251c80a116",
                                          "telematikID": "ID-akq",
                                          "isInsurance": false
                                        },
                                        {
                                          "domain": "cd3cb8cc6f016760f94c1466f408d3a727e5f77d2cb9015c3bf2ac636c6641c9",
                                          "telematikID": "ID-gem",
                                          "isInsurance": false
                                        }
                                      ]
                                    }"""

    private val httpClient = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                if (request.url.encodedPath == "/backend/federation") {
                    respond(responseString, HttpStatusCode.OK)
                } else {
                    error("Unhandled ${request.url.encodedPath}")
                }
            }
        }
    }

    private lateinit var federationListCacheDeferred: Deferred<Unit>

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        federationListCacheDeferred = async { startFileCache() }

        // cancel task after reasonable duration
        Timer().schedule(3000) {
            federationListCacheDeferred.cancel()
        }
    }
    private suspend fun startFileCache() {
            FederationListCacheImpl(
                federationListConfig, regServerConfig, httpClient, fakeFileSystem
            ).start()
    }

    @AfterTest
    fun afterEach() {
        fakeFileSystem.checkNoOpenFiles()
        fakeFileSystem.deleteRecursively(baseDirectory.toPath())
        httpClient.close()
    }

    @Test
    fun shouldReadFederationListFromFile() = runTest {
        fakeFileSystem.read(filePath.toPath()) {
            readUtf8() shouldBe responseString
        }

        fakeFileSystem.read(metaFilePath.toPath()) {
            Json.decodeFromString<FileCacheMeta>(readUtf8()).shouldBeTypeOf<FileCacheMeta>()
        }
    }
}
