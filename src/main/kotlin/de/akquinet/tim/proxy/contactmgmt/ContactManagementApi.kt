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
package de.akquinet.tim.proxy.contactmgmt

import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.contactmgmt.database.ContactManagementService
import de.akquinet.tim.proxy.util.metricsModule
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import mu.KotlinLogging
import kotlin.time.Duration.Companion.hours

interface ContactManagementApi {
    suspend fun start(): ApplicationEngine
}

private val logger = KotlinLogging.logger {}

class ContactManagementApiImpl(
    private val contactManagementConfig: ProxyConfiguration.ContactManagementConfig,
    private val contactRoutes: ContactRoutes,
    private val contactManagementService: ContactManagementService
) : ContactManagementApi {
    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun start(): ApplicationEngine =
        embeddedServer(Netty, port = this@ContactManagementApiImpl.contactManagementConfig.port) {

            if (GlobalScope.async {
                    scheduleExpiredContactCleanup()
                }.start()) {
                logger.info { "Started cleanup job for expired invite settings." }
            } else {
                logger.info { "Cleanup job for expired invite settings already running." }
            }

            metricsModule()
            contactApiServer {
                install(ContentNegotiation) {
                    json()
                }

                with(contactRoutes) {
                    apiRoutes()
                }
            }

        }.start()

    private suspend fun scheduleExpiredContactCleanup() {
        while (true) {
            val deleted = contactManagementService.deleteAllExpired()
            logger.info { "Deleted expired invite settings: $deleted" }

            delay(1.hours)
        }
    }
}
