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

import de.akquinet.tim.proxy.*
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.contactmgmt.database.ContactManagementService
import de.akquinet.tim.proxy.extensions.toUriFormat
import de.akquinet.tim.proxy.federation.model.route.InviteRequestBodyCommon
import de.akquinet.tim.proxy.federation.model.route.InviteV1
import de.akquinet.tim.proxy.federation.model.route.SendJoinV1
import de.akquinet.tim.proxy.rawdata.RawDataService
import de.akquinet.tim.proxy.rawdata.model.Operation
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.api.server.matrixEndpointResource
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.JoinRulesEventContent
import net.folivo.trixnity.serverserverapi.model.discovery.GetWellKnown
import net.folivo.trixnity.serverserverapi.model.federation.*

private val kLog = KotlinLogging.logger { }

interface InboundFederationRoutes : FederationRoutes

class InboundFederationRoutesImpl(
    private val config: ProxyConfiguration.InboundProxyConfiguration,
    private val httpClient: HttpClient,
    private val rawDataService: RawDataService,
    private val contactManagementService: ContactManagementService,
    private val vzdPublicIDCheck: VZDPublicIDCheck,
    private val timAuthorizationCheckConfiguration: ProxyConfiguration.TimAuthorizationCheckConfiguration,
    private val berechtigungsstufeEinsService: BerechtigungsstufeEinsService,
) : InboundFederationRoutes, FederationRoutesImpl(httpClient) {

    private val tolerantJson = Json {
        ignoreUnknownKeys = true
    }

    override fun ApplicationRequest.getDestinationUrl(): Url = uri.mergeToUrl(config.homeserverUrl)

    override fun Route.serverServerRawDataRoutes() {
        forwardWithRawData<GetEvent>(Operation.MP_EXCHANGE_EVENT_OUTSIDE_ORGANISATION_RECEIVER)
        matrixEndpointResource<GetWellKnown> {
            call.request.headers[HttpHeaders.Host]?.let { Destination.from(it) }?.host?.let { hostname ->
                call.respond(HttpStatusCode.OK, GetWellKnown.Response(server = "$hostname:${config.synapsePort}"))
            } ?: throw MatrixServerException(
                HttpStatusCode.BadRequest, ErrorResponse.MissingParam("Host header not found in request")
            )
        }
        // enforceDomainList is used to turn off the invitation check mechanism ("Berechtigungsprüfung Stufe 3") for Sytest
        // TODO https://jira.spree.de/browse/TIMREF-1772: a better alternativ to turning off the feature completely would be to start a Nginx Server that mocks
        // the interface "/vzd/invite" of the registration service
        if (!config.enforceDomainList) {
            kLog.info("Pass invite permission check, cause sytest is running")
            forwardWithRawData<Invite>(Operation.MP_INVITE_OUTSIDE_ORGANISATION_INVITE_RECEIVER)
            forwardWithRawData<InviteV1>(Operation.MP_INVITE_OUTSIDE_ORGANISATION_INVITE_RECEIVER)
        } else {
            // AFO_25046 enforce invite permission check on client
            val doBerechtigungscheck = timAuthorizationCheckConfiguration.concept == TimAuthorizationCheckConcept.PROXY
            matrixEndpointResource<Invite> {
                handleInvite(call, checkBerechtigungsstufe23 = doBerechtigungscheck)
            }
            matrixEndpointResource<InviteV1> {
                handleInvite(call, checkBerechtigungsstufe23 = doBerechtigungscheck)
            }
        }

        matrixEndpointResource<MakeJoin> {
            handleJoinRoom(call)
        }

        matrixEndpointResource<SendJoin> {
            handleJoinRoom(call)
        }

        // SendJoinV1 is deprecated and this call should be removed in future versions
        matrixEndpointResource<SendJoinV1> {
            handleJoinRoom(call)
        }

    }

    private suspend fun handleJoinRoom(call: ApplicationCall) {
        val roomId = call.parameters["roomId"]?.let { RoomId(it) }

        val response = httpClient.get("${config.homeserverUrl}/_matrix/client/v3/publicRooms")

        if (response.status == HttpStatusCode.OK) {
            val body = tolerantJson.decodeFromString<GetPublicRoomsResponse>(response.bodyAsText())

            val toJoinedRoom = body.chunk.find { c -> c.roomId == roomId }
            if (toJoinedRoom?.joinRule == JoinRulesEventContent.JoinRule.Public) {
                throw MatrixServerException(
                    HttpStatusCode.Forbidden,
                    ErrorResponse.Forbidden("Cannot join public rooms owned by other home servers")
                )
            }
        }

        forwardRequest(call, httpClient, call.request.getDestinationUrl(), null)
    }

    private suspend fun handleInvite(call: ApplicationCall, checkBerechtigungsstufe23: Boolean) {
        val requestBody = call.receiveText()
        val request = tolerantJson.decodeFromString<InviteRequestBodyCommon>(requestBody)
        val (sender, invitedUser, content) = request.event
        val membership = content.membership

        if (config.enforceDomainList) {
            checkFederatedDomain(invitedUser.domain)
            checkFederatedDomain(sender.domain)
        }

        suspend fun forwardRequest() {
            forwardRequest(
                call, httpClient, call.request.getDestinationUrl(), requestBody.toByteArray()
            ).let {
                rawDataService.serverRawDataForward(
                    request = it.first,
                    response = it.second,
                    duration = it.third,
                    timOperation = Operation.MP_INVITE_OUTSIDE_ORGANISATION_INVITE_RECEIVER,
                    sizeOut = it.fourth
                )
            }
        }

        if (checkBerechtigungsstufe23) {
            if (membership == "invite" && isInviteAllowed(sender, invitedUser)) {
                forwardRequest()
            } else {
                throw MatrixServerException(
                    HttpStatusCode.Forbidden,
                    ErrorResponse.Forbidden("can not invite this user")
                )
            }
        } else {
            forwardRequest()
        }
    }

    private fun checkFederatedDomain(domain: String) {
        if (berechtigungsstufeEinsService.isUnfederatedDomain(domain)) {
            throw unfederatedDomainException(domain)
        }
    }

    //  Berechtigungsstufe 2 & 3
    private suspend fun isInviteAllowed(inviter: UserId, invitedUser: UserId): Boolean {
        if (contactManagementService.getContact(invitedUser.full, inviter.full) != null) {
            return true
        }
        return vzdPublicIDCheck.areMXIDsPublic(
            invited = invitedUser.toUriFormat().full, inviter = inviter.toUriFormat().full
        )
    }

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardWithRawData(timOperation: Operation) =
        matrixEndpointResource<ENDPOINT> {
            forwardRequest(call, httpClient, call.request.getDestinationUrl(), null).let {
                rawDataService.serverRawDataForward(it.first, it.second, it.third, timOperation, it.fourth)
            }
        }
}
