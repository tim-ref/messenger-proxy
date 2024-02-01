/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.rawdata.model

import kotlinx.serialization.Serializable

@Serializable
data class LogInfoUserAgent(
    val Produkttypversion: String?,
    val Produktversion: String?,
    val Auspraegung: String?,
    val Plattform: String?,
    val OS: String?,
    val OS_Version: String?,
    val client_id: String?,
    val Matrix_Domain: String?,
)

@Serializable
data class LogInfoUserAgentHeader(
    val Produkttypversion: String?,
    val Produktversion: String?,
    val Auspraegung: String?,
    val Plattform: String?,
    val OS: String?,
    val `OS-Version`: String?,
    val client_id: String?,
    val `Matrix-Domain`: String?,
)