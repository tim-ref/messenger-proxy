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
package de.akquinet.tim.proxy.integrationtests

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.koin.core.qualifier.named

class RegistrationServiceHealthApiMockEngine {

    private val responseHeaders = headersOf(
        "Content-Type" to listOf(ContentType.Any.toString())
    )

    private val client = HttpClient(MockEngine) {
        engine {
            named("registrationServiceHealthApiMock")
            addHandler { request ->
                if (request.url.encodedPath == "/actuator/health/readiness") {
                    respond("", HttpStatusCode.OK, responseHeaders)
                }else {
                    respond("Not found", HttpStatusCode.NotFound, responseHeaders)
                }
            }
        }
    }

    fun get() = client.engine
}
