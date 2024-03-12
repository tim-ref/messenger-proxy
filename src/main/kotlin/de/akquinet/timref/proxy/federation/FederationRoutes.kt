/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.federation

import de.akquinet.timref.proxy.federation.model.route.*
import de.akquinet.timref.proxy.forwardRequest
import de.akquinet.timref.proxy.forwardRequestWithoutCallReceival
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import net.folivo.trixnity.api.server.matrixEndpointResource
import net.folivo.trixnity.clientserverapi.model.media.DownloadMedia
import net.folivo.trixnity.clientserverapi.model.media.DownloadThumbnail
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.serverserverapi.model.discovery.*
import net.folivo.trixnity.serverserverapi.model.federation.*

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
