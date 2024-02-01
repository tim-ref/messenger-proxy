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
import de.akquinet.timref.proxy.rawdata.model.RawDataMetaData
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging


interface RawDataService {
    suspend fun serverRawDataForward(request: ApplicationRequest, response: HttpResponse, duration: Long, timOperation: Operation, sizeOut: Int)
    suspend fun contactRawDataForward(request: ApplicationRequest, statusCode: HttpStatusCode, responseLength: Long, duration: Long, timOperation: Operation)
    suspend fun clientRawDataForward(requestHeaders: Headers, responseCode: Int, duration: Long, timOperation: Operation, sizeOut: Int)

}

private val log = KotlinLogging.logger { }


class RawDataServiceImpl(
    private val logInfoConfig: ProxyConfiguration.LogInfoConfig,
    private val httpClient: HttpClient,
) : RawDataService {
    override suspend fun serverRawDataForward(request: ApplicationRequest, response: HttpResponse, duration: Long, timOperation: Operation, sizeOut: Int) {
        RawDataMetaData(
            start = Clock.System.now(),
            durationInMs = duration,
            message = createServerRawData(request, response, timOperation, sizeOut),
            operation = timOperation,
            status = response.status.value
        ).let {
            sendMessageLog(it)
        }
    }

    private fun createServerRawData(request: ApplicationRequest, response: HttpResponse, timOperation: Operation, sizeOut: Int): RawDataMessage = RawDataMessage(
        `Inst-ID` = timOperation.toString() + request.origin.uri,
        `M-Dom` = logInfoConfig.homeFQDN,
        Res = response.status.value,
        sizeIn = request.contentLength(),
        sizeOut = sizeOut.toLong(),
        profOID = logInfoConfig.professionId,
        tID = logInfoConfig.telematikId,
        `UA-A` = "n/a",
        `UA-OS` = "n/a",
        `UA-P` = "n/a",
        `UA-OS-VERSION` = "n/a",
        `UA-PTV` = "n/a",
        `UA-PV` = "n/a"
    )


    override suspend fun contactRawDataForward(request: ApplicationRequest, statusCode: HttpStatusCode, responseLength: Long, duration: Long, timOperation: Operation) {
        RawDataMetaData(
            start = Clock.System.now(),
            durationInMs = duration,
            message = createContactRawData(request, statusCode, responseLength, timOperation),
            operation = timOperation,
            status = statusCode.value
        ).let { sendMessageLog(it) }
    }

    private fun createContactRawData(request: ApplicationRequest, statusCode: HttpStatusCode, responseLength: Long, timOperation: Operation): RawDataMessage {
        return if (request.headers["useragent"] != null) {
            val logInfoUserAgent = Json.decodeFromString<LogInfoUserAgentHeader>(request.headers["useragent"]!!)
            RawDataMessage(
                `Inst-ID` = timOperation.toString(),
                `M-Dom` = logInfoConfig.homeFQDN,
                Res = statusCode.value,
                sizeIn = request.contentLength(),
                sizeOut = responseLength,
                profOID = logInfoConfig.professionId,
                tID = logInfoConfig.telematikId,
                `UA-A` = logInfoUserAgent.Auspraegung,
                `UA-OS` = logInfoUserAgent.OS,
                `UA-P` = logInfoUserAgent.Plattform,
                `UA-OS-VERSION` = logInfoUserAgent.`OS-Version`,
                `UA-PTV` = logInfoUserAgent.Produkttypversion,
                `UA-PV` = logInfoUserAgent.Produktversion
            )
        } else {
            RawDataMessage(
                `Inst-ID` = timOperation.toString(),
                `M-Dom` = logInfoConfig.homeFQDN,
                Res = statusCode.value,
                sizeIn = request.contentLength(),
                sizeOut = responseLength,
                profOID = logInfoConfig.professionId,
                tID = logInfoConfig.telematikId,
                `UA-A` = "n/a",
                `UA-OS` = "n/a",
                `UA-P` = "n/a",
                `UA-OS-VERSION` = "n/a",
                `UA-PTV` = "n/a",
                `UA-PV` = "n/a"
            )
        }
    }

    override suspend fun clientRawDataForward(requestHeaders: Headers, responseCode: Int, duration: Long, timOperation: Operation, sizeOut: Int) {
        RawDataMetaData(
            start = Clock.System.now(),
            durationInMs = duration,
            message = createClientRawData(requestHeaders, responseCode, timOperation, sizeOut),
            operation = timOperation,
            status = responseCode
        ).let { sendMessageLog(it) }
    }

    private fun createClientRawData(requestHeaders: Headers, responseCode: Int, timOperation: Operation, sizeOut: Int): RawDataMessage {
        return if (requestHeaders["useragent"] != null) {
            val logInfoUserAgent = Json.decodeFromString<LogInfoUserAgentHeader>(requestHeaders["useragent"]!!)
            RawDataMessage(
                `Inst-ID` = timOperation.toString(),
                `M-Dom` = logInfoConfig.homeFQDN,
                Res = responseCode,
                sizeIn = if (requestHeaders[HttpHeaders.ContentLength] != null) requestHeaders[HttpHeaders.ContentLength]!!.toLong() else 0,
                sizeOut = sizeOut.toLong(),
                profOID = logInfoConfig.professionId,
                tID = logInfoConfig.telematikId,
                `UA-A` = logInfoUserAgent.Auspraegung,
                `UA-OS` = logInfoUserAgent.OS,
                `UA-P` = logInfoUserAgent.Plattform,
                `UA-OS-VERSION` = logInfoUserAgent.`OS-Version`,
                `UA-PTV` = logInfoUserAgent.Produkttypversion,
                `UA-PV` = logInfoUserAgent.Produktversion
            )
        } else {
            RawDataMessage(
                `Inst-ID` = timOperation.toString(),
                `M-Dom` = logInfoConfig.homeFQDN,
                Res = responseCode,
                sizeIn = if (requestHeaders[HttpHeaders.ContentLength] != null) requestHeaders[HttpHeaders.ContentLength]!!.toLong() else 0,
                sizeOut = sizeOut.toLong(),
                profOID = logInfoConfig.professionId,
                tID = logInfoConfig.telematikId,
                `UA-A` = "n/a",
                `UA-OS` = "n/a",
                `UA-P` = "n/a",
                `UA-OS-VERSION` = "n/a",
                `UA-PTV` = "n/a",
                `UA-PV` = "n/a"
            )
        }
    }

    private suspend fun sendMessageLog(infoLogMessage: RawDataMetaData) {
        httpClient.post(logInfoConfig.url) {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(infoLogMessage))
        }
    }
}
