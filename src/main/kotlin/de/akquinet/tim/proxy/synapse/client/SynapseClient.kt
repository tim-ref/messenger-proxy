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
package de.akquinet.tim.proxy.synapse.client

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import de.akquinet.tim.proxy.config.SynapseClientConfig
import de.akquinet.tim.proxy.outcomes.CouldNotDeleteRoom
import de.akquinet.tim.proxy.outcomes.CouldNotGetAccountData
import de.akquinet.tim.proxy.outcomes.CouldNotGetEventDetails
import de.akquinet.tim.proxy.outcomes.CouldNotGetRoomMessages
import de.akquinet.tim.proxy.outcomes.CouldNotGetRoomState
import de.akquinet.tim.proxy.outcomes.GetAccountDataApiFailure
import de.akquinet.tim.proxy.outcomes.GetEventApiFailure
import de.akquinet.tim.proxy.outcomes.RoomApiFailure
import de.akquinet.tim.proxy.outcomes.UnexpectedRoomsApiResponse
import de.akquinet.tim.proxy.synapse.client.resources.LoginResponseBody
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminAccountData
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminDeleteRoom
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminEvent
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminRoomMessages
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminRoomState
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminRooms
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.delete
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

  suspend fun getEventTimestamp(roomId: String, eventId: String) = either {
    val response =
      Either.catch {
          httpClient.get(SynapseAdminEvent(roomId, eventId)) {
            bearerAuth(bearerToken)
            expectSuccess = false
          }
        }
        .mapLeft { GetEventApiFailure(it) }
        .bind()

    ensure(response.status.isSuccess()) {
      CouldNotGetEventDetails(roomId = roomId, eventId = eventId, error = response.bodyAsText())
    }
    response.body<SynapseAdminEvent.Response>().event.originTimestamp
  }

  // Room Admin API (A_28564-01) -----------------------------------------------

  /**
   * GET /_synapse/admin/v1/rooms
   *
   * @see <a
   *   href="https://element-hq.github.io/synapse/latest/admin_api/rooms.html#list-room-api">List
   *   Room API</a>
   */
  suspend fun listRooms(
    limit: Int = 100,
    from: Int? = null,
    orderBy: String = "name",
    direction: String = "f",
  ) = either {
    val response =
      Either.catch {
          httpClient.get(SynapseAdminRooms(limit, from, orderBy, direction)) {
            bearerAuth(bearerToken)
            expectSuccess = false
          }
        }
        .mapLeft { RoomApiFailure(it) }
        .bind()

    ensure(response.status.isSuccess()) {
      UnexpectedRoomsApiResponse(httpStatusCode = response.status, error = response.bodyAsText())
    }
    response.body<SynapseAdminRooms.Response>()
  }

  /**
   * GET /_synapse/admin/v1/rooms/{room_id}/state
   *
   * @see <a
   *   href="https://element-hq.github.io/synapse/latest/admin_api/rooms.html#room-state-api">Room
   *   State API</a>
   */
  suspend fun getRoomState(roomId: String) = either {
    val response =
      Either.catch {
          httpClient.get(SynapseAdminRoomState(roomId)) {
            bearerAuth(bearerToken)
            expectSuccess = false
          }
        }
        .mapLeft { RoomApiFailure(it) }
        .bind()

    ensure(response.status.isSuccess()) {
      CouldNotGetRoomState(roomId = roomId, error = response.bodyAsText())
    }
    response.body<SynapseAdminRoomState.Response>()
  }

  /**
   * GET /_synapse/admin/v1/rooms/{room_id}/messages
   *
   * @see <a
   *   href="https://element-hq.github.io/synapse/latest/admin_api/rooms.html#room-messages-api">Room
   *   Messages API</a>
   */
  suspend fun getRoomMessages(roomId: String, limit: Int = 100) = either {
    val response =
      Either.catch {
          httpClient.get(SynapseAdminRoomMessages(roomId, limit)) {
            bearerAuth(bearerToken)
            expectSuccess = false
          }
        }
        .mapLeft { RoomApiFailure(it) }
        .bind()

    ensure(response.status.isSuccess()) {
      CouldNotGetRoomMessages(roomId = roomId, error = response.bodyAsText())
    }
    response.body<SynapseAdminRoomMessages.Response>()
  }

  /**
   * DELETE /_synapse/admin/v1/rooms/{room_id}
   *
   * @see <a
   *   href="https://element-hq.github.io/synapse/latest/admin_api/rooms.html#delete-room-api">Delete
   *   Room API</a>
   */
  suspend fun deleteRoom(roomId: String, purge: Boolean = true, block: Boolean = false) = either {
    val response =
      Either.catch {
          httpClient.delete(SynapseAdminDeleteRoom(roomId)) {
            bearerAuth(bearerToken)
            setBody(SynapseAdminDeleteRoom.Request(purge = purge, block = block))
            expectSuccess = false
          }
        }
        .mapLeft { RoomApiFailure(it) }
        .bind()

    ensure(response.status.isSuccess()) {
      CouldNotDeleteRoom(roomId = roomId, error = response.bodyAsText())
    }
    response.body<SynapseAdminDeleteRoom.Response>()
  }

  suspend fun getAccountData(userId: String) = either {
    val response =
      Either.catch {
          httpClient.get(SynapseAdminAccountData(userId)) {
            bearerAuth(bearerToken)
            expectSuccess = false
          }
        }
        .mapLeft { GetAccountDataApiFailure(it) }
        .bind()

    ensure(response.status.isSuccess()) {
      CouldNotGetAccountData(userId = userId, error = response.bodyAsText())
    }
    response.body<SynapseAdminAccountData.Response>()
  }
}
