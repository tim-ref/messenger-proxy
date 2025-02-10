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
package de.akquinet.tim.proxy.rawdata.serializer

import de.akquinet.tim.proxy.rawdata.model.UserAgent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class AuspraegungSerializer : KSerializer<UserAgent.Auspraegung> {
    @OptIn(ExperimentalStdlibApi::class)
    private val valuesMap = UserAgent.Auspraegung.entries.associateBy { it.serialized }
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor(
            AuspraegungSerializer::class.qualifiedName ?: "AuspraegungSerializer",
            PrimitiveKind.STRING
        )

    override fun deserialize(decoder: Decoder): UserAgent.Auspraegung =
        valuesMap[decoder.decodeString()] ?: UserAgent.Auspraegung.UNKNOWN

    override fun serialize(encoder: Encoder, value: UserAgent.Auspraegung) {
        encoder.encodeString(value.serialized)
    }
}
