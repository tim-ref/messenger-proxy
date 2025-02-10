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
package de.akquinet.tim.proxy.contactmgmt.database

import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.InfoObject
import de.akquinet.tim.proxy.contactmgmt.database.DatabaseFactory.dbQuery
import de.akquinet.tim.proxy.contactmgmt.model.ContactEntities
import de.akquinet.tim.proxy.contactmgmt.model.ContactEntity
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.time.Instant
import java.util.*

// Berechtigungsstufe 3
interface ContactManagementService {
    fun getInfo(): InfoObject
    suspend fun findContactsOf(ownerMxid: String): List<ContactEntity>
    suspend fun addContactTo(ownerMxid: String, contactEntity: ContactEntity): ContactEntity?
    suspend fun updateContactSetting(ownerMxid: String, contactEntity: ContactEntity): Boolean
    suspend fun getContact(ownerMxid: String, approvedMxid: String): ContactEntity?
    suspend fun hasContactValidInviteSettings(ownerMxid: String, approvedMxid: String, timeToCompare: Instant): Boolean
    suspend fun deleteContactSetting(ownerMxid: String, approvedMxid: String): Boolean
    suspend fun deleteAllExpired(): Int
}

class ContactManagementServiceImpl : ContactManagementService {

    private fun resultRowToContact(row: ResultRow) = ContactEntity(
        id = row[ContactEntities.id],
        ownerId = row[ContactEntities.ownerId],
        approvedId = row[ContactEntities.approvedId],
        displayName = row[ContactEntities.displayName],
        inviteStart = row[ContactEntities.inviteStart],
        inviteEnd = row[ContactEntities.inviteEnd],
    )

    override fun getInfo() = InfoObject(
        title = "Contact Management des TI-Messengers",
        description = "Contact Management des TI-Messengers. Betreiber: <Betreibername>",
        contact = "Contact information",
        version = "1.0.2"
    )

    override suspend fun findContactsOf(ownerMxid: String): List<ContactEntity> = dbQuery {
        ContactEntities.selectAll().where { ContactEntities.ownerId eq ownerMxid }.map(::resultRowToContact)
    }

    override suspend fun addContactTo(ownerMxid: String, contactEntity: ContactEntity): ContactEntity? = dbQuery {
        val insertStatement = ContactEntities.insert {
            it[id] = UUID.randomUUID()
            it[ownerId] = ownerMxid
            it[approvedId] = contactEntity.approvedId
            it[displayName] = contactEntity.displayName
            it[inviteEnd] = contactEntity.inviteEnd
            it[inviteStart] = contactEntity.inviteStart
        }
        insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToContact)
    }

    override suspend fun updateContactSetting(ownerMxid: String, contactEntity: ContactEntity): Boolean = dbQuery {
        ContactEntities.update({ ContactEntities.id eq contactEntity.id }) {
            it[ownerId] = contactEntity.ownerId
            it[approvedId] = contactEntity.approvedId
            it[displayName] = contactEntity.displayName
            it[inviteEnd] = contactEntity.inviteEnd
            it[inviteStart] = contactEntity.inviteStart
        } > 0
    }

    override suspend fun getContact(ownerMxid: String, approvedMxid: String): ContactEntity? = dbQuery {
        ContactEntities.selectAll().where {
                (ContactEntities.ownerId eq ownerMxid).and(ContactEntities.approvedId eq approvedMxid)
            }.limit(1).map(::resultRowToContact).firstOrNull()
    }

    override suspend fun hasContactValidInviteSettings(
        ownerMxid: String, approvedMxid: String, timeToCompare: Instant
    ): Boolean = dbQuery {
        ContactEntities.selectAll().where {
                (ContactEntities.ownerId eq ownerMxid).and(ContactEntities.approvedId eq approvedMxid)
                    .and(ContactEntities.inviteStart lessEq timeToCompare.epochSecond).and(
                        ContactEntities.inviteEnd.isNull().or(
                            ContactEntities.inviteEnd greater timeToCompare.epochSecond
                        )
                    )

            }.count() > 0
    }


    override suspend fun deleteContactSetting(ownerMxid: String, approvedMxid: String): Boolean = dbQuery {
        ContactEntities.deleteWhere { (approvedId eq approvedMxid).and(ownerId eq ownerMxid) } > 0
    }

    override suspend fun deleteAllExpired(): Int = dbQuery {
        ContactEntities.deleteWhere { inviteEnd less Instant.now().epochSecond }
    }

}