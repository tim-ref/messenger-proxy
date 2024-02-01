/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.integrationtests

import de.akquinet.timref.proxy.*
import de.akquinet.timref.proxy.client.AccessTokenToUserIdAuthenticationFunctionImpl
import de.akquinet.timref.proxy.client.AccessTokenToUserIdImpl
import de.akquinet.timref.proxy.client.InboundClientRoutesImpl
import de.akquinet.timref.proxy.federation.InboundFederationRoutesImpl
import de.akquinet.timref.proxy.mocks.ContactManagementStub
import de.akquinet.timref.proxy.mocks.FederationListCacheMock
import de.akquinet.timref.proxy.mocks.RawDataServiceStub
import de.akquinet.timref.proxy.mocks.VZDPublicIDCheckMock
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.api.client.MatrixApiClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

class InboundProxyWellknownIT {

    private lateinit var federationListCacheMock: FederationListCacheMock

    private lateinit var rawDataServiceStub: RawDataServiceStub
    private val contactManagementServiceMock = ContactManagementStub()
    private val vzdPublicIDCheckMock = VZDPublicIDCheckMock()

    private val virtualHostname = ""
    private val externalMatrixHostname = ""
    private val matrixHttpsPort = 8090
    private val proxyInboundHostPort = 8090
    private val endpointUrl = "http://localhost:8090/.well-known/matrix/server"
    private val httpClient = HttpClient(Java) {
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
                inboundFederationRoutes = InboundFederationRoutesImpl(                    inboundProxyConfig, httpClient, rawDataServiceStub, contactManagementServiceMock, vzdPublicIDCheckMock                ),
                httpClient = httpClient
            ).start()

    }

    @AfterTest
    fun afterEach() {
        inboundApplicationEngine.stop()
        httpClient.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun shouldReturnWellknownHostnameFromRequest() = runTest {
        val httpClient = HttpClient()

        val response = httpClient.get(endpointUrl)

        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldBe "{\"m.server\":\"localhost:443\"}"
        httpClient.close()
    }
}
