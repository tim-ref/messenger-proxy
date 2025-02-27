/*
 * Copyright 2022 Trixnity
 * Copyright © 2025 akquinet GmbH (https://www.akquinet.de)
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
import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3loginssoredirect">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/login/sso/redirect")
@HttpMethod(GET)
@WithoutAuth
data class SSORedirect(
    @SerialName("redirectUrl") val redirectUrl: String,
) : MatrixEndpoint<Unit, Unit> {
    override val responseContentType: ContentType?
        get() = null
}