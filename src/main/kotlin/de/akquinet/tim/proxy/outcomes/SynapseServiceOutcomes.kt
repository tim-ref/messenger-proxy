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

data class GetEventApiFailure(override val exception: Throwable) : HttpFailureWithException {
  override val message: String
    get() = "Could not use EventDetails API, cause: ${exception.localizedMessage}"
}

data class FailedToGetAdminAccessToken(override val exception: Throwable) :
  HttpFailureWithException {
  override val message: String
    get() = "Could not get admin access token cause: ${exception.localizedMessage}"
}

data class CouldNotGetEventDetails(val roomId: String, val eventId: String, val error: String) :
  HttpFailure {
  override val message: String
    get() = "Could not get details of room: $roomId and event: $eventId, cause: $error"

  override val httpStatusCode: HttpStatusCode
    get() = HttpStatusCode.BadGateway
}

data class GetAccountDataApiFailure(override val exception: Throwable) : HttpFailureWithException {
  override val message: String
    get() = "Could not use AccountData API, cause: ${exception.localizedMessage}"
}

data class CouldNotGetAccountData(val userId: String, val error: String) : HttpFailure {
  override val message: String
    get() = "Could not get account data of user: $userId, cause: $error"

  override val httpStatusCode: HttpStatusCode
    get() = HttpStatusCode.BadGateway
}
