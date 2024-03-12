/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.client.model.route

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.authentication.GetEmailRequestTokenForPassword
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#post_matrixclientv3account3pidemailrequesttoken">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/account/3pid/email/requestToken")
@HttpMethod(HttpMethodType.POST)
@WithoutAuth
object GetEmailRequestTokenFor3Pid :
    MatrixEndpoint<GetEmailRequestTokenForPassword.Request, GetEmailRequestTokenForPassword.Response> {
    @Serializable
    data class Request(
        @SerialName("client_secret") val clientSecret: String,
        @SerialName("email") val email: String,
        @SerialName("id_access_token") val idAccessToken: String? = null,
        @SerialName("id_server") val idServer: String? = null,
        @SerialName("next_link") val nextLink: String? = null,
        @SerialName("send_attempt") val sendAttempt: Long
    )

    @Serializable
    data class Response(
        @SerialName("sid") val sessionId: String,
        @SerialName("submit_url") val submitUrl: String? = null,
    )
}
