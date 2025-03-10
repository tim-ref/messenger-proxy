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
import de.akquinet.tim.proxy.actuator.ActuatorRoutesImpl
import de.akquinet.tim.proxy.availability.RegistrationServiceHealthApi
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.client.AccessTokenToUserIdAuthenticationFunctionImpl
import de.akquinet.tim.proxy.client.AccessTokenToUserIdImpl
import de.akquinet.tim.proxy.client.InboundClientRoutesImpl
import de.akquinet.tim.proxy.federation.FederationList
import de.akquinet.tim.proxy.federation.InboundFederationRoutesImpl
import de.akquinet.tim.proxy.federation.OutboundFederationRoutesImpl
import de.akquinet.tim.proxy.logging.LogLevelService
import de.akquinet.tim.proxy.mocks.ContactManagementStub
import de.akquinet.tim.proxy.mocks.FederationListCacheMock
import de.akquinet.tim.proxy.mocks.RawDataServiceStub
import de.akquinet.tim.proxy.mocks.VZDPublicIDCheckMock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.engine.*
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
    private val httpClient = HttpClient(OkHttp) {
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
        invitePermissionCheckEndpoint = "",
        wellKnownSupportEndpoint = "/backend/well-known-support"
    )

    private val logLevelResetConfiguration = ProxyConfiguration.LogLevelResetConfiguration(
        logLevelResetDelayInSeconds = 5,
        resetLogLevel = Level.INFO.name
    )

    private val regServiceHealthMockEngine = RegistrationServiceHealthApiMockEngine()
    private val regServiceHealthApi = RegistrationServiceHealthApi(regServiceHealthMockEngine.get(), regServerConfig)

    private lateinit var bsEinsService: BerechtigungsstufeEinsService
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
        val timAuthorizationCheckConfiguration = ProxyConfiguration.TimAuthorizationCheckConfiguration(
            concept = TimAuthorizationCheckConcept.CLIENT,
            inviteRejectionPolicy = InviteRejectionPolicy.ALLOW_ALL
        )

        Database.connect({ embeddedPostgres.postgresDatabase.connection })

        federationListCacheMock = FederationListCacheMock()
        bsEinsService = BerechtigungsstufeEinsService(federationListCacheMock)
        rawDataServiceStub = RawDataServiceStub()
        // always trust server itself
        federationListCacheMock.domains.update {
            it +
                    FederationList.FederationDomain(
                        domain = "$virtualHostname:$matrixHttpsPort$externalMatrixHostname:$matrixHttpsPort",
                        isInsurance = true,
                        telematikID = "telematik"
                    )
        }

        val outboundProxyCertificateManager =
            OutboundProxyCertificateManagerImpl(outboundProxyConfig, FileSystem.RESOURCES)
        outboundApplicationEngine =
            OutboundProxyImpl(
                outboundProxyConfig,
                bsEinsService,
                OutboundFederationRoutesImpl(httpClient, rawDataServiceStub),
                outboundProxyCertificateManager, httpClient
            ).start()
        inboundApplicationEngine =
            InboundProxyImpl(
                inboundProxyConfiguration = inboundProxyConfig,
                accessTokenToUserIdAuthenticationFunction = AccessTokenToUserIdAuthenticationFunctionImpl(
                    AccessTokenToUserIdImpl(inboundProxyConfig, MatrixApiClient())
                ),
                inboundClientRoutes = InboundClientRoutesImpl(
                    config = inboundProxyConfig,
                    logConfiguration = logInfoConfig,
                    timAuthorizationCheckConfiguration = timAuthorizationCheckConfiguration,
                    httpClient = httpClient,
                    berechtigungsstufeEinsService = bsEinsService,
                    rawDataService = rawDataServiceStub,
                    regServiceConfig = regServerConfig
                ),
                inboundFederationRoutes = InboundFederationRoutesImpl(
                    inboundProxyConfig,
                    httpClient,
                    rawDataServiceStub,
                    contactManagementServiceMock,
                    vzdPublicIDCheckMock,
                    timAuthorizationCheckConfiguration
                ),
                httpClient = httpClient,
                berechtigungsstufeEinsService = bsEinsService
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

    @OptIn(ExperimentalStdlibApi::class)
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
