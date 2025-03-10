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

import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.client.InboundClientRoutes
import de.akquinet.tim.proxy.client.InboundClientRoutesImpl
import de.akquinet.tim.proxy.federation.InboundFederationRoutes
import de.akquinet.tim.proxy.federation.OutboundFederationRoutes
import de.akquinet.tim.proxy.integrationtests.homeserver
import de.akquinet.tim.proxy.mocks.FederationListCacheMock
import de.akquinet.tim.proxy.mocks.RawDataServiceStub
import de.akquinet.tim.proxy.rawdata.RawDataService
import de.akquinet.tim.proxy.util.installCustomMatrixApiServer
import io.ktor.client.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.hours

/**
 * Adds a module containing the proxy's Matrix client-server API routes to TestApplication.
 */
fun ApplicationTestBuilder.proxyWithClientServerRoutes(inboundClientRoutes: InboundClientRoutes) {
    application {
        installCustomMatrixApiServer(Json)
        routing {
            with(inboundClientRoutes) { clientServerApiRoutes() }
        }
    }
}

/**
 * Adds a module containing the proxy's Matrix server-server API routes to TestApplication.
 */
fun ApplicationTestBuilder.inboundProxyWithServerServerRoutes(inboundFederationRoutes: InboundFederationRoutes) {
    application {
        installCustomMatrixApiServer(Json)
        routing {
            with(inboundFederationRoutes) { serverServerApiRoutes() }
        }
    }
}

/**
 * Adds a module containing the proxy's Matrix server-server API routes to TestApplication.
 */
fun ApplicationTestBuilder.outboundProxyWithServerServerRoutes(outboundFederationRoutes: OutboundFederationRoutes) {
    application {
        installCustomMatrixApiServer(Json)
        routing {
            with(outboundFederationRoutes) { serverServerApiRoutes() }
        }
    }
}

/**
 * Generically configured InboundClientRoutes.
 */
fun defaultConfig(
    config: ProxyConfiguration.InboundProxyConfiguration = ProxyConfiguration.InboundProxyConfiguration(
        homeserverUrl = "http://localhost:8083",
        port = 8090,
        synapseHealthEndpoint = "/health",
        synapsePort = 443,
        enforceDomainList = true,
        accessTokenToUserIdCacheDuration = 1.hours
    ),
    logConfiguration: ProxyConfiguration.LogInfoConfig = ProxyConfiguration.LogInfoConfig(
        url = "rawdata/path",
        professionId = "doctor",
        telematikId = "2384234234",
        instanceId = "MP-1",
        homeFQDN = "home.de"
    ),
    timAuthorizationCheckConfiguration: ProxyConfiguration.TimAuthorizationCheckConfiguration = ProxyConfiguration.TimAuthorizationCheckConfiguration(
        concept = TimAuthorizationCheckConcept.CLIENT,
        inviteRejectionPolicy = InviteRejectionPolicy.ALLOW_ALL
    ),
    httpClient: HttpClient,
    rawDataService: RawDataService = RawDataServiceStub(),
    berechtigungsstufeEinsService: BerechtigungsstufeEinsService = BerechtigungsstufeEinsService(
        FederationListCacheMock()
    ),
    regServiceConfig: ProxyConfiguration.RegistrationServiceConfiguration =
        ProxyConfiguration.RegistrationServiceConfiguration(
            baseUrl = "https://reg-service",
            servicePort = "8080",
            healthPort = "8081",
            federationListEndpoint = "/backend/federation",
            invitePermissionCheckEndpoint = "/backend/vzd/invite",
            readinessEndpoint = "/actuator/health/readiness",
            wellKnownSupportEndpoint = "/backend/well-known-support"
        )
) = InboundClientRoutesImpl(
    config,
    logConfiguration,
    timAuthorizationCheckConfiguration,
    httpClient,
    rawDataService,
    berechtigungsstufeEinsService,
    regServiceConfig
)

/**
 * Builds mock Matrix homeserver, installs a Routing plugin for this
 * Application and runs a configuration script on it.
 */
fun ApplicationTestBuilder.homeserverWithRouting(configuration: Routing.() -> Unit) {
    externalServices {
        hosts(homeserver) {
            install(ContentNegotiation) { json() }
            routing { configuration() }
        }
    }
}
