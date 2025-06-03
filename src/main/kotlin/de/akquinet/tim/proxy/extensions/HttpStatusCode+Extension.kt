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

import io.ktor.http.HttpStatusCode
import net.folivo.trixnity.core.ErrorResponse

fun HttpStatusCode.toErrorResponse(message: String): ErrorResponse =
    when (this) {
        HttpStatusCode.Forbidden -> ErrorResponse.Forbidden(error = message)
        HttpStatusCode.NotFound -> ErrorResponse.NotFound(error = message)
        HttpStatusCode.Unauthorized -> ErrorResponse.Unauthorized(error = message)
        HttpStatusCode.BadRequest -> ErrorResponse.BadJson(error = message)
        else -> ErrorResponse.Unknown(error = message)
    }
