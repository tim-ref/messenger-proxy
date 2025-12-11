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

import de.akquinet.tim.proxy.extensions.toErrorResponse
import io.ktor.http.HttpStatusCode
import net.folivo.trixnity.core.ErrorResponse

// failures
sealed interface A26515Failure : GeneralFailure {
    val details: String
        get() = "No details"

    override val message: String
        get() =  if (exception != null) "$details: ${exception?.localizedMessage}" else details

    val httpStatusCode: HttpStatusCode
        get() = HttpStatusCode.InternalServerError

    val errorResponse: ErrorResponse
        get() = httpStatusCode.toErrorResponse(message)
}

data object JoinPublicRoomOverFederationForbidden : A26515Failure {
  override val details: String
    get() = "Joining federated public rooms is forbidden"

  override val httpStatusCode: HttpStatusCode
    get() = HttpStatusCode.Forbidden
}

// Synapse-Admin API
data class CouldNotGetAdminAccessToken(override val exception: Throwable) : A26515Failure {
  override val details: String
    get() = "Could not get admin access token cause: ${exception.localizedMessage}"
}

data class CouldNotGetRoomDetails(val roomId: String, val error: String) : A26515Failure {
  override val details: String
    get() = "Could not get details of room: $roomId, cause: $error"

  override val httpStatusCode: HttpStatusCode
    get() = HttpStatusCode.BadGateway
}

data class RoomDetailsApiFailure(override val exception: Throwable) : A26515Failure {
  override val details: String
    get() = "Could not use RoomDetails API, cause: ${exception.localizedMessage}"
}

// successes
sealed interface A26515Success {
    val message: String
        get() = "Validation successful"
}

data object A26515ValidationPassed : A26515Success