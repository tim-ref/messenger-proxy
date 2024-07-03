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

import de.akquinet.tim.proxy.*
import de.akquinet.tim.proxy.client.AccessTokenToUserIdAuthenticationFunctionImpl
import de.akquinet.tim.proxy.client.AccessTokenToUserIdImpl
import de.akquinet.tim.proxy.client.InboundClientRoutesImpl
import de.akquinet.tim.proxy.federation.InboundFederationRoutesImpl
import de.akquinet.tim.proxy.mocks.ContactManagementStub
import de.akquinet.tim.proxy.mocks.FederationListCacheMock
import de.akquinet.tim.proxy.mocks.RawDataServiceStub
import de.akquinet.tim.proxy.mocks.VZDPublicIDCheckMock
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.api.client.MatrixApiClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

class InboundProxyIT {

    private lateinit var federationListCacheMock: FederationListCacheMock

    private lateinit var rawDataServiceStub: RawDataServiceStub
    private val contactManagementServiceMock = ContactManagementStub()
    private val vzdPublicIDCheckMock = VZDPublicIDCheckMock()

    private val virtualHostname = ""
    private val externalMatrixHostname = ""
    private val matrixHttpsPort = 8090
    private val proxyInboundHostPort = 8090
    private val wellKnownEndpoint = "http://localhost:8090/.well-known/matrix/server"
    private val unknownEndpoint = "http://localhost:8090/_matrix/client/v3/asdf"
    private val pushRuleWithoutTemplateEndpoint = "http://localhost:8090/_matrix/client/v3/pushrules/global/not_a_template/foo"
    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json()
        }
        followRedirects = false

        install(Logging) {
            level = LogLevel.ALL
        }
    }
    val logInfoConfig = ProxyConfiguration.LogInfoConfig(
        "rawdata/path",
        "doctor",
        "2384234234",
        "MP-1",
        "home.de"
    )

    private lateinit var inboundApplicationEngine: ApplicationEngine

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        val inboundProxyConfig = ProxyConfiguration.InboundProxyConfiguration(
            homeserverUrl = "http://localhost:8083",
            port = proxyInboundHostPort,
            synapseHealthEndpoint = "/health",
            synapsePort = 443,
            enforceDomainList = true,
            accessTokenToUserIdCacheDuration = 1.hours
        )

        federationListCacheMock = FederationListCacheMock()
        rawDataServiceStub = RawDataServiceStub()
        // always trust server itself
        federationListCacheMock.domains.update { it + "$virtualHostname:$matrixHttpsPort" + "$externalMatrixHostname:$matrixHttpsPort" }

        inboundApplicationEngine =
            InboundProxyImpl(
                inboundProxyConfiguration = inboundProxyConfig,
                federationListCache = federationListCacheMock,
                accessTokenToUserIdAuthenticationFunction = AccessTokenToUserIdAuthenticationFunctionImpl(
                    AccessTokenToUserIdImpl(inboundProxyConfig, MatrixApiClient())
                ),
                inboundClientRoutes = InboundClientRoutesImpl(inboundProxyConfig,logInfoConfig ,httpClient, rawDataServiceStub),
                inboundFederationRoutes = InboundFederationRoutesImpl(inboundProxyConfig, httpClient, rawDataServiceStub, contactManagementServiceMock, vzdPublicIDCheckMock                ),
                httpClient = httpClient
            ).start()

    }

    @AfterTest
    fun afterEach() {
        inboundApplicationEngine.stop()
        httpClient.close()
    }

    @Test
    fun shouldReturnWellknownHostnameFromRequest() = runTest {
        val response = httpClient.get(wellKnownEndpoint)

        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldBe "{\"m.server\":\"localhost:443\"}"
    }

    // These tests verify the changes made in the CustomMatrixServer.kt in comparison to the orginal MatrixApiServer.kt
    @Test
    fun shouldReturnNotFoundOnNotExistentRoute() = runTest {
        val response = httpClient.get(unknownEndpoint)

        response.status shouldBe HttpStatusCode.NotFound
        response.bodyAsText() shouldBe "{\"errcode\":\"M_NOT_FOUND\"}"
    }

    @Test
    fun shouldReturnBadRequestOnBadRequestException() = runTest {
        val response = httpClient.put(pushRuleWithoutTemplateEndpoint)

        response.status shouldBe HttpStatusCode.BadRequest
        // the error message in the real application environment is "Can't transform call to resource"
        response.bodyAsText() shouldBe "{\"errcode\":\"M_UNKNOWN\",\"error\":\"net.folivo.trixnity.core.model.push.PushRuleKind does not contain element with name 'not_a_template'\"}"
    }
}
