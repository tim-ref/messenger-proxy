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
package de.akquinet.tim.proxy.extensions

import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.Error
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.unauthorized(message: String = "UNAUTHORIZED") =
    this.respond(Unauthorized, message)

suspend fun ApplicationCall.badRequest(message: String) =
    this.respond(
        status = BadRequest,
        message = Error(
            errorCode = BadRequest.toString(),
            errorMessage = message
        )
    )

suspend fun ApplicationCall.notFound(message: String) =
    this.respond(
        status = NotFound,
        message = Error(
            errorCode = NotFound.toString(),
            errorMessage = message
        )
    )

suspend fun ApplicationCall.internalServerError(message: String) =
    this.respond(
        InternalServerError,
        Error(
            errorCode = InternalServerError.toString(),
            errorMessage = message
        )
    )