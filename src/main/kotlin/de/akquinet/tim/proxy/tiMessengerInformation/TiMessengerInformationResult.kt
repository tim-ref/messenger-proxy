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
package de.akquinet.tim.proxy.tiMessengerInformation

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface TiMessengerInformationResult {
    fun encodeToString(): String
}

@Serializable
data class TiMessengerInformation(
    val title: String,
    val version: String,
    val description: String? = null,
    val contact: String? = null,
) : TiMessengerInformationResult {
    override fun encodeToString(): String = Json.encodeToString(this)
}

@Serializable
data class ServerNameResult(
    val serverName: String
) : TiMessengerInformationResult {
    override fun encodeToString(): String = Json.encodeToString(this)
}

@Serializable
data class IsInsuranceResult(
    val isInsurance: Boolean
) : TiMessengerInformationResult {
    override fun encodeToString(): String = Json.encodeToString(this)
}

@Serializable
data class ErrorResult(
    val errorCode: String,
    val errorMessage: String,
) : TiMessengerInformationResult {
    override fun encodeToString(): String = Json.encodeToString(this)
}