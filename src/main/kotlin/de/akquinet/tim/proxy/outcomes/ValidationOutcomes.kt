/*
 * Copyright © 2023 - 2026 akquinet GmbH (https://www.akquinet.de)
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
package de.akquinet.tim.proxy.outcomes

import de.akquinet.tim.proxy.commons.HttpFailure
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import net.folivo.trixnity.core.ErrorResponse

data object Unauthorized : HttpFailure {
  override val message: String
    get() = "Authorization header is missing"

  override val httpStatusCode: HttpStatusCode
    get() = Unauthorized
}

data object ReferencedEventTooOld : HttpFailure {
  override val httpStatusCode: HttpStatusCode
    get() = Forbidden

  override val message: String
    get() = "Event cannot be redacted, it is older than 24h - see A_28358"
}

data object MoreThanOneInvitedUserOnRoomCreation : HttpFailure {
  override val httpStatusCode: HttpStatusCode
    get() = Forbidden

  override val message: String
    get() = "Only one user can be invited at room creation. - see A_25368-01"
}

data class NotSupportedRoomVersion(val version: String?, val supportedRoomVersions: Set<String>) :
  HttpFailure {
  override val httpStatusCode: HttpStatusCode
    get() = BadRequest

  override val message: String
    get() =
      "Room version $version is not supported. Allowed versions are: ${supportedRoomVersions.joinToString()}. - see A_26202, A_26203"

  override val errorResponse: ErrorResponse
    get() = ErrorResponse.UnsupportedRoomVersion(message)
}

data object InviteBlocked : HttpFailure {
  override val message: String
    get() = "The invite was blocked by the invitees policy"
}

data object UnexpectedRejectionPolicyFormat : HttpFailure {
  override val message: String
    get() = "Unexpected Rejection policy from json"
}

// successes
sealed interface GeneralSuccess {
  val message: String
}

data object ValidationSuccess : GeneralSuccess {
  override val message: String
    get() = "Validation successful"
}
