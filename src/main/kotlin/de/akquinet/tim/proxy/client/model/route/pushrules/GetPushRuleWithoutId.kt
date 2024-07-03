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
package de.akquinet.tim.proxy.client.model.route.pushrules

import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.PushRuleKind
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

@Serializable
@Resource("/_matrix/client/v3/pushrules/{scope}/{kind}/")
@HttpMethod(HttpMethodType.GET)
data class GetPushRuleWithoutId(
    @SerialName("scope") val scope: String,
    @SerialName("kind") val kind: PushRuleKind,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, PushRule> {
    override fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: PushRule?
    ): KSerializer<PushRule> {
        val serializer = when (kind) {
            PushRuleKind.OVERRIDE -> PushRule.Override.serializer()
            PushRuleKind.CONTENT -> PushRule.Content.serializer()
            PushRuleKind.ROOM -> PushRule.Room.serializer()
            PushRuleKind.SENDER -> PushRule.Sender.serializer()
            PushRuleKind.UNDERRIDE -> PushRule.Underride.serializer()
        }
        @Suppress("UNCHECKED_CAST")
        return serializer as KSerializer<PushRule>
    }
}
