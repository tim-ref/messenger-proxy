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
import de.akquinet.tim.proxy.commons.HttpFailureWithException
import io.ktor.http.HttpStatusCode

data class RoomApiFailure(override val exception: Throwable) : HttpFailureWithException {
  override val message: String
    get() = "Room API call failed: ${exception.localizedMessage}"
}

data class UnexpectedRoomsApiResponse(
  override val httpStatusCode: HttpStatusCode,
  val error: String,
) : HttpFailure {
  override val message: String
    get() = "Unexpected rooms API response ($httpStatusCode): $error"
}

data class CouldNotGetRoomState(val roomId: String, val error: String) : HttpFailure {
  override val message: String
    get() = "Could not get state of room $roomId: $error"

  override val httpStatusCode: HttpStatusCode
    get() = HttpStatusCode.BadGateway
}

data class CouldNotDeleteRoom(val roomId: String, val error: String) : HttpFailure {
  override val message: String
    get() = "Could not delete room $roomId: $error"

  override val httpStatusCode: HttpStatusCode
    get() = HttpStatusCode.BadGateway
}

data class CouldNotGetRoomMessages(val roomId: String, val error: String) : HttpFailure {
  override val message: String
    get() = "Could not get messages for room $roomId: $error"

  override val httpStatusCode: HttpStatusCode
    get() = HttpStatusCode.BadGateway
}
