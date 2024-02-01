/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.utils.buildHeaders
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.httpMethod
import io.ktor.server.request.httpVersion
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.util.toByteArray
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val log = KotlinLogging.logger { }

private val apacheClient = HttpClient(Apache5) {
    install(ContentNegotiation) {
        json()
    }
    followRedirects = false
}

suspend fun forwardRequest(
    call: ApplicationCall,
    httpClient: HttpClient,
    destinationUrl: Url,
    bodyJson: String?
): Quadruple<ApplicationRequest, HttpResponse, Long, Int> {
    val start = System.nanoTime()
    val requestBody = bodyJson ?: call.receive<String>()

    val response =
        httpClient.request {
            method = call.request.httpMethod
            url(destinationUrl)
            val requestHeaders = call.request.headers.filterUnsafeHeaders()
            debugLog(call, destinationUrl, requestBody, requestHeaders)
            setBody(object : OutgoingContent.ByteArrayContent() {
                override val headers: Headers = requestHeaders
                override fun bytes(): ByteArray = requestBody.toByteArray()
            })
        }
    createResponse(response, call)
    responseLog(call, destinationUrl, response)

    return Quadruple(call.request, response, (System.nanoTime() - start) / 1000000, response.bodyAsText().length)
}

suspend fun forwardRedirect(
    call: ApplicationCall,
    destinationUrl: Url,
    hostHeader: String,
): Quadruple<ApplicationRequest, HttpResponse, Long, Int> {
    val start = System.nanoTime()
    val response =
        apacheClient.request {
            method = call.request.httpMethod
            url(destinationUrl)
            val requestHeaders = call.request.headers.filterUnsafeHeaders()
            val headers = buildHeaders {
                appendAll(requestHeaders)
                remove(HttpHeaders.Host)
                if (!call.request.httpVersion.contains("2"))
                    append(HttpHeaders.Host, hostHeader)
            }
            debugLog(call, destinationUrl, "Could not log body", headers)
            val requestBody = call.request.receiveChannel().toByteArray()
            if (requestHeaders.isChunkedTransferEncoding) {
                setBody(object : OutgoingContent.WriteChannelContent() {
                    override val headers: Headers = headers.filterUnsafeHeaders()
                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        call.request.receiveChannel().copyAndClose(channel)
                    }
                })
            } else {
                setBody(object : OutgoingContent.ByteArrayContent() {
                    override val headers: Headers = headers.filterUnsafeHeaders()
                    override fun bytes(): ByteArray = requestBody
                })
            }
        }
    createResponse(response, call)
    responseLog(call, destinationUrl, response)

    return Quadruple(call.request, response, (System.nanoTime() - start) / 1000000, response.bodyAsText().length)
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
            val requestBody = call.request.receiveChannel().toByteArray()
            debugLog(call, destinationUrl, "Could not log body", requestHeaders)
            if (requestHeaders.isChunkedTransferEncoding) {
                setBody(object : OutgoingContent.WriteChannelContent() {
                    override val headers: Headers = requestHeaders.filterUnsafeHeaders()
                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        call.request.receiveChannel().copyAndClose(channel)
                    }
                })
            } else {
                setBody(object : OutgoingContent.ByteArrayContent() {
                    override val headers: Headers = requestHeaders.filterUnsafeHeaders()
                    override fun bytes(): ByteArray = requestBody
                })
            }
        }
    createResponse(response, call)
    responseLog(call, destinationUrl, response)

    return Quadruple(call.request, response, (System.nanoTime() - start) / 1000000, response.bodyAsText().length)
}

private suspend fun createResponse(
    response: HttpResponse,
    call: ApplicationCall
) {
    val responseHeaders = response.headers
    if (responseHeaders.isChunkedTransferEncoding) {
        val body = response.bodyAsChannel()
        call.respond(object : OutgoingContent.ReadChannelContent() {
            override val headers: Headers = responseHeaders.filterUnsafeHeaders()
            override val status: HttpStatusCode = response.status
            override fun readFrom(): ByteReadChannel = body
        })
    } else {
        val body = response.bodyAsChannel().toByteArray()
        call.respond(object : OutgoingContent.ByteArrayContent() {
            override val headers: Headers = responseHeaders.filterUnsafeHeaders()
            override val status: HttpStatusCode = response.status
            override fun bytes(): ByteArray = body
        })
    }
}

suspend fun responseLog(call: ApplicationCall, destinationUrl: Url, response: HttpResponse) = runBlocking { // this: CoroutineScope
    launch {
        val responseBody = response.bodyAsText()
        log.debug {
            "Response to Request from ${call.request.uri} to $destinationUrl with body: $responseBody and headers: ${response.headers.entries().map { "${it.key}: ${it.value}" }}"
        }
    }
}

private fun debugLog(call: ApplicationCall, destinationUrl: Url, requestBody: String, requestHeaders: Headers) {
    log.debug {
        "Received ${call.request.httpMethod}-Request with ${call.request.httpVersion} to $destinationUrl with body: $requestBody and headers:" +
                " ${requestHeaders.entries().map { "${it.key}: ${it.value}" }}"
    }
}

private fun Headers.filterUnsafeHeaders() =
    buildHeaders {
        appendAll(this@filterUnsafeHeaders)
        HttpHeaders.UnsafeHeadersList.forEach { remove(it) }
    }
private val Headers.isChunkedTransferEncoding: Boolean
    get() = get(HttpHeaders.TransferEncoding)?.contains("chunked") ?: false
