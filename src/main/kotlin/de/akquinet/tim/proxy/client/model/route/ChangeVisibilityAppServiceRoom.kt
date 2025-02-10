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
package de.akquinet.tim.proxy.client.model.route

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.3/application-service-api/#put_matrixclientv3directorylistappservicenetworkidroomid">matrix spec</a>
 */

@Serializable
@Resource("/_matrix/client/v3/directory/list/appservice/{networkId}/{roomId}")
@HttpMethod(HttpMethodType.PUT)
data class ChangeVisibilityAppServiceRoom(
    @SerialName("networkId") val networkId: String,
    @SerialName("roomId") val roomId: String,
) : MatrixEndpoint<ChangeVisibilityAppServiceRoom.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("visibility") val visibility: Visibility
    )

    @Serializable
    enum class Visibility {
        @SerialName("public") PUBLIC,
        @SerialName("private") PRIVATE
    }
}
