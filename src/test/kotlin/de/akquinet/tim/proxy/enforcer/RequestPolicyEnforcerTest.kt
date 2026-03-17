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
import de.akquinet.tim.proxy.client.model.CreateRoomRequestWithPrimitiveInitialState.Preset.PRIVATE
import de.akquinet.tim.proxy.client.model.CreateRoomRequestWithPrimitiveInitialState.Preset.PUBLIC
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.clientserverapi.model.rooms.DirectoryVisibility
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent

class RequestPolicyEnforcerTest :
  ShouldSpec({
    val enforcer = RequestPolicyEnforcer()

    context("disableFederateForPublicRoomCreation") {
      val defaultCreateRoomRequest =
        CreateRoomRequestWithPrimitiveInitialState(
          visibility = DirectoryVisibility.PRIVATE,
          creationContent = CreateEventContent(creator = UserId("@test:example.org")),
          invite = setOf(UserId("@test:example.org")),
          name = "my room",
          preset = PRIVATE,
          roomVersion = null,
          initialState = null,
          inviteThirdPid = null,
          roomAliasLocalPart = null,
          topic = null,
          isDirect = null,
          powerLevelContentOverride = null,
        )

      should("add m.federate=false for public room") {
        val request = defaultCreateRoomRequest.copy(preset = PUBLIC)
        enforcer.disableFederateForPublicRoomCreation(request)?.federate shouldBe false
      }

      should("not modify request for public room if m.federate=false") {
        val creationContent =
          CreateEventContent(creator = UserId("@test:example.org"), federate = false)
        val request =
          defaultCreateRoomRequest.copy(
            visibility = DirectoryVisibility.PUBLIC,
            creationContent = creationContent,
          )

        enforcer.disableFederateForPublicRoomCreation(request) shouldBe creationContent
      }

      should("not modify request for private room") {
        enforcer.disableFederateForPublicRoomCreation(defaultCreateRoomRequest) shouldBe
          defaultCreateRoomRequest.creationContent
      }
    }
  })
