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
package de.akquinet.tim.proxy.client.model.route.account_data

import de.akquinet.tim.proxy.InviteRejectionPolicy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * [A_25045 - Funktionsumfang der Berechtigungskonfiguration](https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.0.0/#A_25045)
 */
@Serializable
enum class DefaultSetting {
    @SerialName("allow all")
    ALLOW_ALL,

    @SerialName("block all")
    BLOCK_ALL,
}

fun InviteRejectionPolicy.asDefaultSetting() = when (this) {
    InviteRejectionPolicy.ALLOW_ALL -> DefaultSetting.ALLOW_ALL
    InviteRejectionPolicy.BLOCK_ALL -> DefaultSetting.BLOCK_ALL
}