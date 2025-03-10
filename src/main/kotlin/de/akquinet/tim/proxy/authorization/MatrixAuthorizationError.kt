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
package de.akquinet.tim.proxy.authorization

import de.akquinet.tim.proxy.commons.GeneralError

sealed interface MatrixAuthorizationError : GeneralError {
    data object MissingMxidHeader : MatrixAuthorizationError {
        override val message: String = "missing 'mxid' header"
    }

    data object MissingAuthorizationHeader : MatrixAuthorizationError {
        override val message: String = "missing 'authorization' header"
    }

    data class MalformedBearerToken(val token: String) : MatrixAuthorizationError {
        override val message: String = "malformed bearer token: '$token'"
    }

    data class AuthenticationFailed(val token: String) : MatrixAuthorizationError {
        override val message: String = "no mxid associated with bearer token"
    }

    data class MxidsDoNotMatch(val givenMxid: String, val authenticatedMxid: String) : MatrixAuthorizationError {
        override val message: String = "incompatible mxid and bearer token"
    }
}