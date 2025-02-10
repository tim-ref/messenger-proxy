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
package de.akquinet.tim.proxy.rawdata

import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.rawdata.model.Operation
import de.akquinet.tim.proxy.rawdata.model.RawDataMessage
import de.akquinet.tim.proxy.rawdata.model.UserAgent
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.mockk.mockk

class RawDataServiceImplTest : ShouldSpec({

    val httpClient: HttpClient = mockk()
    val rawDataServiceUrl = "https://localhost:1234"
    val rawDataPath = "/add-performance-data"
    val logInfoConfig = ProxyConfiguration.LogInfoConfig(
        "$rawDataServiceUrl$rawDataPath",
        "doctor",
        "2384234234",
        "MP-1",
        "home.de"
    )
    val logInfoUserAgentJson =
        """{
            "Produktversion":"1.9.0",
            "Produkttypversion":null,
            "Auspraegung":"Messenger-Client",
            "Plattform":"web",
            "OS":"web",
            "OS-Version":"web",
            "client_id":null,
            "Matrix-Domain":"test.de"
        }""".trimMargin()

    val rawDataService = RawDataServiceImpl(logInfoConfig, httpClient)

    should("should create userAgent from useragent header") {
        val headers: Headers = headersOf(Pair("useragent", listOf(logInfoUserAgentJson)), Pair(HttpHeaders.ContentLength, listOf("150")))
        val responseCode = 200
        val timOperation = Operation.MP_CLIENT_LOGIN_REQUEST_ACCESS_TOKEN
        val sizeOut = 33
        val method = rawDataService.javaClass.getDeclaredMethod("createClientRawData", Headers::class.java, Int::class.java, Operation::class.java, Int::class.java)
        method.isAccessible = true

        val expected = RawDataMessage(
            instanceId = Operation.MP_CLIENT_LOGIN_REQUEST_ACCESS_TOKEN.toString(),
            matrixDomain = "home.de",
            responseHttpStatusCode = 200,
            requestContentLength = 150,
            responseContentLength = 33,
            professionOID = "doctor",
            telematikID = "2384234234",
            userAgentAuspraegung = UserAgent.Auspraegung.MESSENGER_CLIENT,
            userAgentOS = "web",
            userAgentPlatform = UserAgent.Plattform.WEB,
            userAgentOSVersion = "web",
            userAgentProdukttypversion = "n/a",
            userAgentProduktversion = "1.9.0"
        )

        method.invoke(rawDataService, headers, responseCode, timOperation, sizeOut) shouldBe expected
    }

    should("should create unknownInfo agent from missing useragent header") {
        val method = rawDataService.javaClass.getDeclaredMethod("createClientRawData", Headers::class.java, Int::class.java, Operation::class.java, Int::class.java)
        method.isAccessible = true
        val headers = headersOf(HttpHeaders.ContentLength, "150")
        val responseCode = 200
        val timOperation = Operation.MP_CLIENT_LOGIN_REQUEST_ACCESS_TOKEN
        val sizeOut = 33

        val expected = RawDataMessage(
            instanceId = Operation.MP_CLIENT_LOGIN_REQUEST_ACCESS_TOKEN.toString(),
            matrixDomain = "home.de",
            responseHttpStatusCode = 200,
            requestContentLength = 150,
            responseContentLength = 33,
            professionOID = "doctor",
            telematikID = "2384234234",
            userAgentAuspraegung = UserAgent.Auspraegung.UNKNOWN,
            userAgentOS = "n/a",
            userAgentPlatform = UserAgent.Plattform.UNKNOWN,
            userAgentOSVersion = "n/a",
            userAgentProdukttypversion = "n/a",
            userAgentProduktversion = "n/a"
        )

        method.invoke(rawDataService, headers, responseCode, timOperation, sizeOut) shouldBe expected
    }
})
