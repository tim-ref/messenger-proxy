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
/*
 * Modified by akquinet GmbH on 07.02.2024
 *
 * Originally from https://gitlab.com/trixnity/trixnity
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.akquinet.tim.proxy.util

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.ErrorResponseSerializer
import net.folivo.trixnity.core.MatrixServerException

// this code is copied from Trixnity and slightly adapted to our needs to conform with Sytest
fun Application.customMatrixServer(json: Json, routes: Route.() -> Unit) {
    installCustomMatrixApiServer(json)
    routing {
        routes()
    }
}

fun Application.installCustomMatrixApiServer(json: Json) {
    install(Resources)
    install(StatusPages) {
        exception(json)
        notFound(json)
        methodNotAllowed(json)
        unsupportedMediaType(json)
    }
    install(ContentNegotiation) {
        json(json)
    }
}

private fun StatusPagesConfig.exception(json: Json) {
    exception { call: ApplicationCall, cause: Throwable ->
        call.application.log.error(cause)
        when (cause) {
            is MatrixServerException ->
                call.respond(
                    cause.statusCode,
                    json.encodeToJsonElement(ErrorResponseSerializer, cause.errorResponse)
                )

            is SerializationException ->
                call.respond(
                    HttpStatusCode.BadRequest,
                    json.encodeToJsonElement(ErrorResponseSerializer, ErrorResponse.BadJson(cause.message.orEmpty()))
                )

            // catching this exception is crucial for Sytest, this is a change in comparison to trixnity
            is BadRequestException ->
                call.respond(
                    HttpStatusCode.BadRequest,
                    json.encodeToJsonElement(ErrorResponseSerializer, ErrorResponse.Unknown(cause.message.orEmpty()))
                )

            else -> {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    json.encodeToJsonElement(ErrorResponseSerializer, ErrorResponse.Unknown(cause.message.orEmpty()))
                )
            }
        }
    }
}

private fun StatusPagesConfig.notFound(json: Json) {
    status(HttpStatusCode.NotFound) { call, _ ->
        call.respond(
            HttpStatusCode.NotFound,
            json.encodeToJsonElement(
                ErrorResponseSerializer,
                // in comparison to the trixnity code response code was changed from Unrecognized to NotFound to adapt to Sytest
                ErrorResponse.NotFound("unsupported (or unknown) endpoint")
            )
        )
    }
}

/**
 * Similarly, a 405 M_UNRECOGNIZED error is used to denote an unsupported
 * method to a known endpoint.
 *
 * See https://spec.matrix.org/v1.11/server-server-api/#unsupported-endpoints
 */
private fun StatusPagesConfig.methodNotAllowed(json: Json) {
    status(HttpStatusCode.MethodNotAllowed) { call, _ ->
        call.respond(
            HttpStatusCode.MethodNotAllowed,
            json.encodeToJsonElement(
                ErrorResponseSerializer,
                ErrorResponse.Unrecognized("This endpoint is implemented, but the method is not supported.")
            )
        )
    }
}

private fun StatusPagesConfig.unsupportedMediaType(json: Json) {
    status(HttpStatusCode.UnsupportedMediaType) { call, _ ->
        call.respond(
            HttpStatusCode.UnsupportedMediaType,
            json.encodeToJsonElement(
                ErrorResponseSerializer,
                ErrorResponse.Unrecognized("media type of request is not supported")
            )
        )
    }
}
