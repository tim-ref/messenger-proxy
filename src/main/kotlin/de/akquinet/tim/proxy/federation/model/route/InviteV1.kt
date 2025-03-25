/*
 * Copyright 2022 Trixnity
 */
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
package de.akquinet.tim.proxy.federation.model.route

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.PersistentDataUnit
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.keys.Signed

/**
 * @see <a href="https://spec.matrix.org/v1.3/server-server-api/#put_matrixfederationv1inviteroomideventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/invite/{roomId}/{eventId}")
@HttpMethod(HttpMethodType.PUT)
data class InviteV1(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val eventId: EventId,
) : MatrixEndpoint<InviteV1.Request, InviteV1.Response> {
    @Serializable
    data class Request(
        @SerialName("event")
        val event: Signed<@Contextual PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>,
        @SerialName("invite_room_state")
        val inviteRoomState: List<@Contextual ClientEvent.StrippedStateEvent<*>>? = null,
        @SerialName("room_version")
        val roomVersion: String,
    )

    @Serializable
    data class Response(
        @SerialName("event")
        val event: Signed<@Contextual PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>,
    )
}
