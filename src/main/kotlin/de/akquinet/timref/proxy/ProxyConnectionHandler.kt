/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy

import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.ssl.SslHandler
import mu.KotlinLogging
import de.akquinet.timref.proxy.federation.Destination

private val log = KotlinLogging.logger { }

class ProxyConnectionHandler(
    private val manager: OutboundProxyCertificateManager,
) : SimpleChannelInboundHandler<Any>(false) {

    private var tunneling = false
    private var destination: Destination? = null

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Any?) {
        log.trace { "Reading: $msg" }

        if (msg is HttpRequest) {
            handleHttpMessage(ctx, msg)
        } else if (tunneling) {
            forwardToNextHandler(ctx, msg)
        } else if (msg is LastHttpContent) {
            log.trace { "flush response" }
            ctx.flush()
        } else {
            log.warn { "Dropping message because HTTP object was not an HttpMessage: $msg" }
        }
    }

    private fun handleHttpMessage(ctx: ChannelHandlerContext, httpRequest: HttpRequest) {
        if (httpRequest.decoderResult().isFailure) {
            log.warn { "could not parse message from client: ${httpRequest.decoderResult()}" }
            ctx.closeWithBadRequestResponse("Unable to parse HTTP message")
        } else {
            when (httpRequest.method()) {
                HttpMethod.CONNECT -> {
                    log.trace { "got HTTP CONNECT request: $httpRequest" }
                    val destination = httpRequest.uri()?.let { Destination.from(it) }
                    this.destination = destination

                    if (destination == null) {
                        log.warn { "missing destination in CONNECT request: ${httpRequest.decoderResult()}" }
                        ctx.closeWithBadRequestResponse("there was no destination in CONNECT request")
                        return
                    }
                    val response: FullHttpResponse = DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1,
                        HttpResponseStatus.OK,
                    )
                    HttpUtil.setKeepAlive(response, true)

                    log.trace { "respond with OK from request: $httpRequest" }
                    ctx.write(response)
                        .addListener {
                            log.trace { "enabling mitm encryption" }
                            val sslEngine = manager.impersonatingSslEngine(destination.host)
                            sslEngine.useClientMode = false
                            sslEngine.needClientAuth = false
                            ctx.channel()?.config()?.isAutoRead = true
                            val handler = SslHandler(sslEngine)
                            ctx.pipeline().addFirst("ssl", handler)
                            tunneling = true
                        }
                }

                else -> {
                    log.trace { "got request ${httpRequest.uri()} and inject destination $destination" }
                    destination?.also { httpRequest.headers().set(HttpHeaders.Host, destination) }
                    forwardToNextHandler(ctx, httpRequest)
                }
            }
        }
    }

    private fun ChannelHandlerContext.closeWithBadRequestResponse(message: String) {
        val body = message.toByteArray()
        val response: FullHttpResponse = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.BAD_REQUEST,
            Unpooled.copiedBuffer(body)
        )
        response.headers()[HttpHeaderNames.CONTENT_LENGTH] = body.size
        response.headers()[HttpHeaderNames.CONTENT_TYPE] = ContentType.Text.Plain.toString()
        HttpUtil.setKeepAlive(response, false)
        writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }

    private fun forwardToNextHandler(ctx: ChannelHandlerContext, msg: Any?) {
        log.trace { "forward to next handler: $msg" }
        ctx.fireChannelRead(msg)
    }
}
