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
package de.akquinet.tim

import io.kotest.assertions.json.beJsonObject
import io.kotest.assertions.json.containJsonKey
import io.kotest.assertions.json.containJsonKeyValue
import io.kotest.matchers.Matcher
import io.kotest.matchers.compose.all
import io.kotest.matchers.should

// Checks https://spec.matrix.org/v1.3/client-server-api/#standard-error-response
fun equalJsonMatrixStandardErrorResponse(errorResponse: ErrorResponse): Matcher<String?> = Matcher.all(
    beJsonObject(),
    containJsonKeyValue("$.errcode", errorResponse.errcode),
    containJsonKeyValue("$.error", errorResponse.error),
)

infix fun String?.shouldEqualJsonMatrixStandard(errorResponse: ErrorResponse) =
    this should equalJsonMatrixStandardErrorResponse(errorResponse)

data class ErrorResponse(val errcode: String, val error: String)


fun jsonMatrixStandardErrorResponse(): Matcher<String?> = Matcher.all(
    beJsonObject(),
    containJsonKey("$.errcode"),
    containJsonKey("$.error"),
)
