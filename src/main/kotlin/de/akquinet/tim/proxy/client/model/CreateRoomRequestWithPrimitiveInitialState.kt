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
package de.akquinet.tim.proxy.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.clientserverapi.model.rooms.DirectoryVisibility
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent

/// Copied from Trixnity
/// Original Contextual Parsing leads to parsing errors when StatesTypes aren't defined.
/// To fix this it is adapted to parse list of initial states to JsonObjects instead.
@Serializable
data class CreateRoomRequestWithPrimitiveInitialState(
  @SerialName("visibility") val visibility: DirectoryVisibility = DirectoryVisibility.PRIVATE,
  @SerialName("room_alias_name") val roomAliasLocalPart: String? = null,
  @SerialName("name") val name: String? = null,
  @SerialName("topic") val topic: String? = null,
  @SerialName("invite") val invite: Set<UserId>? = null,
  @SerialName("invite_3pid") val inviteThirdPid: Set<InviteThirdPid>? = null,
  @SerialName("room_version") val roomVersion: String? = null,
  @SerialName("creation_content") val creationContent: CreateEventContent? = null,
  @SerialName("initial_state") val initialState: List<JsonObject?>? = null,
  @SerialName("preset") val preset: Preset? = null,
  @SerialName("is_direct") val isDirect: Boolean? = null,
  @SerialName("power_level_content_override")
  val powerLevelContentOverride: PowerLevelsEventContent? = null,
) {
  @Serializable
  data class InviteThirdPid(
    @SerialName("id_server") val identityServer: String,
    @SerialName("id_access_token") val identityServerAccessToken: String,
    @SerialName("medium") val medium: String,
    @SerialName("address") val address: String,
  )

  @Serializable
  enum class Preset {
    @SerialName("private_chat") PRIVATE,
    @SerialName("public_chat") PUBLIC,
    @SerialName("trusted_private_chat") TRUSTED_PRIVATE,
  }
}
