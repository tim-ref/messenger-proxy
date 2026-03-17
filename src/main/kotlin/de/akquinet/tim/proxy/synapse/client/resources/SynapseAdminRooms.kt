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
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET

/**
 * GET /_synapse/admin/v1/rooms List Room API
 *
 * @see <a
 *   href="https://element-hq.github.io/synapse/latest/admin_api/rooms.html#list-room-api">List Room
 *   API</a>
 */
@Serializable
@Resource("/_synapse/admin/v1/rooms")
@HttpMethod(GET)
class SynapseAdminRooms(
  @SerialName("limit") val limit: Int = 100,
  @SerialName("from") val from: Int? = null,
  @SerialName("order_by") val orderBy: String = "name",
  @SerialName("dir") val direction: String = "f",
) {
  @Serializable
  data class Response(
    @SerialName("rooms") val rooms: List<Room>,
    @SerialName("next_batch") val nextBatch: Int? = null,
    @SerialName("total_rooms") val totalRooms: Int,
  )

  @Serializable
  data class Room(
    @SerialName("room_id") val roomId: String,
    @SerialName("name") val name: String? = null,
    @SerialName("creator") val creator: String? = null,
    @SerialName("joined_members") val joinedMembers: Int = 0,
  )
}
