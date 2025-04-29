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

import de.akquinet.tim.proxy.client.model.route.*
import de.akquinet.tim.proxy.federation.model.route.*
import de.akquinet.tim.proxy.forwardRequest
import de.akquinet.tim.proxy.forwardMediaRequest
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import net.folivo.trixnity.api.server.matrixEndpointResource
import net.folivo.trixnity.clientserverapi.model.media.DownloadMediaLegacy
import net.folivo.trixnity.clientserverapi.model.media.GetMediaConfig
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.serverserverapi.model.discovery.GetServerKeys
import net.folivo.trixnity.serverserverapi.model.discovery.QueryServerKeys
import net.folivo.trixnity.serverserverapi.model.discovery.QueryServerKeysByServer
import net.folivo.trixnity.serverserverapi.model.federation.*

interface FederationRoutes {
    fun Route.serverServerApiRoutes()
    fun Route.serverServerRawDataRoutes()
}

abstract class FederationRoutesImpl(
    private val httpClient: HttpClient
) : FederationRoutes {
    override fun Route.serverServerApiRoutes() {
        // see A_26224
        forwardEndpoint<GetServerKeys>()
        forwardEndpoint<QueryServerKeysByServer>()
        forwardEndpoint<GetServerVersionRequireAuth>()
        forwardEndpoint<QueryServerKeys>()
        forwardEndpoint<CheckPubkeyValidity>()
        forwardEndpoint<SendTransaction>()
        forwardEndpoint<GetEventAuthChain>()
        forwardEndpoint<BackfillRoom>()
        forwardEndpoint<GetMissingEvents>()
        forwardEndpoint<GetState>()
        forwardEndpoint<GetStateIds>()
        forwardEndpoint<MakeKnock>()
        forwardEndpoint<SendKnock>()
        forwardEndpoint<MakeLeave>()
        forwardEndpoint<SendLeave>()
        forwardEndpoint<SendLeaveV1>()
        forwardEndpoint<OnBindThirdPid>()
        forwardEndpoint<ExchangeThirdPartyInvite>()
        forwardEndpoint<GetPublicRoomsWithFilter>()
        forwardEndpoint<GetHierarchy>()
        forwardEndpoint<QueryDirectory>()
        forwardEndpoint<QueryProfile>()
        forwardEndpoint<GetDevices>()
        forwardEndpoint<ClaimKeys>()
        forwardEndpoint<GetKeys>()

        // media
        forwardEndpointWithoutCallReceival<DownloadMedia>()
        forwardEndpointWithoutCallReceival<DownloadMediaWithFilename>()
        @Suppress("DEPRECATION")
        forwardEndpointWithoutCallReceival<DownloadMediaLegacy>()
        @Suppress("DEPRECATION")
        forwardEndpointWithoutCallReceival<DownloadMediaWithFilenameLegacy>()
        forwardEndpointWithoutCallReceival<DownloadMediaR0>()
        // media V1 --> A_26262
        forwardEndpointWithoutCallReceival<DownloadMediaV1>()

//            forwardEndpoint<DownloadThumbnail>()        // Fehlerhaft
        forwardEndpoint<DownloadThumbnailWithOptionalMethod>()

        @Suppress("DEPRECATION")
//            forwardEndpoint<DownloadThumbnailLegacy>()  // Fehlerhaft
        forwardEndpoint<DownloadThumbnailLegacyWithOptionalMethod>()

        forwardEndpoint<DownloadThumbnailR0>()

        forwardEndpoint<DownloadThumbnailV1>()


        forwardEndpoint<GetMediaConfig>()
        // cas
        forwardEndpoint<CasProxyValidate>()

        // see A_26244 resp. TIMREF-2045
        // Since InboundFederationRoutesImpl and OutboundFederationRoutesImpl
        // both inherit FederationRoutesImpl, we lose the crucial information
        // whether the class is of type InboundFederationRoutes or OutboundFederationRoutes.
        // (Because the common supertype is FederationRoutes.)
        // This model seems weird to me, and it should be refactored.

        forwardEndpoint<GetServerKeys>()
        forwardEndpoint<QueryServerKeysByServer>()

        if (this@FederationRoutesImpl is InboundFederationRoutes) {
            forwardEndpoint<GetServerKeyById>()
            forwardEndpoint<QueryServerKeyByServerAndId>()
        }
    }

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardEndpoint() =
        matrixEndpointResource<ENDPOINT> {
            forwardRequest(call, httpClient, call.request.getDestinationUrl(), null)
        }

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardEndpointWithoutCallReceival() {
        matrixEndpointResource<ENDPOINT> {
            forwardMediaRequest(call, httpClient, call.request.getDestinationUrl())
        }
    }

    internal abstract fun ApplicationRequest.getDestinationUrl(): Url
}
