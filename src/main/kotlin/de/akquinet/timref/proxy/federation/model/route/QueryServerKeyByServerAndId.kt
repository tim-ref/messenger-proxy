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
import net.folivo.trixnity.serverserverapi.model.discovery.QueryServerKeysResponse

/**
 * @see <a href="https://spec.matrix.org/v1.3/server-server-api/#get_matrixkeyv2queryservernamekeyid">matrix spec</a>
 */

@Serializable
@Resource("/_matrix/key/v2/query/{serverName}/{keyId}")
@HttpMethod(HttpMethodType.GET)
@WithoutAuth
data class QueryServerKeyByServerAndId(
    @SerialName("serverName") val serverName: String,
    @SerialName("keyId") val keyId: String,
    @SerialName("minimum_valid_until_ts") val minimumValidUntil: Long? = null,
) : MatrixEndpoint<Unit, QueryServerKeysResponse>
