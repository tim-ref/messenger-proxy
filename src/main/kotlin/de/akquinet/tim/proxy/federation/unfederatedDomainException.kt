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
package de.akquinet.tim.proxy.federation

import io.ktor.http.*
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException

/**
 * [A_25534 - Fehlschlag Föderationsprüfung](https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#A_25534)
 */
fun unfederatedDomainException(domain: String) = MatrixServerException(
    statusCode = HttpStatusCode.Forbidden,
    errorResponse = unfederatedDomainResponse(domain)
)

fun unfederatedDomainResponse(domain: String) =
    ErrorResponse.Forbidden("$domain kann nicht in der Föderation gefunden werden")