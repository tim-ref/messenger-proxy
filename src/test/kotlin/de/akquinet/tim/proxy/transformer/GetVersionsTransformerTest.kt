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

import io.kotest.assertions.json.shouldBeJsonObject
import io.kotest.assertions.json.shouldBeValidJson
import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.assertions.json.shouldNotContainJsonKey
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.util.toByteArray
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.toByteArray
import org.intellij.lang.annotations.Language

@Language("JSON")
const val inputJson = """
{
  "unstable_features": {
    "org.example.my_feature": true
  },
  "versions": [
    "r0.0.1",
    "v1.11",
    "v1.12"
  ]
}
"""

// Tests compliance with https://spec.matrix.org/v1.11/client-server-api/#get_matrixclientversions
class GetVersionsTransformerTest : ShouldSpec() {
    init {
        coroutineTestScope = true

        context("Input variant: ByteReadChannel") {
            should("produce a valid JSON object") {
                val homeserverResponse = ByteReadChannel(inputJson.toByteArray())

                val transformer = GetVersionsTransformer
                val result = transformer.transform(homeserverResponse) as ByteReadChannel

                val proxyResponseBody = result.toByteArray().decodeToString()

                proxyResponseBody.shouldBeValidJson()
                proxyResponseBody.shouldBeJsonObject()
            }

            should("strip matrix versions above v1.11") {
                val homeserverResponse = ByteReadChannel(inputJson.toByteArray())

                val transformer = GetVersionsTransformer
                val result = transformer.transform(homeserverResponse) as ByteReadChannel

                val proxyResponse = result.toByteArray().decodeToString()

                proxyResponse shouldContainJsonKey "$.versions"
                proxyResponse shouldContain "\"v1.11\""
                proxyResponse shouldNotContain "\"v1.12\""
            }

            should("produce 'unstable features' field") {
                val homeserverResponse = ByteReadChannel(inputJson.toByteArray())

                val transformer = GetVersionsTransformer
                val result = transformer.transform(homeserverResponse) as ByteReadChannel

                val proxyResponse = result.toByteArray().decodeToString()

                proxyResponse shouldNotContainJsonKey "$.unstableFeatures"
                proxyResponse shouldContainJsonKey "$.unstable_features"
            }
        }

        context("Input variant: ByteReadPacket") {
            should("produce a valid JSON object") {
                val homeserverResponse = ByteReadPacket(inputJson.toByteArray())

                val transformer = GetVersionsTransformer
                val result = transformer.transform(homeserverResponse) as ByteReadPacket

                val proxyResponseBody = result.readBytes().decodeToString()

                proxyResponseBody.shouldBeValidJson()
                proxyResponseBody.shouldBeJsonObject()
            }

            should("strip matrix versions above v1.11") {
                val homeserverResponse = ByteReadPacket(inputJson.toByteArray())

                val transformer = GetVersionsTransformer
                val result = transformer.transform(homeserverResponse) as ByteReadPacket

                val proxyResponse = result.readBytes().decodeToString()

                proxyResponse shouldContainJsonKey "$.versions"
                proxyResponse shouldContain "\"v1.11\""
                proxyResponse shouldNotContain "\"v1.12\""
            }

            should("produce 'unstable features' field") {
                val homeserverResponse = ByteReadPacket(inputJson.toByteArray())

                val transformer = GetVersionsTransformer
                val result = transformer.transform(homeserverResponse) as ByteReadPacket

                val proxyResponse = result.readBytes().decodeToString()

                proxyResponse shouldNotContainJsonKey "$.unstableFeatures"
                proxyResponse shouldContainJsonKey "$.unstable_features"
            }
        }
    }
}