/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.federation.model.route

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

/**
 * @see <a href="https://apereo.github.io/cas/6.6.x/protocol/CAS-Protocol-V2-Specification.html">CAS 2.0 Spec</a>
 */
@Serializable
@Resource("/cas/proxyValidate")
@HttpMethod(HttpMethodType.GET)
@WithoutAuth
data class CasProxyValidate(
    @SerialName("service") val service: String,
    @SerialName("ticket") val ticket: String,
    @SerialName("pgtUrl") val pgtUrl: String? = null,
    @SerialName("renew") val renew: Boolean? = null,
) : MatrixEndpoint<Unit, Unit>
