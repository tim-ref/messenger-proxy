/*
 * Copyright © 2024 akquinet GmbH (https://www.akquinet.de)
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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*

class HeadersExtensionTest : ShouldSpec({

    should("streamline multiple headers with same value") {
        val headers = headersOf(
            "cache-control" to listOf("no-cache"),
            "content-length" to listOf("724"),
            "content-type" to listOf("application/json","charset=UTF-8"),
            "date" to listOf("Thu, 21 Nov 2024 09:42:44 GMT"),
            "referrer-policy" to listOf("no-referrer"),
            "strict-transport-security" to listOf("max-age=31536000", "includeSubDomains"),
            "x-content-type-options" to listOf("nosniff"),
            "x-frame-options" to listOf("SAMEORIGIN"),
            "x-xss-protection" to listOf("1", "mode=block"),
        )

        headers.filterUnsafeHeaders().contains("Content-Length") shouldBe false
    }
})
