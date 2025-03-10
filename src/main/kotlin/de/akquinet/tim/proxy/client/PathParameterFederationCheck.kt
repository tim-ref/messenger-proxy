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

package de.akquinet.tim.proxy.client

import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.ErrorResponse.ServerNotTrusted
import net.folivo.trixnity.core.ErrorResponseSerializer

/**
 * Federation check of path parameters per [A_26328](https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#A_26328)
 */
val PathParameterFederationCheck = createRouteScopedPlugin(
    name = "PathParameterFederationCheckPlugin",
    createConfiguration = ::PathParameterFederationCheckPluginConfiguration
) {
    pluginConfig.apply {
        onCall { call ->
            val serverName = call.parameters[pathParameterName] ?: "server name not found"
            val isServerFederated = service.areDomainsFederated(setOf(serverName))
            if (!isServerFederated) {
                val matrixError = ServerNotTrusted("Server '$serverName' is not part of federation.")
                val body = json.encodeToJsonElement(ErrorResponseSerializer, matrixError)
                call.respond(BadRequest, body)
            }
        }
    }
}

/**
 * PathParameterFederationCheckPlugin configuration
 *
 * @property pathParameterName the name of the path parameter containing the server name.
 * @property service service performing the federation check.
 */
class PathParameterFederationCheckPluginConfiguration {
    var pathParameterName: String = "serverName"
    lateinit var service: BerechtigungsstufeEinsService
    var json: Json = Json
}