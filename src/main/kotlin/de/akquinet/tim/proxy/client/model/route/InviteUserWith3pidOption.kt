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
package de.akquinet.tim.proxy.client.model.route

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.rooms.InviteUser
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#post_matrixclientv3roomsroomidinvite">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/invite")
@HttpMethod(POST)
data class InviteUserWith3pidOption(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("user_id") val asUserId: UserId? = null,
    @SerialName("address") val address: String? = null,
    @SerialName("id_access_token") val idAccessToken: String? = null,
    @SerialName("id_server") val idServer: String? = null,
    @SerialName("medium") val medium: String? = null,
) : MatrixEndpoint<InviteUser.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("user_id") val userId: UserId? = null,
        @SerialName("address") val address: String? = null,
        @SerialName("id_access_token") val idAccessToken: String? = null,
        @SerialName("id_server") val idServer: String? = null,
        @SerialName("medium") val medium: String? = null,
    )
}
