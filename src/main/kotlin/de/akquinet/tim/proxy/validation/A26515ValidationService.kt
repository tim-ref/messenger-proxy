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
package de.akquinet.tim.proxy.validation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import de.akquinet.tim.proxy.error.A26515Failure
import de.akquinet.tim.proxy.error.A26515ValidationPassed
import de.akquinet.tim.proxy.error.JoinPublicRoomOverFederationForbidden
import de.akquinet.tim.proxy.synapse.SynapseService
import de.akquinet.tim.proxy.synapse.client.resources.JoinRules
import net.folivo.trixnity.core.model.RoomId

class A26515ValidationService(private val synapseService: SynapseService) {

    /**
     * Implements checks for joining public rooms
     *
     * @see <a
     *   href="https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Pro/gemSpec_TI-M_Pro_V1.0.2/#A_26515"
     *   A_26515 - Öffentliche Räume beitreten</a>
     */
    suspend fun ensureSameHomeserverForPublicRoomJoin(
        roomId: RoomId
    ): Either<A26515Failure, A26515ValidationPassed> = either {
        synapseService
            .getRoomJoinRules(roomId.full)
            .map {
                ensure(it != JoinRules.PUBLIC) { JoinPublicRoomOverFederationForbidden }
                A26515ValidationPassed
            }
            .bind()
    }
}
