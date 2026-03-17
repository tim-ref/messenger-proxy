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
package de.akquinet.tim.proxy.federation

import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.TimAuthorizationCheckConcept
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.federation.model.route.InviteRequestBodyCommon
import de.akquinet.tim.proxy.federation.model.route.InviteV1
import de.akquinet.tim.proxy.federation.model.route.SendJoinV1
import de.akquinet.tim.proxy.forwardRequest
import de.akquinet.tim.proxy.mergeToUrl
import de.akquinet.tim.proxy.rawdata.RawDataService
import de.akquinet.tim.proxy.rawdata.model.Operation
import de.akquinet.tim.proxy.validation.SynapseAdminAPIValidator
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.api.server.matrixEndpointResource
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.serverserverapi.model.discovery.GetWellKnown
import net.folivo.trixnity.serverserverapi.model.federation.GetEvent
import net.folivo.trixnity.serverserverapi.model.federation.Invite
import net.folivo.trixnity.serverserverapi.model.federation.MakeJoin
import net.folivo.trixnity.serverserverapi.model.federation.SendJoin

private val logger = KotlinLogging.logger {}

interface InboundFederationRoutes : FederationRoutes

class InboundFederationRoutesImpl(
  private val config: ProxyConfiguration.InboundProxyConfiguration,
  private val httpClient: HttpClient,
  private val rawDataService: RawDataService,
  private val timAuthorizationCheckConfiguration:
    ProxyConfiguration.TimAuthorizationCheckConfiguration,
  private val berechtigungsstufeEinsService: BerechtigungsstufeEinsService,
  private val synapseAdminAPIValidator: SynapseAdminAPIValidator,
) : InboundFederationRoutes, FederationRoutesImpl(httpClient) {

  private val tolerantJson = Json { ignoreUnknownKeys = true }

  override fun ApplicationRequest.getDestinationUrl(): Url = uri.mergeToUrl(config.homeserverUrl)

  override fun Route.serverServerRawDataRoutes() {
    forwardWithRawData<GetEvent>(Operation.MP_EXCHANGE_EVENT_OUTSIDE_ORGANISATION_RECEIVER)
    matrixEndpointResource<GetWellKnown> {
      call.request.headers[HttpHeaders.Host]
        ?.let { Destination.from(it) }
        ?.host
        ?.let { hostname ->
          call.respond(
            HttpStatusCode.OK,
            GetWellKnown.Response(server = "$hostname:${config.synapsePort}"),
          )
        }
        ?: throw MatrixServerException(
          HttpStatusCode.BadRequest,
          ErrorResponse.MissingParam("Host header not found in request"),
        )
    }
    // enforceDomainList is used to turn off the invitation check mechanism ("Berechtigungsprüfung
    // Stufe 3") for Sytest
    // TODO https://jira.spree.de/browse/TIMREF-1772: a better alternativ to turning off the feature
    // completely would be to start a Nginx Server that mocks
    // the interface "/vzd/invite" of the registration service
    if (!config.enforceDomainList) {
      logger.info("Pass invite permission check, cause sytest is running")
      forwardWithRawData<Invite>(Operation.MP_INVITE_OUTSIDE_ORGANISATION_INVITE_RECEIVER)
      forwardWithRawData<InviteV1>(Operation.MP_INVITE_OUTSIDE_ORGANISATION_INVITE_RECEIVER)
    } else {
      val checkInvitePermission =
        timAuthorizationCheckConfiguration.concept == TimAuthorizationCheckConcept.PROXY
      matrixEndpointResource<Invite> {
        handleInvite(call, checkInvitePermission = checkInvitePermission)
      }
      matrixEndpointResource<InviteV1> {
        handleInvite(call, checkInvitePermission = checkInvitePermission)
      }
    }

    forwardEndpoint<MakeJoin>()
    forwardEndpoint<SendJoin>()
    forwardEndpoint<SendJoinV1>()
  }

  private suspend fun handleInvite(call: ApplicationCall, checkInvitePermission: Boolean) {
    val requestBody = call.receiveText()
    val request = tolerantJson.decodeFromString<InviteRequestBodyCommon>(requestBody)
    val (sender, invitedUser, content) = request.event
    val membership = content.membership

    suspend fun forwardRequest() {
      forwardRequest(call, httpClient, call.request.getDestinationUrl(), requestBody.toByteArray())
        .let {
          rawDataService.serverRawDataForward(
            request = it.first,
            response = it.second,
            duration = it.third,
            timOperation = Operation.MP_INVITE_OUTSIDE_ORGANISATION_INVITE_RECEIVER,
            sizeOut = it.fourth,
          )
        }
    }

    if (config.enforceDomainList) {
      checkFederatedDomain(invitedUser.domain)
      checkFederatedDomain(sender.domain)

      if (checkInvitePermission) {
        if (
          membership == "invite" &&
            synapseAdminAPIValidator.validateInvitePermission(invitedUser.full, sender).isRight()
        ) {
          forwardRequest()
        } else {
          throw MatrixServerException(
            HttpStatusCode.Forbidden,
            ErrorResponse.Forbidden("Cannot invite this user"),
          )
        }
      } else {
        forwardRequest()
      }
    }
  }

  private fun checkFederatedDomain(domain: String) {
    if (berechtigungsstufeEinsService.isUnfederatedDomain(domain)) {
      throw unfederatedDomainException(domain)
    }
  }

  private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardEndpoint() =
    matrixEndpointResource<ENDPOINT> {
      forwardRequest(call, httpClient, call.request.getDestinationUrl(), null)
    }

  private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardWithRawData(
    timOperation: Operation
  ) =
    matrixEndpointResource<ENDPOINT> {
      forwardRequest(call, httpClient, call.request.getDestinationUrl(), null).let {
        rawDataService.serverRawDataForward(it.first, it.second, it.third, timOperation, it.fourth)
      }
    }
}
