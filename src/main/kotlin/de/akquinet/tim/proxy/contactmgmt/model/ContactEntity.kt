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
package de.akquinet.tim.proxy.contactmgmt.model


import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.Contact
import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.ContactInviteSettings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.exposed.sql.Table
import java.util.*

@Serializable
data class ContactEntity(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val ownerId: String = "unkownOwner",
    val approvedId: String,
    val displayName: String,
    val inviteStart: Long,
    val inviteEnd: Long? = null
) {
    internal fun toDto() = Contact(
        displayName = displayName,
        mxid = approvedId,
        ContactInviteSettings(
            start=inviteStart,
            end=inviteEnd
        )
    )
}

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }
}

object ContactEntities : Table("contacts") {
    val id = uuid("id")
    val ownerId = varchar("owner_id", 256)
    val approvedId = varchar("approved_id", 256)
    val displayName = varchar("display_name", 256)
    val inviteStart = long("invite_start")
    val inviteEnd = long("invite_end").nullable()

    override val primaryKey = PrimaryKey(id)
}
