/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.transformer

import com.google.gson.Gson
import de.akquinet.timref.proxy.client.model.MatrixVersion
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.InvalidBodyException
import io.ktor.util.toByteArray
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.toByteArray
import net.folivo.trixnity.clientserverapi.model.server.GetVersions

object GetVersionsTransformer : BodyTransformer {
    override val path: String
        get() = "/_matrix/client/versions"

    override val applicableStates: Set<HttpStatusCode>
        get() = setOf(HttpStatusCode.OK)

    override suspend fun transform(body: Any): Any {
        val errorMessage =
            "Unexpected body type ${body.javaClass}: Please use bodyAsChannel() or bodyAsText() only."
        val bytes = when (body) {
            is ByteReadChannel -> body.toByteArray()
            is ByteReadPacket -> body.readBytes()
            else -> throw InvalidBodyException(errorMessage)
        }
        val originalVersions = Gson().fromJson(bytes.decodeToString(), GetVersions.Response::class.java)
        val maxVersion = MatrixVersion("v1.3")
        val filteredVersions = originalVersions.versions
            .map { MatrixVersion(it) }
            .filter { it <= maxVersion }

        val newBody = Gson().toJson(
            GetVersions.Response(
                versions = filteredVersions.sorted().map { it.version },
                unstable_features = originalVersions.unstable_features
            )
        )

        return when (body) {
            is ByteReadChannel -> ByteReadChannel(newBody)
            is ByteReadPacket -> ByteReadPacket(newBody.toByteArray())
            else -> throw InvalidBodyException(errorMessage)
        }
    }
}
