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
package de.akquinet.tim.proxy.rawdata

import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.rawdata.model.LogInfoUserAgentHeader
import de.akquinet.tim.proxy.rawdata.model.Operation
import de.akquinet.tim.proxy.rawdata.model.RawDataMessage
import de.akquinet.tim.proxy.rawdata.model.RawDataMetaData
import de.akquinet.tim.proxy.rawdata.model.UserAgent
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


interface RawDataService {
    suspend fun serverRawDataForward(
        request: ApplicationRequest,
        response: HttpResponse,
        duration: Long,
        timOperation: Operation,
        sizeOut: Int
    )

    suspend fun contactRawDataForward(
        request: ApplicationRequest,
        statusCode: HttpStatusCode,
        responseLength: Long,
        duration: Long,
        timOperation: Operation
    )

    suspend fun clientRawDataForward(
        requestHeaders: Headers,
        responseCode: Int,
        duration: Long,
        timOperation: Operation,
        sizeOut: Int
    )

}

class RawDataServiceImpl(
    private val logInfoConfig: ProxyConfiguration.LogInfoConfig,
    private val httpClient: HttpClient,
) : RawDataService {
    override suspend fun serverRawDataForward(
        request: ApplicationRequest,
        response: HttpResponse,
        duration: Long,
        timOperation: Operation,
        sizeOut: Int
    ) {
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

    private fun createServerRawData(
        request: ApplicationRequest,
        response: HttpResponse,
        timOperation: Operation,
        sizeOut: Int
    ): RawDataMessage = RawDataMessage(
        instanceId = timOperation.toString() + request.origin.uri,
        matrixDomain = logInfoConfig.homeFQDN,
        responseHttpStatusCode = response.status.value,
        requestContentLength = request.contentLength() ?: 0,
        responseContentLength = sizeOut.toLong(),
        professionOID = logInfoConfig.professionId,
        telematikID = logInfoConfig.telematikId,
        userAgentAuspraegung = UserAgent.Auspraegung.UNKNOWN,
        userAgentOS = "n/a",
        userAgentPlatform = UserAgent.Plattform.UNKNOWN,
        userAgentOSVersion = "n/a",
        userAgentProdukttypversion = "n/a",
        userAgentProduktversion = "n/a"
    )


    override suspend fun contactRawDataForward(
        request: ApplicationRequest,
        statusCode: HttpStatusCode,
        responseLength: Long,
        duration: Long,
        timOperation: Operation
    ) {
        RawDataMetaData(
            start = Clock.System.now(),
            durationInMs = duration,
            message = createContactRawData(request, statusCode, responseLength, timOperation),
            operation = timOperation,
            status = statusCode.value
        ).let { sendMessageLog(it) }
    }

    private fun createContactRawData(
        request: ApplicationRequest,
        statusCode: HttpStatusCode,
        responseLength: Long,
        timOperation: Operation
    ): RawDataMessage =
        request.headers["useragent"]?.let { userAgent ->
            val logInfoUserAgent = Json.decodeFromString<LogInfoUserAgentHeader>(userAgent)
            RawDataMessage(
                instanceId = timOperation.toString(),
                matrixDomain = logInfoConfig.homeFQDN,
                responseHttpStatusCode = statusCode.value,
                requestContentLength = request.contentLength() ?: 0,
                responseContentLength = responseLength,
                professionOID = logInfoConfig.professionId,
                telematikID = logInfoConfig.telematikId,
                userAgentAuspraegung = logInfoUserAgent.auspraegung ?: UserAgent.Auspraegung.UNKNOWN,
                userAgentOS = logInfoUserAgent.operatingSystem ?: "n/a",
                userAgentPlatform = logInfoUserAgent.plattform ?: UserAgent.Plattform.UNKNOWN,
                userAgentOSVersion = logInfoUserAgent.osVersion ?: "n/a",
                userAgentProdukttypversion = logInfoUserAgent.produkttypversion ?: "n/a",
                userAgentProduktversion = logInfoUserAgent.produktversion ?: "n/a"
            )
        } ?: RawDataMessage(
            instanceId = timOperation.toString(),
            matrixDomain = logInfoConfig.homeFQDN,
            professionOID = logInfoConfig.professionId,
            responseHttpStatusCode = statusCode.value,
            requestContentLength = request.contentLength() ?: 0,
            responseContentLength = responseLength,
            telematikID = logInfoConfig.telematikId
        )


    override suspend fun clientRawDataForward(
        requestHeaders: Headers,
        responseCode: Int,
        duration: Long,
        timOperation: Operation,
        sizeOut: Int
    ) {
        sendMessageLog(
            RawDataMetaData(
                start = Clock.System.now(),
                durationInMs = duration,
                message = createClientRawData(requestHeaders, responseCode, timOperation, sizeOut),
                operation = timOperation,
                status = responseCode
            )
        )
    }

    private fun createClientRawData(
        requestHeaders: Headers,
        responseCode: Int,
        timOperation: Operation,
        sizeOut: Int
    ): RawDataMessage =
        requestHeaders["useragent"]?.let { userAgent ->
            val logInfoUserAgent = Json.decodeFromString<LogInfoUserAgentHeader>(userAgent)
            RawDataMessage(
                instanceId = timOperation.toString(),
                matrixDomain = logInfoConfig.homeFQDN,
                responseHttpStatusCode = responseCode,
                requestContentLength = requestHeaders[HttpHeaders.ContentLength]?.toLong() ?: 0,
                responseContentLength = sizeOut.toLong(),
                professionOID = logInfoConfig.professionId,
                telematikID = logInfoConfig.telematikId,
                userAgentAuspraegung = logInfoUserAgent.auspraegung ?: UserAgent.Auspraegung.UNKNOWN,
                userAgentOS = logInfoUserAgent.operatingSystem ?: "n/a",
                userAgentPlatform = logInfoUserAgent.plattform ?: UserAgent.Plattform.UNKNOWN,
                userAgentOSVersion = logInfoUserAgent.osVersion ?: "n/a",
                userAgentProdukttypversion = logInfoUserAgent.produkttypversion ?: "n/a",
                userAgentProduktversion = logInfoUserAgent.produktversion ?: "n/a"
            )
        } ?: RawDataMessage(
            instanceId = timOperation.toString(),
            matrixDomain = logInfoConfig.homeFQDN,
            responseHttpStatusCode = responseCode,
            requestContentLength = requestHeaders[HttpHeaders.ContentLength]?.toLong() ?: 0,
            responseContentLength = sizeOut.toLong(),
            professionOID = logInfoConfig.professionId,
            telematikID = logInfoConfig.telematikId
        )

    private suspend fun sendMessageLog(infoLogMessage: RawDataMetaData) {
        httpClient.post(logInfoConfig.url) {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(infoLogMessage))
        }
    }
}
