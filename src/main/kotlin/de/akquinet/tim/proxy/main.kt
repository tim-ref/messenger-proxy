/*
 * Copyright © 2023 - 2026 akquinet GmbH (https://www.akquinet.de)
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

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addResourceOrFileSource
import com.sksamuel.hoplite.yaml.YamlParser
import de.akquinet.tim.proxy.actuator.ActuatorRoutes
import de.akquinet.tim.proxy.actuator.ActuatorRoutesImpl
import de.akquinet.tim.proxy.authorization.MatrixAuthorizationService
import de.akquinet.tim.proxy.authorization.MatrixAuthorizationServiceImpl
import de.akquinet.tim.proxy.authorization.MatrixOpenIdClient
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.client.AccessTokenToUserId
import de.akquinet.tim.proxy.client.AccessTokenToUserIdAuthenticationFunction
import de.akquinet.tim.proxy.client.AccessTokenToUserIdAuthenticationFunctionImpl
import de.akquinet.tim.proxy.client.AccessTokenToUserIdImpl
import de.akquinet.tim.proxy.client.InboundClientRoutes
import de.akquinet.tim.proxy.client.InboundClientRoutesImpl
import de.akquinet.tim.proxy.enforcer.RequestPolicyEnforcer
import de.akquinet.tim.proxy.federation.FederationListCache
import de.akquinet.tim.proxy.federation.FederationListCacheImpl
import de.akquinet.tim.proxy.federation.InboundFederationRoutes
import de.akquinet.tim.proxy.federation.InboundFederationRoutesImpl
import de.akquinet.tim.proxy.federation.OutboundFederationRoutes
import de.akquinet.tim.proxy.federation.OutboundFederationRoutesImpl
import de.akquinet.tim.proxy.logging.LogLevelService
import de.akquinet.tim.proxy.orphanedrooms.OrphanedRoomChecker
import de.akquinet.tim.proxy.orphanedrooms.OrphanedRoomCheckerImpl
import de.akquinet.tim.proxy.orphanedrooms.OrphanedRoomCleanupService
import de.akquinet.tim.proxy.orphanedrooms.OrphanedRoomCleanupServiceImpl
import de.akquinet.tim.proxy.rawdata.RawDataService
import de.akquinet.tim.proxy.rawdata.RawDataServiceImpl
import de.akquinet.tim.proxy.synapse.SynapseService
import de.akquinet.tim.proxy.synapse.client.SynapseClient
import de.akquinet.tim.proxy.tiMessengerInformation.TIMessengerInformationService
import de.akquinet.tim.proxy.tiMessengerInformation.TiMessengerInformationApi
import de.akquinet.tim.proxy.validation.RequestContentValidator
import de.akquinet.tim.proxy.validation.SynapseAdminAPIValidator
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.security.Security
import java.time.Clock
import java.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import okhttp3.Dispatcher
import okio.FileSystem
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module

private val klogger = KotlinLogging.logger {}

suspend fun main(): Unit = coroutineScope {
  // Suppress noisy BouncyCastle JSSE provider logs (uses java.util.logging)
  java.util.logging.Logger.getLogger("org.bouncycastle").level = java.util.logging.Level.WARNING

  // BC providers required for certificates and TLS cipher suites using brainpool curves
  Security.insertProviderAt(BouncyCastleProvider(), 1)
  Security.insertProviderAt(BouncyCastleJsseProvider(), 1)

  val filePath = System.getenv("CONFIGURATION_FILE_PATH")
  val config = loadProxyConfiguration(filePath)

  val koin = initiateKoin(config)

  launch { koin.get<FederationListCacheImpl>().start() }
  launch { koin.get<InboundProxy>().start() }
  launch { koin.get<OutboundProxy>().start() }
  launch { koin.get<ActuatorRoutes>().start() }
  launch { koin.get<TiMessengerInformationApi>().start() }
  launch { koin.get<OrphanedRoomCleanupServiceImpl>().start() }
}

private fun initiateKoin(config: ProxyConfiguration) =
  koinApplication {
      modules(
        module {
          single { config }
          single { config.federationListCache }
          single { config.inboundProxy }
          single { config.outboundProxy }
          single { config.actuatorConfig }
          single { config.registrationServiceConfig }
          single { config.prometheusClient }
          single { config.logInfoConfig }
          single { config.logLevelResetConfig }
          single { config.timAuthorizationCheckConfiguration }
          single { config.tiMessengerInformationConfiguration }
          single { config.httpClientConfig }
          single { config.synapse.adminApi }
          single { config.orphanedRoomCleanup }
          single { FileSystem.SYSTEM }

          val generalHttpClient = createGeneralHttpClient(config)
          InterceptorInstaller(generalHttpClient).install()

          single { generalHttpClient }
          single { OkHttp.create() } // default HttpClientEngine
          single { MatrixApiClient() }
          single { createMatrixEventJson() }
          single { createDefaultEventContentSerializerMappings() }
          single { Clock.systemUTC() }

          singleOf(::MatrixOpenIdClient).bind()
          singleOf(::LogLevelService).bind()
          singleOf(::MatrixAuthorizationServiceImpl).bind<MatrixAuthorizationService>()
          singleOf(::FederationListCacheImpl).bind<FederationListCache>()
          singleOf(::InboundFederationRoutesImpl).bind<InboundFederationRoutes>()
          singleOf(::OutboundFederationRoutesImpl).bind<OutboundFederationRoutes>()
          singleOf(::OutboundProxyCertificateManagerImpl).bind<OutboundProxyCertificateManager>()
          singleOf(::AccessTokenToUserIdImpl).bind<AccessTokenToUserId>()
          singleOf(::AccessTokenToUserIdAuthenticationFunctionImpl)
            .bind<AccessTokenToUserIdAuthenticationFunction>()
          singleOf(::InboundClientRoutesImpl).bind<InboundClientRoutes>()
          singleOf(::InboundProxyImpl).bind<InboundProxy>()
          singleOf(::OutboundProxyImpl).bind<OutboundProxy>()
          singleOf(::ActuatorRoutesImpl).bind<ActuatorRoutes>()
          singleOf(::RawDataServiceImpl).bind<RawDataService>()
          singleOf(::BerechtigungsstufeEinsService) { bind<BerechtigungsstufeEinsService>() }
          singleOf(::TiMessengerInformationApi) { bind<TiMessengerInformationApi>() }
          singleOf(::RequestContentValidator) { bind<RequestContentValidator>() }
          singleOf(::SynapseClient) { bind<SynapseClient>() }
          singleOf(::SynapseService) { bind<SynapseService>() }
          singleOf(::SynapseAdminAPIValidator) { bind<SynapseAdminAPIValidator>() }
          singleOf(::RequestPolicyEnforcer) { bind<RequestPolicyEnforcer>() }
          singleOf(::TIMessengerInformationService) { bind<TIMessengerInformationService>() }
          singleOf(::OrphanedRoomCheckerImpl).bind<OrphanedRoomChecker>()
          singleOf(::OrphanedRoomCleanupServiceImpl).bind<OrphanedRoomCleanupService>()
        }
      )
    }
    .koin

private fun createGeneralHttpClient(config: ProxyConfiguration): HttpClient {
  val generalHttpClient =
    HttpClient(OkHttp) {
      install(ContentNegotiation) { json() }
      followRedirects = false
      engine {
        config {
          connectTimeout(30.seconds.toJavaDuration())
          readTimeout(2.minutes.toJavaDuration())
          callTimeout(Duration.ZERO) // disables call timeout
          dispatcher(
            Dispatcher().apply {
              maxRequests = config.httpClientConfig.maxRequests
              maxRequestsPerHost = config.httpClientConfig.maxRequestsPerHost
            }
          )
        }
      }
      klogger.info {
        "Setup of module HttpClient finished. maxRequest=${config.httpClientConfig.maxRequests} maxRequestsPerHost=${config.httpClientConfig.maxRequestsPerHost}"
      }
    }
  return generalHttpClient
}

private fun loadProxyConfiguration(filePath: String) =
  ConfigLoaderBuilder.default()
    .addParser("yml", YamlParser())
    .addResourceOrFileSource(filePath)
    .build()
    .loadConfigOrThrow<ProxyConfiguration>()
