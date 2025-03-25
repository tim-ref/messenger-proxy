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
package de.akquinet.tim.proxy.federation

import de.akquinet.tim.proxy.federation.model.route.InviteV1
import de.akquinet.tim.proxy.federation.model.route.SendJoinV1
import de.akquinet.tim.proxy.forwardRequest
import de.akquinet.tim.proxy.rawdata.RawDataService
import de.akquinet.tim.proxy.rawdata.model.Operation
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.takeFrom
import io.ktor.server.application.call
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.uri
import io.ktor.server.routing.Route
import net.folivo.trixnity.api.server.matrixEndpointResource
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.serverserverapi.model.discovery.GetWellKnown
import net.folivo.trixnity.serverserverapi.model.federation.GetEvent
import net.folivo.trixnity.serverserverapi.model.federation.Invite
import net.folivo.trixnity.serverserverapi.model.federation.MakeJoin
import net.folivo.trixnity.serverserverapi.model.federation.SendJoin

interface OutboundFederationRoutes : FederationRoutes

class OutboundFederationRoutesImpl(
    private val httpClient: HttpClient,
    private val rawDataService: RawDataService
) : OutboundFederationRoutes, FederationRoutesImpl(httpClient) {
    override fun ApplicationRequest.getDestinationUrl(): Url = URLBuilder().apply {
        takeFrom(uri)
        val destination = headers[HttpHeaders.Host]?.let { Destination.from(it) }
            ?: throw IllegalArgumentException("host header was not set")
        protocol = URLProtocol.HTTPS
        host = destination.host
        port = destination.port
    }.build()

    override fun Route.serverServerRawDataRoutes() {
        forwardWithRawData<Invite>(Operation.MP_INVITE_OUTSIDE_ORGANISATION_INVITE_SENDER)
        forwardWithRawData<InviteV1>(Operation.MP_INVITE_OUTSIDE_ORGANISATION_INVITE_SENDER)
        forwardWithRawData<GetEvent>(Operation.MP_EXCHANGE_EVENT_OUTSIDE_ORGANISATION_SENDER)
        forwardEndpoint<GetWellKnown>()
        forwardEndpoint<SendJoin>()
        forwardEndpoint<SendJoinV1>()
        forwardEndpoint<MakeJoin>()
    }

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardWithRawData(timOperation: Operation) =
        matrixEndpointResource<ENDPOINT> {
            forwardRequest(call, httpClient, call.request.getDestinationUrl(), null).let {
                    rawDataService.serverRawDataForward(it.first, it.second, it.third, timOperation, it.fourth)
            }
        }

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardEndpoint() =
        matrixEndpointResource<ENDPOINT> {
            forwardRequest(call, httpClient, call.request.getDestinationUrl(), null)
        }

}
