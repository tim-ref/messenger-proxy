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

import de.akquinet.tim.proxy.authorization.MatrixAuthorizationError
import de.akquinet.tim.proxy.commons.GeneralError

sealed interface ContactManagementError : GeneralError {
    data class Unauthorized(val reason: MatrixAuthorizationError) : ContactManagementError {
        override val message: String = reason.message
    }

    data class MissingParameter(val parameter: String) : ContactManagementError {
        override val message: String = "missing parameter '$parameter'"
    }

    data class ContactNotFound(val ownerMxid: String, val approvedMxid: String) : ContactManagementError {
        override val message: String = "contact not found: $approvedMxid"
    }

    data class ContactMalformed(val throwable: Throwable) : ContactManagementError {
        override val message: String = "contact malformed"
    }

    data class ContactAlreadyExists(val ownerMxid: String, val approvedMxid: String) : ContactManagementError {
        override val message: String = "contact already exists: $approvedMxid"
    }

    data class ContactCouldNotBeCreated(val ownerMxid: String, val throwable: Throwable? = null) :
        ContactManagementError {
        override val message: String = "contact could not be created"
    }

    data class ContactCouldNotBeUpdated(val ownerMxid: String) : ContactManagementError {
        override val message: String = "contact could not be updated"
    }
}