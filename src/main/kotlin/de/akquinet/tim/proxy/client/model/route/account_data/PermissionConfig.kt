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

import de.akquinet.tim.proxy.ProxyConfiguration
import kotlinx.serialization.Serializable

/**
 * [A_25258](https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.0.0/#A_25258)
 * [JSON schema](https://github.com/gematik/api-ti-messenger/blob/main/src/schema/permissionConfig.json)
 */
@Serializable
data class PermissionConfig(
    val defaultSetting: DefaultSetting,
)

fun ProxyConfiguration.TimAuthorizationCheckConfiguration.asPermissionConfig() =
    PermissionConfig(this.inviteRejectionPolicy.asDefaultSetting())