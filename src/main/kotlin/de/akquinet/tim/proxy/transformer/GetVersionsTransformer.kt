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
package de.akquinet.tim.proxy.transformer

import de.akquinet.tim.proxy.client.model.MatrixVersion
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.clientserverapi.model.server.GetVersions

object GetVersionsTransformer : BodyTransformer {

    private val maxMatrixVersion = MatrixVersion("v1.11")

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
        val originalVersions = Json.decodeFromString<GetVersions.Response>(bytes.decodeToString())
        val filteredVersions = originalVersions.versions
            .map { MatrixVersion(it) }
            .filter { it <= maxMatrixVersion }
            .map { it.version }

        val json = Json {
            encodeDefaults = true
        }

        val newBody = json.encodeToString(
            GetVersions.Response(
                versions = filteredVersions.sorted(),
                unstableFeatures = mapOf()
            )
        )

        return when (body) {
            is ByteReadChannel -> ByteReadChannel(newBody)
            is ByteReadPacket -> ByteReadPacket(newBody.toByteArray())
            else -> throw InvalidBodyException(errorMessage)
        }
    }
}
