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
package de.akquinet.tim.proxy.client.model.route

import io.ktor.http.*
import io.ktor.server.routing.*
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException

fun Route.installPushrulesRoutesForBadRequest() {
    route("/_matrix/client/v3/pushrules") {
        get("/{scope}") {
            throw MatrixServerException(HttpStatusCode.BadRequest, ErrorResponse.MissingParam(""))
        }
        get("/{scope}/{kind}") {
            throw MatrixServerException(HttpStatusCode.BadRequest, ErrorResponse.MissingParam(""))
        }
        get("/{scope}/{kind}/{ruleId}/{attribute}") {
            throw MatrixServerException(HttpStatusCode.BadRequest, ErrorResponse.InvalidParam(""))
        }
        put("/") {
            throw MatrixServerException(HttpStatusCode.BadRequest, ErrorResponse.MissingParam(""))
        }
        put("/{scope}") {
            throw MatrixServerException(HttpStatusCode.BadRequest, ErrorResponse.MissingParam(""))
        }
        put("/{scope}/{kind}") {
            throw MatrixServerException(HttpStatusCode.BadRequest, ErrorResponse.MissingParam(""))
        }
        put("/{scope}/{kind}/") {
            throw MatrixServerException(HttpStatusCode.BadRequest, ErrorResponse.InvalidParam(""))
        }
        put("/{scope}/{kind}/{ruleId}/{attribute}") {
            throw MatrixServerException(HttpStatusCode.BadRequest, ErrorResponse.InvalidParam(""))
        }
    }
}
