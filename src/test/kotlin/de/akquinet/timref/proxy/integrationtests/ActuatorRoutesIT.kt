/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.integrationtests

import de.akquinet.timref.proxy.InboundProxyImpl
import de.akquinet.timref.proxy.OutboundProxyCertificateManagerImpl
import de.akquinet.timref.proxy.OutboundProxyImpl
import de.akquinet.timref.proxy.ProxyConfiguration
import de.akquinet.timref.proxy.actuator.ActuatorRoutesImpl
import de.akquinet.timref.proxy.availability.RegistrationServiceHealthApi
import de.akquinet.timref.proxy.client.AccessTokenToUserIdAuthenticationFunctionImpl
import de.akquinet.timref.proxy.client.AccessTokenToUserIdImpl
import de.akquinet.timref.proxy.client.InboundClientRoutesImpl
import de.akquinet.timref.proxy.federation.InboundFederationRoutesImpl
import de.akquinet.timref.proxy.federation.OutboundFederationRoutesImpl
import de.akquinet.timref.proxy.logging.LogLevelService
import de.akquinet.timref.proxy.mocks.ContactManagementStub
import de.akquinet.timref.proxy.mocks.FederationListCacheMock
import de.akquinet.timref.proxy.mocks.RawDataServiceStub
import de.akquinet.timref.proxy.mocks.VZDPublicIDCheckMock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.ApplicationEngine
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.api.client.MatrixApiClient
import okio.FileSystem
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.event.Level
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Duration.Companion.hours

class ActuatorRoutesIT {

    private lateinit var federationListCacheMock: FederationListCacheMock

    private lateinit var rawDataServiceStub: RawDataServiceStub
    private val contactManagementServiceMock = ContactManagementStub()
    private val vzdPublicIDCheckMock = VZDPublicIDCheckMock()

    private val virtualHostname = ""
    private val externalMatrixHostname = ""
    private val matrixHttpsPort = 8090
    private val proxyInboundHostPort = 8090
    private val proxyOutboundHostPort = 8558
    private val healthBaseUrl = "http://localhost:1233/actuator/health/"
    private val loggingBaseUrl = "http://localhost:1233/actuator/logging"
    private val httpClient = HttpClient(Java) {
        install(Logging) {
            level = LogLevel.ALL
        }
    }
    private val rawDataServiceUrl = "https://localhost:1234"
    private val rawDataPath = "/add-performance-data"

    private val regServerConfig = ProxyConfiguration.RegistrationServiceConfiguration(
        baseUrl = "http://localhost",
        servicePort = "8070",
        healthPort = "8071",
        readinessEndpoint = "/actuator/health/readiness",
        federationListEndpoint = "",
        invitePermissionCheckEndpoint = ""
    )

    private val logLevelResetConfiguration = ProxyConfiguration.LogLevelResetConfiguration(
        logLevelResetDelayInSeconds = 5,
        resetLogLevel = Level.INFO.name
    )

    private val regServiceHealthMockEngine = RegistrationServiceHealthApiMockEngine()
    private val regServiceHealthApi = RegistrationServiceHealthApi(regServiceHealthMockEngine.get(), regServerConfig)

    private lateinit var outboundApplicationEngine: ApplicationEngine
    private lateinit var inboundApplicationEngine: ApplicationEngine
    private lateinit var healthCheckApplicationEngine: ApplicationEngine

    private var inboundProxyConfig = ProxyConfiguration.InboundProxyConfiguration(
        homeserverUrl = "http://localhost",
        port = proxyInboundHostPort,
        synapseHealthEndpoint = "/health",
        synapsePort = 443,
        enforceDomainList = true,
        accessTokenToUserIdCacheDuration = 1.hours
    )

