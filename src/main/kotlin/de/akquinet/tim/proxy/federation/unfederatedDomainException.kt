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