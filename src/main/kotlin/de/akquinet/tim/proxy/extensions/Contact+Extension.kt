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
package de.akquinet.tim.proxy.extensions

import de.akquinet.tim.proxy.contactmgmt.model.ContactEntity
import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.Contact
import java.util.*

fun Contact.toEntity(
    ownerId: String,
    uuid: String?
) = ContactEntity(
    id = uuid?.let { UUID.fromString(it) } ?: UUID.randomUUID(),
    ownerId = ownerId,
    approvedId = mxid,
    displayName = displayName,
    inviteStart = inviteSettings.start,
    inviteEnd = inviteSettings.end
)