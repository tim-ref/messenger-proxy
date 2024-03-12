/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.federation

import de.akquinet.timref.proxy.ProxyConfiguration
import de.akquinet.timref.proxy.VZDPublicIDCheck
import de.akquinet.timref.proxy.contactmgmt.database.ContactManagementService
import de.akquinet.timref.proxy.extensions.toUriFormat
import de.akquinet.timref.proxy.federation.model.route.InviteV1
import de.akquinet.timref.proxy.forwardRequest
import de.akquinet.timref.proxy.mergeToUrl
import de.akquinet.timref.proxy.rawdata.RawDataService
import de.akquinet.timref.proxy.rawdata.model.Operation
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.api.server.matrixEndpointResource
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.serverserverapi.model.discovery.GetWellKnown
import net.folivo.trixnity.serverserverapi.model.federation.GetEvent
import net.folivo.trixnity.serverserverapi.model.federation.Invite

interface InboundFederationRoutes : FederationRoutes

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
        // enforceDomainList is used to turn off the invitation check mechanism ("Berechtigungsprüfung Stufe 3") for Sytest
        // TODO https://jira.spree.de/browse/TIMREF-1772: a better alternativ to turning off the feature completely would be to start a Nginx Server that mocks
        // the interface "/vzd/invite" of the registration service

        if (!config.enforceDomainList) {
            forwardWithRawData<Invite>(Operation.MP_INVITE_OUTSIDE_ORGANISATION_INVITE_RECEIVER)
            forwardWithRawData<InviteV1>(Operation.MP_INVITE_OUTSIDE_ORGANISATION_INVITE_RECEIVER)
        } else {
            matrixEndpointResource<Invite> {
                handleInvite(call)
            }
            matrixEndpointResource<InviteV1> {
                handleInvite(call)
            }
        }
    }

    private suspend fun handleInvite(call: ApplicationCall) {
        val requestBody = call.receive<JsonObject>()
        val eventJson = requestBody["event"]?.jsonObject
        checkNotNull(eventJson)
        val inviter = eventJson["sender"]?.jsonPrimitive?.content?.let(::UserId)
        val invited = eventJson["state_key"]?.jsonPrimitive?.content?.let(::UserId)
        val membership = eventJson["content"]?.jsonObject?.get("membership")?.jsonPrimitive?.content
        if (membership == "invite" && isInviteAllowed(inviter, invited)) {
            forwardRequest(call, httpClient, call.request.getDestinationUrl(), requestBody.toString().toByteArray())
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

    private suspend fun isInviteAllowed(inviter: UserId?, invitedUser: UserId?): Boolean {
        if (inviter != null && invitedUser != null && (contactManagementService.getContact(
                invitedUser.full,
                inviter.full
            ) != null)
        ) {
            return true
        }
        return (inviter != null) &&
                (invitedUser != null) &&
                vzdPublicIDCheck.areMXIDsPublic(
                    invited = invitedUser.toUriFormat().full,
                    inviter = inviter.toUriFormat().full
                )
    }

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardWithRawData(timOperation: Operation) =
        matrixEndpointResource<ENDPOINT> {
            forwardRequest(call, httpClient, call.request.getDestinationUrl(), null).let {
                rawDataService.serverRawDataForward(it.first, it.second, it.third, timOperation, it.fourth)
            }
        }
}
