/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.actuator

import ch.qos.logback.classic.Level
import de.akquinet.timref.proxy.ProxyConfiguration
import de.akquinet.timref.proxy.availability.RegistrationServiceHealthApi
import de.akquinet.timref.proxy.logging.LogLevelService
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineEnvironmentBuilder
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException

interface ActuatorRoutes {
    suspend fun start(env: ApplicationEngineEnvironmentBuilder.() -> Unit = {}): ApplicationEngine
}

@Serializable
data class HealthLivenessResponse(
    val inboundProxy: Boolean,
    val outboundProxy: Boolean,
)

@Serializable
data class HealthReadinessResponse(
    val database: Boolean,
    val registrationService: Boolean
)

class ActuatorRoutesImpl(
    private val actuatorConfig: ProxyConfiguration.ActuatorConfiguration,
    private val inboundProxyConfig: ProxyConfiguration.InboundProxyConfiguration,
    private val outboundProxyConfig: ProxyConfiguration.OutboundProxyConfiguration,
    private val logLevelService: LogLevelService,
    private val registrationServiceHealthApi: RegistrationServiceHealthApi,
    private val httpClient: HttpClient
) : ActuatorRoutes {
    override suspend fun start(env: ApplicationEngineEnvironmentBuilder.() -> Unit): ApplicationEngine =
        embeddedServer(Netty, applicationEngineEnvironment {
            connector {
                port = this@ActuatorRoutesImpl.actuatorConfig.port
            }
            module {
                routing {
                    val healthPath = "${actuatorConfig.basePath}/health"
                    val loggingPath = "${actuatorConfig.basePath}/logging"

                    // Liveness probe
                    get("$healthPath/liveness") {
                        val inboundProxyResponse =
                            httpClient.get("http://127.0.0.1:" + inboundProxyConfig.port + healthPath)
                        val outboundProxyResponse =
                            httpClient.get("http://127.0.0.1:" + outboundProxyConfig.port + healthPath)

                        // create response body
                        val livenessResponse = HealthLivenessResponse(
                            inboundProxy = inboundProxyResponse.status == HttpStatusCode.OK,
                            outboundProxy = outboundProxyResponse.status == HttpStatusCode.OK,
                        )

                        // calculate status
                        val responseStatus: HttpStatusCode = if (
                            setOf(inboundProxyResponse.status, outboundProxyResponse.status)
                                .containsOnly(HttpStatusCode.OK)
                        ) {
                            HttpStatusCode.OK
                        } else {
                            HttpStatusCode.ServiceUnavailable
                        }

                        call.respondText(
                            Json.encodeToString(livenessResponse),
                            ContentType.Application.Json,
                            responseStatus
                        )
                    }

                    // Readiness probe
                    get("$healthPath/readiness") {
                        val isConnectedToDatabase = transaction {
                            try {
                                !connection.isClosed
                            } catch (e: SQLException) {
                                false
                            }
                        }

                        val registrationServiceResponse = registrationServiceHealthApi.getReadinessState()

                        // create response body
                        val readinessResponse = HealthReadinessResponse(
                            registrationService = registrationServiceResponse.status == HttpStatusCode.OK,
                            database = isConnectedToDatabase
                        )

                        // calculate status
                        val responseStatus: HttpStatusCode =
                            if (isConnectedToDatabase && setOf(
                                    registrationServiceResponse.status,
                                ).containsOnly(HttpStatusCode.OK)
                            ) {
                                HttpStatusCode.OK
                            } else {
                                HttpStatusCode.ServiceUnavailable
                            }

                        call.respondText(
                            Json.encodeToString(readinessResponse),
                            ContentType.Application.Json,
                            responseStatus
                        )
                    }

                    // logger actuator
                    get("$loggingPath/levels") {
                        val acceptedValues = org.slf4j.event.Level.entries
                            .joinToString(", ", "[", "]") { """"${it.name}"""" }
                        call.respond(HttpStatusCode.OK, """{ "acceptedValues": $acceptedValues }""")
                    }

                    val paramNewLogLevel = "newLogLevel"
                    put("$loggingPath/{$paramNewLogLevel}") {
                        val newLogLevel = call.parameters[paramNewLogLevel].toString()
                        logLevelService.setLogLevel(Level.toLevel(newLogLevel))
                        call.respond(HttpStatusCode.Accepted)

                        logLevelService.scheduleLogLevelReset()
                    }

                    val paramLoggerIdentifier = "loggerIdentifier"
                    put("$loggingPath/{$paramNewLogLevel}/{$paramLoggerIdentifier}") {
                        val newLogLevel = call.parameters[paramNewLogLevel].toString()
                        val loggerIdentifier = call.parameters[paramLoggerIdentifier].toString()
                        logLevelService.setLogLevel(Level.toLevel(newLogLevel), loggerIdentifier)
                        call.respond(HttpStatusCode.Accepted)

                        logLevelService.scheduleLogLevelReset(loggerIdentifier)
                    }
                }
            }
        }).start()
}

fun <T> Set<T>.containsOnly(element: T): Boolean = if (this.size == 1) {
    this.first() == element
} else {
    false
}
