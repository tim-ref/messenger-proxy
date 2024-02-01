/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.federation

import de.akquinet.timref.proxy.ProxyConfiguration
import de.akquinet.timref.proxy.VZDPublicIDCheck
import de.akquinet.timref.proxy.contactmgmt.database.ContactManagementService
import de.akquinet.timref.proxy.forwardRequest
import de.akquinet.timref.proxy.mergeToUrl
import de.akquinet.timref.proxy.rawdata.RawDataService
import de.akquinet.timref.proxy.rawdata.model.Operation
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import net.folivo.trixnity.api.server.matrixEndpointResource
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.serverserverapi.model.discovery.GetWellKnown
import net.folivo.trixnity.serverserverapi.model.federation.GetEvent
import net.folivo.trixnity.serverserverapi.model.federation.Invite

interface InboundFederationRoutes : FederationRoutes

private val log = KotlinLogging.logger { }

class InboundFederationRoutesImpl(
    private val config: ProxyConfiguration.InboundProxyConfiguration,
    private val httpClient: HttpClient,
    private val rawDataService: RawDataService,
    private val contactManagementService: ContactManagementService,
    private val vzdPublicIDCheck: VZDPublicIDCheck
) : InboundFederationRoutes, FederationRoutesImpl(httpClient) {
    override fun ApplicationRequest.getDestinationUrl(): Url = uri.mergeToUrl(config.homeserverUrl)

    override fun Route.serverServerRawDataRoutes() {
        forwardWithRawData<GetEvent>(Operation.MP_EXCHANGE_EVENT_OUTSIDE_ORGANISATION_RECEIVER)
        matrixEndpointResource<GetWellKnown> {
            val hostname = call.request.headers[HttpHeaders.Host]?.let { Destination.from(it) }!!.host
            call.respond(HttpStatusCode.OK, GetWellKnown.Response(server = "$hostname:${config.synapsePort}"))
        }
        matrixEndpointResource<Invite> {
            val request = call.receive<JsonObject>()
            val eventJson = request["event"]?.jsonObject
            checkNotNull(eventJson)
            val inviter = eventJson["sender"]?.jsonPrimitive?.content?.let(::UserId)
            val invited = eventJson["state_key"]?.jsonPrimitive?.content?.let(::UserId)
            val membership = eventJson["content"]?.jsonObject?.get("membership")?.jsonPrimitive?.content
            if (membership == "invite" && isInviteAllowed(inviter, invited)) {
                forwardRequest(call, httpClient, call.request.getDestinationUrl(), request.toString())
                    .let {
                        rawDataService.serverRawDataForward(
                            it.first,
                            it.second,
                            it.third,
                            Operation.MP_INVITE_OUTSIDE_ORGANISATION_INVITE_RECEIVER,
                            it.fourth
                        )
                    }
            } else {
                throw MatrixServerException(HttpStatusCode.Forbidden, ErrorResponse.Forbidden("can not invite this user"))
            }
        }

    }

    private suspend fun isInviteAllowed(inviter: UserId?, invitedUser: UserId?): Boolean {
        if (inviter != null && invitedUser != null && (contactManagementService.getContact(invitedUser.full, inviter.full) != null)) {
            return true
        }
        return (inviter != null) && (invitedUser != null) && vzdPublicIDCheck.areMXIDsPublic(invited = invitedUser.full, inviter = inviter.full)
    }

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardWithRawData(timOperation: Operation) =
        matrixEndpointResource<ENDPOINT> {
            forwardRequest(call, httpClient, call.request.getDestinationUrl(), null).let {
                rawDataService.serverRawDataForward(it.first, it.second, it.third, timOperation, it.fourth)
            }
        }
}
