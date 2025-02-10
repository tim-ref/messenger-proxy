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
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.PersistentDataUnit
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.serverserverapi.model.SignedPersistentDataUnit
import net.folivo.trixnity.serverserverapi.model.SignedPersistentStateDataUnit

/**
 * @see <a href="https://spec.matrix.org/v1.3/server-server-api/#put_matrixfederationv1send_joinroomideventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/send_join/{roomId}/{eventId}")
@HttpMethod(HttpMethodType.PUT)
data class SendJoinV1(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val eventId: EventId,
) : MatrixEndpoint<Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>, SendJoinV1.Response> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun requestSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>?
    ): KSerializer<Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>>? {
        @Suppress("UNCHECKED_CAST")
        val serializer = requireNotNull(json.serializersModule.getContextual(PersistentDataUnit.PersistentStateDataUnit::class))
                as KSerializer<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>>
        return Signed.serializer(serializer, String.serializer())
    }

    @Serializable
    data class Response(
        @SerialName("auth_chain")
        val authChain: List<SignedPersistentDataUnit<*>>,
        @SerialName("event")
        val event: Signed<@Contextual PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>? = null,
        @SerialName("origin")
        val origin: String,
        @SerialName("state")
        val state: List<SignedPersistentStateDataUnit<*>>,
    )
}
