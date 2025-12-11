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
package de.akquinet.tim.proxy.synapse.client

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import de.akquinet.tim.proxy.config.SynapseClientConfig
import de.akquinet.tim.proxy.error.CouldNotGetRoomDetails
import de.akquinet.tim.proxy.error.RoomDetailsApiFailure
import de.akquinet.tim.proxy.synapse.client.resources.LoginResponseBody
import de.akquinet.tim.proxy.synapse.client.resources.SynapseRoomDetails
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * A client for Synapse's admin API
 *
 * @see [https://element-hq.github.io/synapse/latest/usage/administration/admin_api/index.html]
 */
class SynapseClient(engine: HttpClientEngine, config: SynapseClientConfig) {
  private val httpClient =
    HttpClient(engine) {
      install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
      install(Resources)
      defaultRequest {
        url(config.baseUrl)
        contentType(Application.Json)
      }
    }

  lateinit var bearerToken: String

  // Matrix API -------------------------------------------------------------

  /**
   * @see
   *   [POST /_matrix/client/v3/login](https://spec.matrix.org/v1.11/client-server-api/#post_matrixclientv3login)
   */
  suspend fun login(userIdOrLocalpart: String, password: String): LoginResponseBody {
    return httpClient
      .post("/_matrix/client/v3/login") {
        setBody(
          """
                {
                  "type": "m.login.password",
                  "identifier": {
                    "type": "m.id.user",
                    "user": "$userIdOrLocalpart"
                  },
                  "password": "$password",
                  "initial_device_display_name": "Messenger Proxy"
                }
            """
        )
        expectSuccess = true
      }
      .body()
  }

  /** @see [https://element-hq.github.io/synapse/latest/admin_api/rooms.html#room-details-api] */
  suspend fun getRoomDetails(roomId: String) = either {
    val detailsResponse =
      Either.catch {
          httpClient.get(SynapseRoomDetails(roomId)) {
            bearerAuth(bearerToken)
            expectSuccess = false
          }
        }
        .mapLeft { RoomDetailsApiFailure(it) }
        .bind()

    ensure(detailsResponse.status.isSuccess()) {
      CouldNotGetRoomDetails(roomId = roomId, error = detailsResponse.bodyAsText())
    }

    detailsResponse.body<SynapseRoomDetails.Response>().joinRules
  }
}
