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

package de.akquinet.tim.proxy.contactmgmt

import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.contactmgmt.database.ContactManagementService
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.hours

interface ContactManagementApi {
    suspend fun start(env: ApplicationEngineEnvironmentBuilder.() -> Unit = {}): ApplicationEngine
}

class ContactManagementApiImpl(
    private val contactManagementConfig: ProxyConfiguration.ContactManagementConfig,
    private val contactRoutes: ContactRoutes,
    private val contactManagementService: ContactManagementService
) : ContactManagementApi {
    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun start(env: ApplicationEngineEnvironmentBuilder.() -> Unit): ApplicationEngine = embeddedServer(Netty, applicationEngineEnvironment {

        GlobalScope.async { scheduleExpiredContactCleanup() }

        connector {
            port = this@ContactManagementApiImpl.contactManagementConfig.port
        }

        module {
            contactApiServer {
                install(ContentNegotiation) {
                    json()
                }

                with(contactRoutes) {
                    apiRoutes()
                }
            }
        }


    }).start()

    private suspend fun scheduleExpiredContactCleanup() {
        while (true) {
            contactManagementService.deleteAllExpired()
            delay(1.hours)
        }
    }


}
