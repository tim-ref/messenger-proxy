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
package de.akquinet.tim.proxy.mocks

import de.akquinet.tim.proxy.contactmgmt.database.ContactManagementService
import de.akquinet.tim.proxy.contactmgmt.model.ContactEntity
import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.InfoObject
import de.akquinet.tim.proxy.contactmgmt.database.ContactManagementServiceImpl
import java.time.Instant
import java.util.*

class ContactManagementStub : ContactManagementService {
    override fun getInfo(): InfoObject = ContactManagementServiceImpl().getInfo()

    override suspend fun findContactsOf(ownerMxid: String): List<ContactEntity> {
        return if (ownerMxid == "1234")
            emptyList()
        else
            listOf(ContactEntity(id = UUID.fromString("8f0874ee-8db6-4056-baf8-eeaa1c23aed0"), ownerId = "owner4", approvedId = "12345", displayName = "Alice", inviteStart = 17))
    }

    override suspend fun addContactTo(ownerMxid: String, contactEntity: ContactEntity): ContactEntity? {
        return if (contactEntity.approvedId == "4445")
            contactEntity
        else null
    }

    override suspend fun updateContactSetting(ownerMxid: String, contactEntity: ContactEntity): Boolean {
        return contactEntity.approvedId == "4444"
    }

    override suspend fun getContact(ownerMxid: String, approvedMxid: String): ContactEntity? {
        return if (approvedMxid == "4444")
            ContactEntity(id = UUID.fromString("8f0874ee-8db6-4056-baf8-eeaa1c23aed0"), ownerId = "1234", approvedId = "4444", displayName = "Alice", inviteStart = 17)
        else null
    }

    override suspend fun hasContactValidInviteSettings(
        ownerMxid: String,
        approvedMxid: String,
        timeToCompare: Instant
    ): Boolean {
        return approvedMxid == "4444"
    }

    override suspend fun deleteContactSetting(ownerMxid: String, approvedMxid: String): Boolean {
        return approvedMxid == "5555"
    }

    override suspend fun deleteAllExpired(): Int = 0

}
