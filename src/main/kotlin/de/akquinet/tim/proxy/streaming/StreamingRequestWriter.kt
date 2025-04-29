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
package de.akquinet.tim.proxy.streaming

import de.akquinet.tim.proxy.extensions.contentLength
import de.akquinet.tim.proxy.extensions.contentType
import de.akquinet.tim.proxy.extensions.filterUnsafeHeaders
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyAndClose
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class StreamingRequestWriter(
    val call: ApplicationCall,
    val requestHeaders: Headers = call.request.headers,
) : OutgoingContent.WriteChannelContent() {
    override val headers: Headers = requestHeaders.filterUnsafeHeaders()
    override val contentLength: Long? = requestHeaders.contentLength ?: run {
        logger.debug { "Request content-length not set" }
        null
    }
    override val contentType: ContentType? = requestHeaders.contentType ?: run {
        logger.debug { "Request content-type not set" }
        null
    }
    override suspend fun writeTo(channel: ByteWriteChannel) {
        val byteCount = call.request.receiveChannel().copyAndClose(channel)
        logger.debug { "Forwarded $byteCount byte(s)." }
    }
}