    private val embeddedPostgres: EmbeddedPostgres = EmbeddedPostgres.start()

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        val outboundProxyConfig = ProxyConfiguration.OutboundProxyConfiguration(
            port = proxyOutboundHostPort,
            baseDirectory = "certificates",
            caCertificateFile = "ca.crt",
            caPrivateKeyFile = "ca.key",
            enforceDomainList = true,
            domainWhiteList = "",
            ssoDomain = ""
        )
        val logInfoConfig = ProxyConfiguration.LogInfoConfig(
            "$rawDataServiceUrl$rawDataPath",
            "doctor",
            "2384234234",
            "MP-1",
            "home.de"
        )
        val actuatorConfiguration = ProxyConfiguration.ActuatorConfiguration(
            port = 1233,
            basePath = "/actuator"
        )

        Database.connect({ embeddedPostgres.postgresDatabase.connection })

        federationListCacheMock = FederationListCacheMock()
        rawDataServiceStub = RawDataServiceStub()
        // always trust server itself
        federationListCacheMock.domains.update { it + "$virtualHostname:$matrixHttpsPort" + "$externalMatrixHostname:$matrixHttpsPort" }

        val outboundProxyCertificateManager =
            OutboundProxyCertificateManagerImpl(outboundProxyConfig, FileSystem.RESOURCES)
        outboundApplicationEngine =
            OutboundProxyImpl(
                outboundProxyConfig,
                federationListCacheMock,
                OutboundFederationRoutesImpl(httpClient, rawDataServiceStub),
                outboundProxyCertificateManager, httpClient
            ).start()
        inboundApplicationEngine =
            InboundProxyImpl(
                inboundProxyConfiguration = inboundProxyConfig,
                federationListCache = federationListCacheMock,
                accessTokenToUserIdAuthenticationFunction = AccessTokenToUserIdAuthenticationFunctionImpl(
                    AccessTokenToUserIdImpl(inboundProxyConfig, MatrixApiClient())
                ),
                inboundClientRoutes = InboundClientRoutesImpl(
                    inboundProxyConfig,
                    logInfoConfig,
                    httpClient,
                    rawDataServiceStub
                ),
                inboundFederationRoutes = InboundFederationRoutesImpl(
                    inboundProxyConfig,
                    httpClient,
                    rawDataServiceStub,
                    contactManagementServiceMock,
                    vzdPublicIDCheckMock
                ),
                httpClient = httpClient
            ).start()

        healthCheckApplicationEngine =
            ActuatorRoutesImpl(
                actuatorConfig = actuatorConfiguration,
                inboundProxyConfig = inboundProxyConfig,
                outboundProxyConfig = outboundProxyConfig,
                logLevelService = LogLevelService(logLevelResetConfiguration),
                registrationServiceHealthApi = regServiceHealthApi,
                httpClient = httpClient
            ).start()
    }

    @AfterTest
    fun afterEach() {
        inboundApplicationEngine.stop()
        outboundApplicationEngine.stop()
        healthCheckApplicationEngine.stop()
        httpClient.close()
        embeddedPostgres.close()
    }

    @ParameterizedTest
    @ValueSource(strings = ["liveness", "readiness"])
    fun shouldReturnHealthCheckOk(endpoint: String) = runTest {
        httpClient.get(healthBaseUrl + endpoint).status shouldBe HttpStatusCode.OK
    }

    @Test
    fun canGetSupportedLogLevels() = runTest {
        val response = httpClient.get("$loggingBaseUrl/levels")
        response.status shouldBe HttpStatusCode.OK
        Level.entries.forEach { response.body<String>() shouldContain it.name }
    }

    @ParameterizedTest
    @ValueSource(strings = ["OFF", "TRACE", "DEBUG", "INFO", "WARN", "ERROR"])
    fun canChangeRootLogLevel(newLogLevel: String) = runTest {
        val response = httpClient.put("$loggingBaseUrl/$newLogLevel")
        response.status shouldBe HttpStatusCode.Accepted
    }

    @ParameterizedTest
    @ValueSource(strings = ["OFF", "TRACE", "DEBUG", "INFO", "WARN", "ERROR"])
    fun canChangeSpecificLogLevel(newLogLevel: String) = runTest {
        val response = httpClient.put("$loggingBaseUrl/$newLogLevel/some.logger.identifier")
        response.status shouldBe HttpStatusCode.Accepted
    }
}
