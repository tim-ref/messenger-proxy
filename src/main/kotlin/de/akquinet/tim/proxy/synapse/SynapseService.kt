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
package de.akquinet.tim.proxy.synapse

import arrow.core.Either
import arrow.core.flatMap
import de.akquinet.tim.proxy.config.SynapseClientConfig
import de.akquinet.tim.proxy.error.CouldNotGetAdminAccessToken
import de.akquinet.tim.proxy.synapse.client.SynapseClient

class SynapseService(
  val synapseClient: SynapseClient,
  val config: SynapseClientConfig,
) {

  private suspend fun getAdminUserAccessToken(): String {
    return try {
      synapseClient
        .login(
          userIdOrLocalpart =
            config.username?.value ?: throw RuntimeException("Admin user name not set"),
          password =
            config.password?.value ?: throw RuntimeException("Admin user password not set"),
        )
        .accessToken
    } catch (e: Exception) {
      throw RuntimeException("Failed to acquire admin user access token", e)
    }
  }

  suspend fun getRoomJoinRules(roomId: String) =
    Either.catch { getAdminUserAccessToken() }
      .mapLeft { CouldNotGetAdminAccessToken(it) }
      .flatMap { token ->
        synapseClient.bearerToken = token
        synapseClient.getRoomDetails(roomId)
      }
}
