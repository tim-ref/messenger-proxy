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
package de.akquinet.tim.proxy.client.model.route.cas

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

/**
 * @see <a href="https://spec.matrix.org/legacy/client_server/r0.3.0.html#get-matrix-client-r0-login-cas-ticket">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/r0/login/cas/ticket")
@HttpMethod(HttpMethodType.GET)
@WithoutAuth
data class CasTicket(
    @SerialName("redirectUrl") val redirectUrl: String? = null,
    @SerialName("session") val session: String? = null,
    @SerialName("ticket") val ticket: String,
) : MatrixEndpoint<Unit, Unit>
