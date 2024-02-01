/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.mocks

import de.akquinet.timref.proxy.contactmgmt.database.ContactManagementService
import de.akquinet.timref.proxy.contactmgmt.model.Contact
import de.akquinet.timref.proxy.contactmgmt.model.ContactManagementInfo
import java.util.*

class ContactManagementStub : ContactManagementService {
    override fun getInfo(): ContactManagementInfo {
        return ContactManagementInfo(
            title = "Contact Management des TI-Messengers",
            description = "Contact Management des TI-Messengers. Betreiber: <Betreibername>",
            contact = "Kontaktinformationen",
            version = "1.0.0"
        )
    }

    override suspend fun getContacts(listOwnerMxid: String): List<Contact> {
        return if (listOwnerMxid == "1234")
            emptyList()
        else
            listOf(Contact(id = UUID.fromString("8f0874ee-8db6-4056-baf8-eeaa1c23aed0"), ownerId = "owner4", approvedId = "12345", displayName = "Alice", inviteStart = 17))
    }

    override suspend fun createContactSetting(listOwnerMxid: String, contact: Contact): Contact? {
        return if (contact.approvedId == "4445")
            contact
        else null
    }

    override suspend fun updateContactSetting(listOwnerMxid: String, contact: Contact): Boolean {
        return contact.approvedId == "4444"
    }

    override suspend fun getContact(listOwnerMxid: String, approvedMxid: String): Contact? {
        return if (approvedMxid == "4444")
            Contact(id = UUID.fromString("8f0874ee-8db6-4056-baf8-eeaa1c23aed0"), ownerId = "1234", approvedId = "4444", displayName = "Alice", inviteStart = 17)
        else null
    }

    override suspend fun deleteContactSetting(listOwnerMxid: String, approvedMxid: String): Boolean {
        return approvedMxid == "5555"
    }

    override suspend fun deleteAllExpired() {
    }

}
