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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

/**
 * Slice of [net.folivo.trixnity.serverserverapi.model.federation.Invite] and [InviteV1] used for different checks.
 */
@Serializable
data class InviteRequestBodyCommon(val event: InviteEventCommon)

@Serializable
data class InviteEventCommon(
    val sender: UserId,
    @SerialName("state_key") val stateKey: UserId,
    val content: MembershipEventContentCommon
)

@Serializable
data class MembershipEventContentCommon(val membership: String)
