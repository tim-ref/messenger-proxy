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
package de.akquinet.tim.proxy.federation

import de.akquinet.tim.proxy.client.model.route.GetServerKeyById
import de.akquinet.tim.proxy.federation.model.route.CasProxyValidate
import de.akquinet.tim.proxy.federation.model.route.CheckPubkeyValidity
import de.akquinet.tim.proxy.federation.model.route.DownloadMediaR0
import de.akquinet.tim.proxy.federation.model.route.DownloadThumbnailR0
import de.akquinet.tim.proxy.federation.model.route.QueryServerKeyByServerAndId
import de.akquinet.tim.proxy.federation.model.route.SendJoinV1
import de.akquinet.tim.proxy.federation.model.route.SendLeaveV1
import de.akquinet.tim.proxy.forwardRequest
import de.akquinet.tim.proxy.forwardRequestWithoutCallReceival
import io.ktor.client.HttpClient
import io.ktor.http.Url
import io.ktor.server.application.call
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.routing.Route
import net.folivo.trixnity.api.server.matrixEndpointResource
import net.folivo.trixnity.clientserverapi.model.media.DownloadMedia
import net.folivo.trixnity.clientserverapi.model.media.DownloadThumbnail
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.serverserverapi.model.discovery.GetServerKeys
import net.folivo.trixnity.serverserverapi.model.discovery.GetServerVersion
import net.folivo.trixnity.serverserverapi.model.discovery.QueryServerKeys
import net.folivo.trixnity.serverserverapi.model.discovery.QueryServerKeysByServer
import net.folivo.trixnity.serverserverapi.model.federation.BackfillRoom
import net.folivo.trixnity.serverserverapi.model.federation.ClaimKeys
import net.folivo.trixnity.serverserverapi.model.federation.ExchangeThirdPartyInvite
import net.folivo.trixnity.serverserverapi.model.federation.GetDevices
import net.folivo.trixnity.serverserverapi.model.federation.GetEventAuthChain
import net.folivo.trixnity.serverserverapi.model.federation.GetHierarchy
import net.folivo.trixnity.serverserverapi.model.federation.GetKeys
import net.folivo.trixnity.serverserverapi.model.federation.GetMissingEvents
import net.folivo.trixnity.serverserverapi.model.federation.GetPublicRooms
import net.folivo.trixnity.serverserverapi.model.federation.GetPublicRoomsWithFilter
import net.folivo.trixnity.serverserverapi.model.federation.GetState
import net.folivo.trixnity.serverserverapi.model.federation.GetStateIds
import net.folivo.trixnity.serverserverapi.model.federation.MakeJoin
import net.folivo.trixnity.serverserverapi.model.federation.MakeKnock
import net.folivo.trixnity.serverserverapi.model.federation.MakeLeave
import net.folivo.trixnity.serverserverapi.model.federation.OnBindThirdPid
import net.folivo.trixnity.serverserverapi.model.federation.QueryDirectory
import net.folivo.trixnity.serverserverapi.model.federation.QueryProfile
import net.folivo.trixnity.serverserverapi.model.federation.SendJoin
import net.folivo.trixnity.serverserverapi.model.federation.SendKnock
import net.folivo.trixnity.serverserverapi.model.federation.SendLeave
import net.folivo.trixnity.serverserverapi.model.federation.SendTransaction

interface FederationRoutes {
    fun Route.serverServerApiRoutes()
    fun Route.serverServerRawDataRoutes()
}

abstract class FederationRoutesImpl(
    private val httpClient: HttpClient
) : FederationRoutes {
    override fun Route.serverServerApiRoutes() {
        forwardEndpoint<GetServerVersion>()
        forwardEndpoint<GetServerKeys>()
        forwardEndpoint<GetServerKeyById>()
        forwardEndpoint<QueryServerKeys>()
        forwardEndpoint<QueryServerKeysByServer>()
        forwardEndpoint<QueryServerKeyByServerAndId>()
        forwardEndpoint<CheckPubkeyValidity>()
        forwardEndpoint<SendTransaction>()
        forwardEndpoint<GetEventAuthChain>()
        forwardEndpoint<BackfillRoom>()
        forwardEndpoint<GetMissingEvents>()
        forwardEndpoint<GetState>()
        forwardEndpoint<GetStateIds>()
        forwardEndpoint<MakeJoin>()
        forwardEndpoint<SendJoin>()
        forwardEndpoint<SendJoinV1>()
        forwardEndpoint<MakeKnock>()
        forwardEndpoint<SendKnock>()
        forwardEndpoint<MakeLeave>()
        forwardEndpoint<SendLeave>()
        forwardEndpoint<SendLeaveV1>()
        forwardEndpoint<OnBindThirdPid>()
        forwardEndpoint<ExchangeThirdPartyInvite>()
        forwardEndpoint<GetPublicRooms>()
        forwardEndpoint<GetPublicRoomsWithFilter>()
        forwardEndpoint<GetHierarchy>()
        forwardEndpoint<QueryDirectory>()
        forwardEndpoint<QueryProfile>()
        forwardEndpoint<GetDevices>()
        forwardEndpoint<ClaimKeys>()
        forwardEndpoint<GetKeys>()

        // media
        forwardEndpointWithoutCallRecieval<DownloadMedia>()
        forwardEndpointWithoutCallRecieval<DownloadMediaR0>()
        forwardEndpoint<DownloadThumbnail>()
        forwardEndpoint<DownloadThumbnailR0>()

        // cas
        forwardEndpoint<CasProxyValidate>()
    }

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardEndpoint() =
        matrixEndpointResource<ENDPOINT> {
            forwardRequest(call, httpClient, call.request.getDestinationUrl(), null)
        }

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardEndpointWithoutCallRecieval() {
        matrixEndpointResource<ENDPOINT> {
            forwardRequestWithoutCallReceival(call, httpClient, call.request.getDestinationUrl())
        }
    }

    internal abstract fun ApplicationRequest.getDestinationUrl(): Url
}
