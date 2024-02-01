/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.rawdata.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class RawDataMessage(
    val `Inst-ID`: String?,
    val `UA-PTV`: String?,
    val `UA-PV`: String?,
    val `UA-A`: String?,
    val `UA-P`: String?,
    val `UA-OS`: String?,
    val `UA-OS-VERSION`: String?,
    val `UA-cid`: String = "n/a",
    val `M-Dom`: String?,
    val sizeIn: Long?,
    val sizeOut: Long?,
    val tID: String?,
    val profOID: String?,
    val Res: Int
)

@Serializable
data class RawDataMetaData(
    val start: Instant,
    val durationInMs: Long,
    val operation: Operation?,
    val status: Int,
    val message: RawDataMessage
)

