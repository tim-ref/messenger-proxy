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

package de.akquinet.tim.proxy.client

import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import de.akquinet.tim.proxy.ProxyConfiguration
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.clientserverapi.model.authentication.WhoAmI
import net.folivo.trixnity.core.model.UserId

interface AccessTokenToUserId : suspend (String) -> UserId

class AccessTokenToUserIdImpl(
    private val config: ProxyConfiguration.InboundProxyConfiguration,
    private val matrixApiClient: MatrixApiClient,
) : AccessTokenToUserId {
    private data class CachedUserId(
        val userId: UserId,
        val cachedAt: Instant,
    )

    private val cache = MutableStateFlow<Map<String, CachedUserId>>(mapOf())
    override suspend fun invoke(accessToken: String): UserId {
        val cachedUserId = cache.value[accessToken]
        return if (cachedUserId != null
            && cachedUserId.cachedAt < (Clock.System.now() + config.accessTokenToUserIdCacheDuration)
        ) {
            cachedUserId.userId
        } else {
            val fetchedUserId = matrixApiClient.request(WhoAmI()) {
                bearerAuth(accessToken)
                url {
                    Url(config.homeserverUrl).also {
                        protocol = it.protocol
                        host = it.host
                        port = it.port
                    }
                }
            }.getOrThrow().userId
            cache.update { it + (accessToken to CachedUserId(fetchedUserId, Clock.System.now())) }
            fetchedUserId
        }
    }
}
