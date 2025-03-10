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
package de.akquinet.tim.proxy.tiMessengerInformation

import de.akquinet.tim.proxy.authorization.MatrixAuthorizationError
import de.akquinet.tim.proxy.commons.GeneralError
import io.ktor.http.*

sealed interface TiMessengerInformationError : GeneralError {
    data class Unauthorized(val reason: MatrixAuthorizationError) : TiMessengerInformationError {
        override val message = reason.message
    }

    data class MissingParameter(override val message: String) : TiMessengerInformationError

    data class NoMatch(override val message: String) : TiMessengerInformationError

    fun statusCode(): HttpStatusCode = when (this) {
        is Unauthorized -> HttpStatusCode.Unauthorized
        is MissingParameter -> HttpStatusCode.BadRequest
        is NoMatch -> HttpStatusCode.NotFound
    }

    fun toErrorResult(): ErrorResult = ErrorResult(errorCode = statusCode().value.toString(), errorMessage = message)
}