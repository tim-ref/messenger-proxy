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

import de.akquinet.tim.proxy.commons.HttpFailureWithException
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import net.folivo.trixnity.core.ErrorResponse

sealed interface BadRequestOutcome : HttpFailureWithException {
  override val httpStatusCode: HttpStatusCode
    get() = BadRequest
}

data class JSONDeserializationFailure(override val exception: Throwable) : BadRequestOutcome {
  override val message: String
    get() = "Failed to deserialize request body"
}

data class RoomTypeNotPermitted(val type: String) : BadRequestOutcome {
  override val message: String
    get() = "Room type not permitted: $type"
}

data object RoomIdMissing : BadRequestOutcome {
  override val message: String
    get() = "Room ID missing"
}

data object EventIdMissing : BadRequestOutcome {
  override val message: String
    get() = "Event ID missing"
}

data object InvalidRoomStateRequest : BadRequestOutcome {
  override val errorResponse: ErrorResponse
    get() = ErrorResponse.InvalidRoomState(error = message)

  override val message: String
    get() = "Subsequent creation of public rooms not permitted"
}

data object StateTypeMissing : BadRequestOutcome {
  override val message: String
    get() = "State type missing"
}

data object InvitedUserIdMissing : BadRequestOutcome {
  override val message: String
    get() = "InviteUser.Request.userId is missing"
}
