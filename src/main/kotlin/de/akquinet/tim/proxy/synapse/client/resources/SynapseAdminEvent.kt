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

@Serializable
@Resource("/_synapse/admin/v1/rooms/{roomId}/context/{eventId}")
@HttpMethod(GET)
class SynapseAdminEvent(
  @SerialName("roomId") val roomId: String,
  @SerialName("eventId") val eventId: String,
) {
  @Serializable data class Response(@SerialName("event") val event: Event)
}

@Serializable class Event(@SerialName("origin_server_ts") val originTimestamp: Long)
