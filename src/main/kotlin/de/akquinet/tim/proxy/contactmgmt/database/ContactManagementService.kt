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

package de.akquinet.tim.proxy.contactmgmt.database

import de.akquinet.tim.proxy.contactmgmt.database.DatabaseFactory.dbQuery
import de.akquinet.tim.proxy.contactmgmt.model.Contact
import de.akquinet.tim.proxy.contactmgmt.model.ContactManagementInfo
import de.akquinet.tim.proxy.contactmgmt.model.Contacts
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

interface ContactManagementService {
    fun getInfo(): ContactManagementInfo
    suspend fun getContacts(listOwnerMxid: String): List<Contact>
    suspend fun createContactSetting(listOwnerMxid: String, contact: Contact): Contact?
    suspend fun updateContactSetting(listOwnerMxid: String, contact: Contact): Boolean
    suspend fun getContact(listOwnerMxid: String, approvedMxid: String): Contact?
    suspend fun deleteContactSetting(listOwnerMxid: String, approvedMxid: String): Boolean
    suspend fun deleteAllExpired()

}

class ContactManagementServiceImpl() : ContactManagementService {

    private fun resultRowToContact(row: ResultRow) = Contact(
        id = row[Contacts.id],
        ownerId = row[Contacts.ownerId],
        approvedId = row[Contacts.approvedId],
        displayName = row[Contacts.displayName],
        inviteStart = row[Contacts.inviteStart],
        inviteEnd = row[Contacts.inviteEnd],
    )

    override fun getInfo() = ContactManagementInfo(
        title = "Contact Management des TI-Messengers",
        description = "Contact Management des TI-Messengers. Betreiber: <Betreibername>",
        contact = "Kontaktinformationen",
        version = "1.0.0"
    )

    override suspend fun getContacts(listOwnerMxid: String): List<Contact> = dbQuery {
        Contacts.selectAll()
            .where { Contacts.ownerId eq listOwnerMxid }
            .map(::resultRowToContact)
    }

    override suspend fun createContactSetting(listOwnerMxid: String, contact: Contact): Contact? = dbQuery {
        val insertStatement = Contacts.insert {
            it[id] = UUID.randomUUID()
            it[ownerId] = listOwnerMxid
            it[approvedId] = contact.approvedId
            it[displayName] = contact.displayName
            it[inviteEnd] = contact.inviteEnd
            it[inviteStart] = contact.inviteStart
        }
        insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToContact)
    }

    override suspend fun updateContactSetting(listOwnerMxid: String, contact: Contact): Boolean = dbQuery {
        Contacts.update({ Contacts.id eq contact.id }) {
            it[ownerId] = contact.ownerId
            it[approvedId] = contact.approvedId
            it[displayName] = contact.displayName
            it[inviteEnd] = contact.inviteEnd
            it[inviteStart] = contact.inviteStart
        } > 0
    }

    override suspend fun getContact(listOwnerMxid: String, approvedMxid: String): Contact? = dbQuery {
        Contacts.selectAll()
            .where { (Contacts.ownerId eq listOwnerMxid).and(Contacts.approvedId eq approvedMxid) }
            .limit(1)
            .map(::resultRowToContact).firstOrNull() }

    override suspend fun deleteContactSetting(listOwnerMxid: String, approvedMxid: String): Boolean = dbQuery{
        Contacts.deleteWhere { (approvedId eq approvedMxid).and(ownerId eq listOwnerMxid) } > 0
    }

    override suspend fun deleteAllExpired() {
        Contacts.deleteWhere { inviteEnd less Instant.now().epochSecond }
    }


}
