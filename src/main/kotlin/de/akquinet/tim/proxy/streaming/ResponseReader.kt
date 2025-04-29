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
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent

class ResponseReader(
    val response: HttpResponse,
    val bytes: ByteArray
) : OutgoingContent.ByteArrayContent() {
    override val contentLength: Long? = response.headers.contentLength
    override val contentType: ContentType? = response.headers.contentType
    override val headers: Headers = response.headers.filterUnsafeHeaders()
    override val status: HttpStatusCode = response.status
    override fun bytes(): ByteArray = bytes
}