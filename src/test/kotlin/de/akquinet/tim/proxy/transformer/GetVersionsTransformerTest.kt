/*
 * Copyright © 2023 - 2024 akquinet GmbH (https://www.akquinet.de)
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
import io.ktor.utils.io.core.*
import org.intellij.lang.annotations.Language

@Language("JSON")
const val inputJson = """
{
  "unstable_features": {
    "org.example.my_feature": true
  },
  "versions": [
    "r0.0.1",
    "v1.1",
    "v1.4"
  ]
}
"""

// Tests compliance with https://spec.matrix.org/v1.3/client-server-api/#get_matrixclientversions
class GetVersionsTransformerTest : ShouldSpec() {
    init {
        coroutineTestScope = true

        should("produce a valid JSON object") {
            val homeserverResponsePacket = toByteReadPacket(inputJson)

            val transformer = GetVersionsTransformer
            val result = transformer.transform(homeserverResponsePacket)

            val proxyResponseBody = fromByteReadPacket(result)

            proxyResponseBody.shouldBeValidJson()
            proxyResponseBody.shouldBeJsonObject()
        }

        should("strip matrix versions above v1.3") {
            val homeserverResponsePacket = toByteReadPacket(inputJson)

            val transformer = GetVersionsTransformer
            val result = transformer.transform(homeserverResponsePacket)

            val proxyResponseBody = fromByteReadPacket(result)

            proxyResponseBody shouldContainJsonKey "$.versions"
            proxyResponseBody shouldContain "\"v1.1\""
            proxyResponseBody shouldNotContain "\"v1.4\""
        }

        should("produce 'unstable features' field") {
            val homeserverResponsePacket = toByteReadPacket(inputJson)

            val transformer = GetVersionsTransformer
            val result = transformer.transform(homeserverResponsePacket)

            val proxyResponseBody = fromByteReadPacket(result)

            proxyResponseBody shouldNotContainJsonKey "$.unstableFeatures"
            proxyResponseBody shouldContainJsonKey "$.unstable_features"
        }
    }

    private fun toByteReadPacket(str: String): ByteReadPacket {
        return ByteReadPacket(str.toByteArray())
    }

    private fun fromByteReadPacket(packet: Any): String {
        val str = packet as ByteReadPacket
        return str.readBytes().decodeToString()
    }

}