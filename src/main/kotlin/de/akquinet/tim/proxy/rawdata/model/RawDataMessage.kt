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

import de.akquinet.tim.proxy.rawdata.serializer.AuspraegungSerializer
import de.akquinet.tim.proxy.rawdata.serializer.PlatformSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawDataMessage(
    @SerialName("Inst-ID") val instanceId: String = "n/a",
    @SerialName("M-Dom") val matrixDomain: String = "n/a",
    @SerialName("UA-A") val userAgentAuspraegung: UserAgent.Auspraegung = UserAgent.Auspraegung.UNKNOWN,
    @SerialName("UA-cid") val userAgentClientId: String = "n/a",
    @SerialName("UA-OS-VERSION") val userAgentOSVersion: String = "n/a",
    @SerialName("UA-OS") val userAgentOS: String = "n/a",
    @SerialName("UA-P") val userAgentPlatform: UserAgent.Plattform = UserAgent.Plattform.UNKNOWN,
    @SerialName("UA-PTV") val userAgentProdukttypversion: String = "n/a",
    @SerialName("UA-PV") val userAgentProduktversion: String = "n/a",
    @SerialName("profOID") val professionOID: String,
    @SerialName("Res") val responseHttpStatusCode: Int, // status code http
    @SerialName("sizeIn") val requestContentLength: Long = 0,
    @SerialName("sizeOut") val responseContentLength: Long = 0,
    @SerialName("tID") val telematikID: String = "n/a"
)

@Serializable
data class RawDataMetaData(
    val start: Instant,
    val durationInMs: Long,
    val operation: Operation?,
    val status: Int,
    val message: RawDataMessage
)

class UserAgent {

    @Serializable(with = AuspraegungSerializer::class)
    enum class Auspraegung(
        val serialized: String
    ) {
        UNKNOWN("n/a"),
        ORG_ADMIN_CLIENT("Org-Admin-Client"),
        MESSENGER_CLIENT("Messenger-Client");
    }

    @Serializable(with = PlatformSerializer::class)
    enum class Plattform(
        val serialized: String
    ) {
        UNKNOWN("n/a"),
        MOBIL("mobil"),
        STATIONAER("stationaer"),
        WEB("web");
    }
}

