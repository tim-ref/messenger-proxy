/*
 * Copyright © 2023 - 2026 akquinet GmbH (https://www.akquinet.de)
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
import de.akquinet.tim.proxy.rawdata.RawDataService
import de.akquinet.tim.proxy.rawdata.model.Operation
import de.akquinet.tim.proxy.streaming.RequestWriter
import de.akquinet.tim.proxy.streaming.StreamingRequestWriter
import de.akquinet.tim.proxy.streaming.StreamingResponseReader
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.httpMethod
import io.ktor.server.request.httpVersion
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writer
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Forwards an incoming request, and relays the destination's response.
 *
 * The response body is streamed from the upstream channel straight to the client. The upstream
 * [HttpResponse] is bound to the `execute { }` block, so its body is released when this function
 * returns, normally or exceptionally.
 */
suspend fun forwardRequest(
  call: ApplicationCall,
  httpClient: HttpClient,
  destinationUrl: Url,
  bodyJson: ByteArray?,
): Quadruple<ApplicationRequest, HttpResponse, Long, Int> {
  val start = System.nanoTime()
  val requestBody: ByteArray = bodyJson ?: call.receive()

  return httpClient
    .prepareRequest {
      method = call.request.httpMethod
      url(destinationUrl)
      val requestHeaders = call.request.headers
      val safeRequestHeaders = requestHeaders.filterUnsafeHeaders()
      logIncomingRequest(call, destinationUrl, requestBody, requestHeaders)
      headers { appendAll(safeRequestHeaders) }
      setBody(requestBody)
    }
    .execute { response ->
      val bodySize = streamResponse(response, call)
      logOutgoingResponse(call, destinationUrl, response, bodySize)
      Quadruple(call.request, response, (System.nanoTime() - start) / 1_000_000, bodySize)
    }
}

suspend fun forwardRequestWithRawData(
  call: ApplicationCall,
  httpClient: HttpClient,
  homeserverUrl: String,
  requestBody: String,
  rawDataService: RawDataService,
  timOperation: Operation = Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION,
) {
  val (request, response, duration, sizeOut) =
    forwardRequest(
      call = call,
      httpClient = httpClient,
      destinationUrl = call.request.uri.mergeToUrl(homeserverUrl),
      bodyJson = requestBody.toByteArray(),
    )
  rawDataService.clientRawDataForward(
    requestHeaders = request.headers,
    responseCode = response.status.value,
    duration = duration,
    timOperation = timOperation,
    sizeOut = sizeOut,
  )
}

/**
 * Forwards an incoming request, and relays the destination's response. Empty 'OK' responses have
 * their body replaced with defaultResponseText.
 */
suspend fun forwardRequestWithDefaultResponse(
  call: ApplicationCall,
  httpClient: HttpClient,
  destinationUrl: Url,
  defaultResponseText: String,
  bodyJson: ByteArray?,
): Quadruple<ApplicationRequest, HttpResponse, Long, Int> {
  val start = System.nanoTime()
  val requestBody: ByteArray = bodyJson ?: call.receive()

  return httpClient
    .prepareRequest {
      method = call.request.httpMethod
      url(destinationUrl)
      val requestHeaders = call.request.headers
      logIncomingRequest(call, destinationUrl, requestBody, requestHeaders)
      headers { appendAll(requestHeaders) }
      setBody(requestBody)
    }
    .execute { homeserverResponse ->
      val bodySize =
        if (homeserverResponse.status == HttpStatusCode.NotFound) {
          call.respondText(
            status = HttpStatusCode.OK,
            contentType = ContentType.Application.Json,
            text = defaultResponseText,
          )
          defaultResponseText.length
        } else {
          streamResponse(homeserverResponse, call)
        }
      logOutgoingResponse(call, destinationUrl, homeserverResponse, bodySize)
      Quadruple(call.request, homeserverResponse, (System.nanoTime() - start) / 1_000_000, bodySize)
    }
}

suspend fun forwardMediaRequest(
  call: ApplicationCall,
  httpClient: HttpClient,
  destinationUrl: Url,
  requestBody: String? = null,
) {
  httpClient
    .prepareRequest {
      method = call.request.httpMethod
      url(destinationUrl)
      requestBody?.let { setBody(RequestWriter(call, it.encodeToByteArray())) }
        ?: setBody(StreamingRequestWriter(call))
    }
    .execute { response ->
      coroutineScope {
        val synapseResponseBody: ByteReadChannel = response.body()
        val writerJob =
          writer(autoFlush = true) {
            while (!synapseResponseBody.isClosedForRead) {
              val packet = synapseResponseBody.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
              channel.writePacket(packet)
            }
          }
        val responseReader = StreamingResponseReader(response, writerJob.channel)
        call.respond(responseReader)
      }
    }
}

/**
 * Streams [response]'s body to the client via [StreamingResponseReader]. Returns the number of body
 * bytes relayed, for raw data size accounting.
 */
private suspend fun streamResponse(response: HttpResponse, call: ApplicationCall): Int =
  coroutineScope {
    val upstream: ByteReadChannel = response.body()
    var bytesStreamed = 0L
    val writerJob =
      writer(autoFlush = true) {
        while (!upstream.isClosedForRead) {
          val packet = upstream.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
          bytesStreamed += packet.remaining
          channel.writePacket(packet)
        }
      }
    call.respond(StreamingResponseReader(response, writerJob.channel))
    writerJob.join()
    bytesStreamed.toInt()
  }

private fun logOutgoingResponse(
  call: ApplicationCall,
  destinationUrl: Url,
  response: HttpResponse,
  bodySize: Int,
) {
  log.debug {
    "Response to Request from ${call.request.uri} \n" +
      "to $destinationUrl \n" +
      "with headers: ${response.headers.entries().map { "${it.key}: ${it.value}" }} \n" +
      "response status: ${response.status.value} body bytes: $bodySize"
  }
}

private fun logIncomingRequest(
  call: ApplicationCall,
  destinationUrl: Url,
  requestBody: ByteArray,
  requestHeaders: Headers,
) {
  log.debug {
    "Received ${call.request.httpMethod.value}-Request with ${call.request.httpVersion} \n" +
      "to $destinationUrl \n" +
      "with headers: ${requestHeaders.entries().map { "${it.key}: ${it.value}" }} \n" +
      "and body: $requestBody"
  }
}
