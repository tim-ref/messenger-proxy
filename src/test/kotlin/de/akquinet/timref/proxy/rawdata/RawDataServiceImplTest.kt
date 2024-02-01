/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.rawdata

import de.akquinet.timref.proxy.ProxyConfiguration
import de.akquinet.timref.proxy.rawdata.model.LogInfoUserAgentHeader
import de.akquinet.timref.proxy.rawdata.model.Operation
import de.akquinet.timref.proxy.rawdata.model.RawDataMessage
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.http.*
import io.mockk.mockk
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
        "{\"Produktversion\":\"1.9.0\",\"Produkttypversion\":null,\"Auspraegung\":\"Messenger-Client\",\"Plattform\":\"web\",\"OS\":\"web\",\"OS-Version\":\"web\",\"client_id\":null,\"Matrix-Domain\":\"test.de\"}"
    val logInfoUserAgentHeader = Json.decodeFromString<LogInfoUserAgentHeader>(logInfoUserAgentJson)
    val filledHeaderMessage = RawDataMessage(
        `Inst-ID` = Operation.MP_CLIENT_LOGIN_REQUEST_ACCESS_TOKEN.toString(),
        `M-Dom` = logInfoConfig.homeFQDN,
        Res = 200,
        sizeIn = 150,
        sizeOut = 33,
        profOID = logInfoConfig.professionId,
        tID = logInfoConfig.telematikId,
        `UA-A` = logInfoUserAgentHeader.Auspraegung,
        `UA-OS` = logInfoUserAgentHeader.OS,
        `UA-P` = logInfoUserAgentHeader.Plattform,
        `UA-OS-VERSION` = logInfoUserAgentHeader.`OS-Version`,
        `UA-PTV` = logInfoUserAgentHeader.Produkttypversion,
        `UA-PV` = logInfoUserAgentHeader.Produktversion
    )

    val emptyHeaderMessage = RawDataMessage(
        `Inst-ID` = Operation.MP_CLIENT_LOGIN_REQUEST_ACCESS_TOKEN.toString(),
        `M-Dom` = logInfoConfig.homeFQDN,
        Res = 200,
        sizeIn = 150,
        sizeOut = 33,
        profOID = logInfoConfig.professionId,
        tID = logInfoConfig.telematikId,
        `UA-A` = "n/a",
        `UA-OS` = "n/a",
        `UA-P` = "n/a",
        `UA-OS-VERSION` = "n/a",
        `UA-PTV` = "n/a",
        `UA-PV` = "n/a"
    )

    val rawDataService = RawDataServiceImpl(logInfoConfig, httpClient)

    should("should create userAgent from useragent header") {
        val headers: Headers = headersOf(Pair("useragent", listOf(logInfoUserAgentJson)), Pair(HttpHeaders.ContentLength, listOf("150")))
        val responseCode = 200
        val timOperation = Operation.MP_CLIENT_LOGIN_REQUEST_ACCESS_TOKEN
        val sizeOut = 33
        val method = rawDataService.javaClass.getDeclaredMethod("createClientRawData", Headers::class.java, Int::class.java, Operation::class.java, Int::class.java)
        method.isAccessible = true

        method.invoke(rawDataService, headers, responseCode, timOperation, sizeOut) shouldBe filledHeaderMessage
    }

    should("should create unknownInfo agent from missing useragent header") {
        val method = rawDataService.javaClass.getDeclaredMethod("createClientRawData", Headers::class.java, Int::class.java, Operation::class.java, Int::class.java)
        method.isAccessible = true
        val headers = headersOf(HttpHeaders.ContentLength, "150")
        val responseCode = 200
        val timOperation = Operation.MP_CLIENT_LOGIN_REQUEST_ACCESS_TOKEN
        val sizeOut = 33

        method.invoke(rawDataService, headers, responseCode, timOperation, sizeOut) shouldBe emptyHeaderMessage

    }

})
