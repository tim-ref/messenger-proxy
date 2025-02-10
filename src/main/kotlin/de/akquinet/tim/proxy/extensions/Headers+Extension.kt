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
package de.akquinet.tim.proxy.extensions

import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*

fun Headers.filterUnsafeHeaders(): Headers =
    buildHeaders {
        appendFiltered(this@filterUnsafeHeaders, keepEmpty = true) { name, _ -> !HttpHeaders.isUnsafe(name) }

        // Fehler im Synapse wegen doppeleten contentLength Header:
        // Wenn man OutgoingContent nutzt, sind dort bereits contentLength und contentType definiert.
        // Wenn man es zusätzlich in die headers packt, sendet ktor einfach beide, was andere services
        // und clients gerne mal ablehnen.
        remove(HttpHeaders.ContentLength)
    }

 val Headers.isChunkedTransferEncoding: Boolean
    get() = get(HttpHeaders.TransferEncoding)?.contains("chunked") ?: false