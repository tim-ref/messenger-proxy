/*
 * Copyright 2022 Trixnity
 */
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

import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.clientserverapi.model.media.Media
import net.folivo.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixmediav3thumbnailservernamemediaid">matrix spec</a>
 *
 * Changes in comparison to [net.folivo.trixnity.clientserverapi.model.media.DownloadThumbnailLegacy]:
 * * query parameter "method" is optional
 */
@Serializable
@Resource("/_matrix/media/v3/thumbnail/{serverName}/{mediaId}")
@HttpMethod(GET)
@WithoutAuth
@Deprecated("use DownloadThumbnail instead")
data class DownloadThumbnailLegacyWithOptionalMethod(
    @SerialName("serverName") val serverName: String,
    @SerialName("mediaId") val mediaId: String,
    @SerialName("width") val width: Long,
    @SerialName("height") val height: Long,
    @SerialName("method") val method: ThumbnailResizingMethod? = null,
    @SerialName("allow_remote") val allowRemote: Boolean? = null,
    @SerialName("allow_redirect") val allowRedirect: Boolean? = null,
    @SerialName("timeout_ms") val timeoutMs: Long? = null,
    @SerialName("animated") val animated: Boolean? = null,
) : MatrixEndpoint<Unit, Media> {
    @Transient
    override val requestContentType = ContentType.Application.Json

    @Transient
    override val responseContentType = ContentType.Application.OctetStream
}