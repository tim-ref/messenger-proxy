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
package de.akquinet.tim.proxy

import kotlin.time.Duration

data class ProxyConfiguration(
    val federationListCache: FederationListCacheConfiguration,
    val inboundProxy: InboundProxyConfiguration,
    val outboundProxy: OutboundProxyConfiguration,
    val actuatorConfig: ActuatorConfiguration,
    val contactManagement: ContactManagementConfig,
    val database: DatabaseConfig,
    val logInfoConfig: LogInfoConfig,
    val prometheusClient: PrometheusClient,
    val registrationServiceConfig: RegistrationServiceConfiguration,
    val logLevelResetConfig: LogLevelResetConfiguration
) {
    data class FederationListCacheConfiguration(
        val baseDirectory: String,
        val file: String,
        val metaFile: String,
    )

    data class InboundProxyConfiguration(
        val enforceDomainList: Boolean,
        val homeserverUrl: String,
        val synapseHealthEndpoint: String,
        val synapsePort: Int,
        val port: Int,
        val accessTokenToUserIdCacheDuration: Duration,
    )

    data class OutboundProxyConfiguration(
        val enforceDomainList: Boolean,
        val port: Int,
        val baseDirectory: String,
        val caCertificateFile: String,
        val caPrivateKeyFile: String,
        val domainWhiteList: String,
        val ssoDomain : String
    )

    data class ActuatorConfiguration(
        val port: Int,
        val basePath: String
    )

    data class ContactManagementConfig(
        val port: Int
    )

    data class DatabaseConfig(
        val jdbcUrl: String,
        val driver: String = "org.postgresql.Driver",
        val dbUser: String,
        val dbPassword: String
    )

    data class LogInfoConfig(
        val url: String,
        val professionId: String,
        val telematikId: String,
        val instanceId: String,
        val homeFQDN: String
    )

    data class PrometheusClient(
        val port: Int,
        val enableDefaultExports: String
    )

    data class RegistrationServiceConfiguration(
        val baseUrl: String,
        val servicePort: String,
        val healthPort: String,
        val federationListEndpoint: String,
        val invitePermissionCheckEndpoint: String,
        val readinessEndpoint: String
    )

    data class LogLevelResetConfiguration(
        val logLevelResetDelayInSeconds: Long,
        val resetLogLevel: String
    )
}
