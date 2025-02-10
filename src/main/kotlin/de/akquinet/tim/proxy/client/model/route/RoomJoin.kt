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
package de.akquinet.tim.proxy.client.model.route

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Signed

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/join")
@HttpMethod(POST)
data class RoomJoin(
    @SerialName("roomId") val roomId: String,
    @SerialName("server_name") val serverNames: Set<String>? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<RoomJoin.Request, RoomJoin.Response> {
    @Serializable
    data class Request(
        @SerialName("reason") val reason: String?,
        @SerialName("third_party_signed") val thirdPartySigned: Signed<ThirdParty, String>?,
    ) {
        @Serializable
        data class ThirdParty(
            @SerialName("sender") val sender: UserId,
            @SerialName("mxid") val mxid: UserId,
            @SerialName("token") val token: String,
        )
    }

    @Serializable
    data class Response(
        @SerialName("room_id") val roomId: RoomId
    )
}
