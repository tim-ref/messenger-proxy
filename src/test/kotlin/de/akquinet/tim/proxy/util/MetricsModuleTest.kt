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
package de.akquinet.tim.proxy.util

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test


class MetricsModuleTest {
    @Test
    fun `should offer metrics`() = testApplication {
        application {
            metricsModule()
        }
        val response = client.get("/metrics")

        response shouldHaveStatus HttpStatusCode.OK
        response.bodyAsText() shouldContain "system_cpu_count"
        response.bodyAsText() shouldContain "ktor_http_server_requests_active"
    }
}