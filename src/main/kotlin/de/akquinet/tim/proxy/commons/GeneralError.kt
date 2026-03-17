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
package de.akquinet.tim.proxy.commons

import de.akquinet.tim.proxy.extensions.toErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import net.folivo.trixnity.core.ErrorResponse

interface GeneralError {
  val message: String
}

interface HttpFailure : GeneralError {
  val httpStatusCode: HttpStatusCode
    get() = Forbidden

  val errorResponse: ErrorResponse
    get() = httpStatusCode.toErrorResponse(message)
}

interface HttpFailureWithException : HttpFailure {
  val exception: Throwable?
    get() = null
}
