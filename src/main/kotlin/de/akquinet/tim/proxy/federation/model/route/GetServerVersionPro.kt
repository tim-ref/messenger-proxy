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

package de.akquinet.tim.proxy.federation.model.route

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.serverserverapi.model.discovery.GetServerVersion

/**
 * @see <a href="https://spec.matrix.org/v1.11/server-server-api/#get_matrixfederationv1version">matrix spec</a>
 * Removed authentication in compliance with <a href="https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#A_26331">A_26331</a>.
 */
@Serializable
@Resource("/_matrix/federation/v1/version")
@HttpMethod(HttpMethodType.GET)
object GetServerVersionRequireAuth : MatrixEndpoint<Unit, GetServerVersion.Response> {
    @Serializable
    data class Response(
        @SerialName("server") val server: Server,
    ) {
        @Serializable
        data class Server(
            @SerialName("name") val name: String,
            @SerialName("version") val version: String,
        )
    }
}