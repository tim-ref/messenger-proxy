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
package de.akquinet.tim.proxy.enforcer

import de.akquinet.tim.proxy.client.model.CreateRoomRequestWithPrimitiveInitialState
import de.akquinet.tim.proxy.client.model.CreateRoomRequestWithPrimitiveInitialState.Preset.PUBLIC
import net.folivo.trixnity.clientserverapi.model.rooms.DirectoryVisibility
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent

class RequestPolicyEnforcer {
  /*
    gemSpec_TI-M_Pro_V1.1.0 A_26515-01
    https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Pro/gemSpec_TI-M_Pro_V1.1.0/#A_26515-01
  */
  fun disableFederateForPublicRoomCreation(
    createRoomRequest: CreateRoomRequestWithPrimitiveInitialState
  ): CreateEventContent? {
    val creationContent = createRoomRequest.creationContent

    val isPublic =
      createRoomRequest.visibility == DirectoryVisibility.PUBLIC ||
        createRoomRequest.preset == PUBLIC
    val isFederated = creationContent?.federate ?: true

    if (isPublic && isFederated) {
      return creationContent?.copy(federate = false) ?: CreateEventContent(federate = false)
    }
    return creationContent
  }
}
