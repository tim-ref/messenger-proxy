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

package de.akquinet.tim.proxy.rawdata.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LogInfoUserAgentHeader(
    @SerialName("Produkttypversion") val produkttypversion: String?,
    @SerialName("Produktversion") val produktversion: String?,
    @SerialName("Auspraegung") val auspraegung: UserAgent.Auspraegung?,
    @SerialName("Plattform") val plattform: UserAgent.Plattform?,
    @SerialName("OS") val operatingSystem: String?,
    @SerialName("OS-Version") val osVersion: String?,
    @SerialName("client_id") val clientId: String?,
    @SerialName("Matrix-Domain") val matrixDomain: String?,
)
