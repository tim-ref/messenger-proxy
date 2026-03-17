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
package de.akquinet.tim.proxy.synapse.client.resources

import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET

/**
 * GET /_synapse/admin/v1/rooms/{room_id}/state Room State Admin API
 *
 * @see <a
 *   href="https://element-hq.github.io/synapse/latest/admin_api/rooms.html#room-state-api">Room
 *   State API</a>
 */
@Serializable
@Resource("/_synapse/admin/v1/rooms/{roomId}/state")
@HttpMethod(GET)
class SynapseAdminRoomState(@SerialName("roomId") val roomId: String) {
  @Serializable data class Response(@SerialName("state") val state: List<StateEvent>)

  @Serializable
  data class StateEvent(
    @SerialName("type") val type: String,
    @SerialName("state_key") val stateKey: String? = null,
    @SerialName("content") val content: JsonObject,
    @SerialName("origin_server_ts") val originServerTs: Long,
    @SerialName("sender") val sender: String,
  )
}
