/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addResourceOrFileSource
import com.sksamuel.hoplite.yaml.YamlParser
import de.akquinet.timref.proxy.actuator.ActuatorRoutes
import de.akquinet.timref.proxy.actuator.ActuatorRoutesImpl
import de.akquinet.timref.proxy.availability.RegistrationServiceHealthApi
import de.akquinet.timref.proxy.client.AccessTokenToUserId
import de.akquinet.timref.proxy.client.AccessTokenToUserIdAuthenticationFunction
import de.akquinet.timref.proxy.client.AccessTokenToUserIdAuthenticationFunctionImpl
import de.akquinet.timref.proxy.client.AccessTokenToUserIdImpl
import de.akquinet.timref.proxy.client.InboundClientRoutes
import de.akquinet.timref.proxy.client.InboundClientRoutesImpl
import de.akquinet.timref.proxy.contactmgmt.ContactManagementApi
import de.akquinet.timref.proxy.contactmgmt.ContactManagementApiImpl
import de.akquinet.timref.proxy.contactmgmt.ContactRoutes
import de.akquinet.timref.proxy.contactmgmt.ContactRoutesImpl
import de.akquinet.timref.proxy.contactmgmt.authorization.MatrixAuthorizationService
import de.akquinet.timref.proxy.contactmgmt.authorization.MatrixAuthorizationServiceImpl
import de.akquinet.timref.proxy.contactmgmt.authorization.MatrixOpenIdClient
import de.akquinet.timref.proxy.contactmgmt.database.ContactManagementService
import de.akquinet.timref.proxy.contactmgmt.database.ContactManagementServiceImpl
import de.akquinet.timref.proxy.contactmgmt.database.DatabaseFactory
import de.akquinet.timref.proxy.federation.FederationListCache
import de.akquinet.timref.proxy.federation.FederationListCacheImpl
import de.akquinet.timref.proxy.federation.InboundFederationRoutes
import de.akquinet.timref.proxy.federation.InboundFederationRoutesImpl
import de.akquinet.timref.proxy.federation.OutboundFederationRoutes
import de.akquinet.timref.proxy.federation.OutboundFederationRoutesImpl
import de.akquinet.timref.proxy.logging.LogLevelService
import de.akquinet.timref.proxy.rawdata.RawDataService
import de.akquinet.timref.proxy.rawdata.RawDataServiceImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import okio.FileSystem
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.security.Security

private val log = KotlinLogging.logger { }
suspend fun main(): Unit = coroutineScope {
    // BC providers required for certificates and TLS cipher suites using brainpool curves
    Security.insertProviderAt(BouncyCastleProvider(), 1)
    Security.insertProviderAt(BouncyCastleJsseProvider(), 1)

    val filePath = System.getenv("CONFIGURATION_FILE_PATH")
    val config = ConfigLoaderBuilder.default()
        .addParser("yml", YamlParser())
        .addResourceOrFileSource(filePath)
        .build()
        .loadConfigOrThrow<ProxyConfiguration>()

    val koin = koinApplication {

        modules(
            module {

                single { config }
                single { config.federationListCache }
                single { config.inboundProxy }
                single { config.outboundProxy }
                single { config.actuatorConfig }
                single { config.database }
                single { config.registrationServiceConfig }
                single { config.contactManagement }
                single { config.prometheusClient }
                single { config.logInfoConfig }
                single { config.logLevelResetConfig }
                single { FileSystem.SYSTEM }

                //flyway migrate
                DatabaseFactory.migrate(
                    config.database
                )
                //DB Connection
                DatabaseFactory.init(config.database)

                val generalHttpClient = HttpClient(Java) {
                    install(ContentNegotiation) {
                        json()
                    }
                    followRedirects = false
                }
                InterceptorInstaller(generalHttpClient).install()

                single { generalHttpClient }
                single { Java.create() } // default HttpClientEngine
                single { MatrixApiClient() }
                single { createMatrixEventJson() }
                single { createDefaultEventContentSerializerMappings() }

                singleOf(::MatrixOpenIdClient).bind()
                singleOf(::LogLevelService).bind()
                singleOf(::RegistrationServiceHealthApi).bind()
                singleOf(::ContactRoutesImpl).bind<ContactRoutes>()
                singleOf(::ContactManagementServiceImpl).bind<ContactManagementService>()
                singleOf(::MatrixAuthorizationServiceImpl).bind<MatrixAuthorizationService>()
                singleOf(::VZDPublicIDCheckImpl).bind<VZDPublicIDCheck>()
                singleOf(::FederationListCacheImpl).bind<FederationListCache>()
                singleOf(::InboundFederationRoutesImpl).bind<InboundFederationRoutes>()
                singleOf(::OutboundFederationRoutesImpl).bind<OutboundFederationRoutes>()
                singleOf(::OutboundProxyCertificateManagerImpl).bind<OutboundProxyCertificateManager>()
                singleOf(::AccessTokenToUserIdImpl).bind<AccessTokenToUserId>()
                singleOf(::AccessTokenToUserIdAuthenticationFunctionImpl).bind<AccessTokenToUserIdAuthenticationFunction>()
                singleOf(::InboundClientRoutesImpl).bind<InboundClientRoutes>()
                singleOf(::InboundProxyImpl).bind<InboundProxy>()
                singleOf(::OutboundProxyImpl).bind<OutboundProxy>()
                singleOf(::ActuatorRoutesImpl).bind<ActuatorRoutes>()
                singleOf(::RawDataServiceImpl).bind<RawDataService>()
                singleOf(::ContactManagementApiImpl).bind<ContactManagementApi>()
            })
    }.koin

    launch { koin.get<FederationListCacheImpl>().start() }
    launch { koin.get<InboundProxy>().start() }
    launch { koin.get<OutboundProxy>().start() }
    launch { koin.get<ActuatorRoutes>().start() }
    launch { koin.get<ContactManagementApiImpl>().start() }
}
