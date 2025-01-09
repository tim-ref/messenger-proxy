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

package de.akquinet.tim.proxy

import de.akquinet.tim.proxy.extensions.filterUnsafeHeaders
import de.akquinet.tim.proxy.extensions.isChunkedTransferEncoding
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.koin.core.time.measureTimedValue

private val log = KotlinLogging.logger { }
private val chunkedEncodingBodyMessage = "body is empty because of chunkedTransferEncoding".toByteArray()

/**
 * Forwards an incoming request, and relays the destination's response.
 */
suspend fun forwardRequest(
    call: ApplicationCall,
    httpClient: HttpClient,
    destinationUrl: Url,
    bodyJson: ByteArray?
): Quadruple<ApplicationRequest, HttpResponse, Long, Int> {
    val start = System.nanoTime()
    val requestBody: ByteArray = bodyJson ?: call.receive()

    val response =
        httpClient.request {
            method = call.request.httpMethod
            url(destinationUrl)
            val requestHeaders = call.request.headers
            val safeRequestHeaders = requestHeaders.filterUnsafeHeaders()
            logIncomingRequest(call, destinationUrl, requestBody, requestHeaders)
            headers { appendAll(safeRequestHeaders) }
            setBody(requestBody)
        }
    sendResponse(response, call)
    logOutgoingResponse(call, destinationUrl, response)

    return Quadruple(call.request, response, (System.nanoTime() - start) / 1000000, response.bodyAsText().length)
}

/**
 * Forwards an incoming request, and relays the destination's response. Empty
 * 'OK' responses have their body replaced with defaultResponseText.
 */
suspend fun forwardRequestWithDefaultResponse(
    call: ApplicationCall,
    httpClient: HttpClient,
    destinationUrl: Url,
    defaultResponseText: String,
    bodyJson: ByteArray?
): Quadruple<ApplicationRequest, HttpResponse, Long, Int> {
    val (response, elapsedMilliseconds) = measureTimedValue {
        val requestBody: ByteArray = bodyJson ?: call.receive()

        val homeserverResponse =
            httpClient.request {
                method = call.request.httpMethod
                url(destinationUrl)
                val requestHeaders = call.request.headers
                logIncomingRequest(call, destinationUrl, requestBody, requestHeaders)
                headers { appendAll(requestHeaders) }
                setBody(requestBody)
            }

        if (homeserverResponse.status == HttpStatusCode.NotFound) {
            call.respondText(
                status = HttpStatusCode.OK,
                contentType = ContentType.Application.Json,
                text = defaultResponseText,
            )
        } else {
            sendResponse(homeserverResponse, call)
        }
        logOutgoingResponse(call, destinationUrl, homeserverResponse)
        homeserverResponse
    }

    return Quadruple(
        call.request,
        response,
        elapsedMilliseconds.toLong(),
        response.bodyAsText().length
    )
}

suspend fun forwardRequestWithoutCallReceival(
    call: ApplicationCall,
    httpClient: HttpClient,
    destinationUrl: Url
): Quadruple<ApplicationRequest, HttpResponse, Long, Int> {
    val start = System.nanoTime()

    val response =
        httpClient.request {
            method = call.request.httpMethod
            url(destinationUrl)
            val requestHeaders = call.request.headers
            val safeRequestHeaders = requestHeaders.filterUnsafeHeaders()

            if (requestHeaders.isChunkedTransferEncoding) {
                logIncomingRequest(call, destinationUrl, chunkedEncodingBodyMessage, requestHeaders)
                setBody(object : OutgoingContent.WriteChannelContent() {
                    override val headers: Headers = safeRequestHeaders
                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        call.request.receiveChannel().copyAndClose(channel)
                    }
                })
            } else {
                val requestBody = call.request.receiveChannel().toByteArray()
                logIncomingRequest(call, destinationUrl, requestBody, requestHeaders)
                headers { appendAll(safeRequestHeaders) }
                setBody(requestBody)
            }
        }
    sendResponse(response, call)
    logOutgoingResponse(call, destinationUrl, response)

    return Quadruple(call.request, response, (System.nanoTime() - start) / 1000000, response.bodyAsText().length)
}

private suspend fun sendResponse(
    response: HttpResponse,
    call: ApplicationCall
) {
    val responseHeaders = response.headers
    val safeRequestHeaders = responseHeaders.filterUnsafeHeaders()
    if (responseHeaders.isChunkedTransferEncoding) {
        val body = response.bodyAsChannel()
        call.respond(object : OutgoingContent.ReadChannelContent() {
            override val headers: Headers = safeRequestHeaders
            override val status: HttpStatusCode = response.status
            override fun readFrom(): ByteReadChannel = body
        })
    } else {
        val body: ByteArray = response.bodyAsChannel().toByteArray()
        call.response.headers.appendAll(safeRequestHeaders)
        call.respondBytes(body, response.contentType(), response.status)
    }
}

private fun logOutgoingResponse(call: ApplicationCall, destinationUrl: Url, response: HttpResponse) =
    runBlocking { // this: CoroutineScope
        launch {
            val responseBody = response.bodyAsText()
            log.debug {
                "Response to Request from ${call.request.uri} \n" +
                        "to $destinationUrl \n" +
                        "with headers: ${response.headers.entries().map { "${it.key}: ${it.value}" }} \n" +
                        "and body: $responseBody \nresponse status: ${response.status.value}"
            }
        }
    }

private fun logIncomingRequest(
    call: ApplicationCall,
    destinationUrl: Url,
    requestBody: ByteArray,
    requestHeaders: Headers
) {
    log.debug {
        "Received ${call.request.httpMethod.value}-Request with ${call.request.httpVersion} \n" +
                "to $destinationUrl \n" +
                "with headers: ${requestHeaders.entries().map { "${it.key}: ${it.value}" }} \n" +
                "and body: $requestBody"
    }
}

private fun ResponseHeaders.appendAll(headers: Headers) {
    headers.forEach { name, values -> values.forEach { append(name, it) } }
}
