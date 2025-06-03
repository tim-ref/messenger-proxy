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
package de.akquinet.tim.proxy.error

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import net.folivo.trixnity.core.ErrorResponse

// failures
sealed interface SendMessageFailure : ValidationFailed

data object TypeParameterIsMissingFailure : SendMessageFailure {
    override val httpStatusCode: HttpStatusCode
        get() = BadRequest
    override val message: String
        get() = "type"
    override val errorResponse: ErrorResponse
        get() = ErrorResponse.MissingParam(message)
}

data object ThreadingIsNotAllowed : SendMessageFailure {
    override val httpStatusCode: HttpStatusCode
        get() = BadRequest
    override val message: String
        get() = "Message threading is not supported – see A_25395-02"
    override val errorResponse: ErrorResponse
        get() = ErrorResponse.Forbidden(message)
}

data object InvalidKeyLength : SendMessageFailure {
    override val httpStatusCode: HttpStatusCode
        get() = BadRequest
    override val message: String
        get() = "Reaction key must not be longer than one emoji – see A_26228-01"
    override val errorResponse: ErrorResponse
        get() = ErrorResponse.BadJson(message)
}

data object KeyMustOnlyContainEmoji : SendMessageFailure {
    override val httpStatusCode: HttpStatusCode
        get() = BadRequest
    override val message: String
        get() = "Key must only contain emoji"
}

data object InvalidRelationType : SendMessageFailure {
    override val httpStatusCode: HttpStatusCode
        get() = BadRequest
    override val message: String
        get() = "RelType must be 'm.annotation'"
}

// successes
sealed interface SendMessageSuccess : ValidationSuccess

data object SendMessageIsValid : SendMessageSuccess {
    override val message: String
        get() = "SendMessage validated successfully"
}