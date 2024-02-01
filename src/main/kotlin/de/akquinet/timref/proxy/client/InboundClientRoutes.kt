/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.client

import de.akquinet.timref.proxy.ProxyConfiguration
import de.akquinet.timref.proxy.client.model.route.RoomJoin
import de.akquinet.timref.proxy.client.model.route.SsoCallback
import de.akquinet.timref.proxy.client.model.route.SsoLogin
import de.akquinet.timref.proxy.client.model.route.SsoLoginOIDC
import de.akquinet.timref.proxy.forwardRedirect
import de.akquinet.timref.proxy.forwardRequest
import de.akquinet.timref.proxy.forwardRequestWithoutCallReceival
import de.akquinet.timref.proxy.mergeToUrl
import de.akquinet.timref.proxy.rawdata.RawDataService
import de.akquinet.timref.proxy.rawdata.model.Operation
import io.ktor.client.HttpClient
import io.ktor.http.Url
import io.ktor.server.application.call
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.host
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.routing.Route
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import net.folivo.trixnity.api.server.matrixEndpointResource
import net.folivo.trixnity.clientserverapi.model.authentication.AddThirdPartyIdentifiers
import net.folivo.trixnity.clientserverapi.model.authentication.BindThirdPartyIdentifiers
import net.folivo.trixnity.clientserverapi.model.authentication.ChangePassword
import net.folivo.trixnity.clientserverapi.model.authentication.DeactivateAccount
import net.folivo.trixnity.clientserverapi.model.authentication.DeleteThirdPartyIdentifiers
import net.folivo.trixnity.clientserverapi.model.authentication.GetEmailRequestTokenForPassword
import net.folivo.trixnity.clientserverapi.model.authentication.GetEmailRequestTokenForRegistration
import net.folivo.trixnity.clientserverapi.model.authentication.GetLoginTypes
import net.folivo.trixnity.clientserverapi.model.authentication.GetMsisdnRequestTokenForPassword
import net.folivo.trixnity.clientserverapi.model.authentication.GetMsisdnRequestTokenForRegistration
import net.folivo.trixnity.clientserverapi.model.authentication.GetOIDCRequestToken
import net.folivo.trixnity.clientserverapi.model.authentication.GetThirdPartyIdentifiers
import net.folivo.trixnity.clientserverapi.model.authentication.IsRegistrationTokenValid
import net.folivo.trixnity.clientserverapi.model.authentication.IsUsernameAvailable
import net.folivo.trixnity.clientserverapi.model.authentication.Login
import net.folivo.trixnity.clientserverapi.model.authentication.Logout
import net.folivo.trixnity.clientserverapi.model.authentication.LogoutAll
import net.folivo.trixnity.clientserverapi.model.authentication.Refresh
import net.folivo.trixnity.clientserverapi.model.authentication.Register
import net.folivo.trixnity.clientserverapi.model.authentication.UnbindThirdPartyIdentifiers
import net.folivo.trixnity.clientserverapi.model.authentication.WhoAmI
import net.folivo.trixnity.clientserverapi.model.devices.DeleteDevice
import net.folivo.trixnity.clientserverapi.model.devices.DeleteDevices
import net.folivo.trixnity.clientserverapi.model.devices.GetDevice
import net.folivo.trixnity.clientserverapi.model.devices.GetDevices
import net.folivo.trixnity.clientserverapi.model.devices.UpdateDevice
import net.folivo.trixnity.clientserverapi.model.discovery.GetWellKnown
import net.folivo.trixnity.clientserverapi.model.keys.AddSignatures
import net.folivo.trixnity.clientserverapi.model.keys.ClaimKeys
import net.folivo.trixnity.clientserverapi.model.keys.DeleteRoomKeyBackup
import net.folivo.trixnity.clientserverapi.model.keys.DeleteRoomKeyBackupData
import net.folivo.trixnity.clientserverapi.model.keys.DeleteRoomKeyBackupVersion
import net.folivo.trixnity.clientserverapi.model.keys.DeleteRoomsKeyBackup
import net.folivo.trixnity.clientserverapi.model.keys.GetKeyChanges
import net.folivo.trixnity.clientserverapi.model.keys.GetKeys
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeyBackup
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeyBackupData
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeyBackupVersion
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeyBackupVersionByVersion
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomsKeyBackup
import net.folivo.trixnity.clientserverapi.model.keys.SetCrossSigningKeys
import net.folivo.trixnity.clientserverapi.model.keys.SetKeys
import net.folivo.trixnity.clientserverapi.model.keys.SetRoomKeyBackup
import net.folivo.trixnity.clientserverapi.model.keys.SetRoomKeyBackupData
import net.folivo.trixnity.clientserverapi.model.keys.SetRoomKeyBackupVersion
import net.folivo.trixnity.clientserverapi.model.keys.SetRoomKeyBackupVersionByVersion
import net.folivo.trixnity.clientserverapi.model.keys.SetRoomsKeyBackup
import net.folivo.trixnity.clientserverapi.model.media.DownloadMedia
import net.folivo.trixnity.clientserverapi.model.media.DownloadThumbnail
import net.folivo.trixnity.clientserverapi.model.media.GetMediaConfig
import net.folivo.trixnity.clientserverapi.model.media.GetUrlPreview
import net.folivo.trixnity.clientserverapi.model.media.UploadMedia
import net.folivo.trixnity.clientserverapi.model.push.DeletePushRule
import net.folivo.trixnity.clientserverapi.model.push.GetNotifications
import net.folivo.trixnity.clientserverapi.model.push.GetPushRule
import net.folivo.trixnity.clientserverapi.model.push.GetPushRuleActions
import net.folivo.trixnity.clientserverapi.model.push.GetPushRuleEnabled
import net.folivo.trixnity.clientserverapi.model.push.GetPushRules
import net.folivo.trixnity.clientserverapi.model.push.GetPushers
import net.folivo.trixnity.clientserverapi.model.push.SetPushRule
import net.folivo.trixnity.clientserverapi.model.push.SetPushRuleActions
import net.folivo.trixnity.clientserverapi.model.push.SetPushRuleEnabled
import net.folivo.trixnity.clientserverapi.model.push.SetPushers
import net.folivo.trixnity.clientserverapi.model.rooms.BanUser
import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.clientserverapi.model.rooms.DeleteRoomAlias
import net.folivo.trixnity.clientserverapi.model.rooms.DeleteRoomTag
import net.folivo.trixnity.clientserverapi.model.rooms.ForgetRoom
import net.folivo.trixnity.clientserverapi.model.rooms.GetDirectoryVisibility
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvent
import net.folivo.trixnity.clientserverapi.model.rooms.GetEventContext
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.clientserverapi.model.rooms.GetHierarchy
import net.folivo.trixnity.clientserverapi.model.rooms.GetJoinedMembers
import net.folivo.trixnity.clientserverapi.model.rooms.GetJoinedRooms
import net.folivo.trixnity.clientserverapi.model.rooms.GetMembers
import net.folivo.trixnity.clientserverapi.model.rooms.GetPublicRooms
import net.folivo.trixnity.clientserverapi.model.rooms.GetPublicRoomsWithFilter
import net.folivo.trixnity.clientserverapi.model.rooms.GetRelations
import net.folivo.trixnity.clientserverapi.model.rooms.GetRelationsByRelationType
import net.folivo.trixnity.clientserverapi.model.rooms.GetRelationsByRelationTypeAndEventType
import net.folivo.trixnity.clientserverapi.model.rooms.GetRoomAccountData
import net.folivo.trixnity.clientserverapi.model.rooms.GetRoomAlias
import net.folivo.trixnity.clientserverapi.model.rooms.GetRoomAliases
import net.folivo.trixnity.clientserverapi.model.rooms.GetRoomTags
import net.folivo.trixnity.clientserverapi.model.rooms.GetState
import net.folivo.trixnity.clientserverapi.model.rooms.GetStateEvent
import net.folivo.trixnity.clientserverapi.model.rooms.InviteUser
import net.folivo.trixnity.clientserverapi.model.rooms.JoinRoom
import net.folivo.trixnity.clientserverapi.model.rooms.KickUser
import net.folivo.trixnity.clientserverapi.model.rooms.KnockRoom
import net.folivo.trixnity.clientserverapi.model.rooms.LeaveRoom
import net.folivo.trixnity.clientserverapi.model.rooms.RedactEvent
import net.folivo.trixnity.clientserverapi.model.rooms.ReportEvent
import net.folivo.trixnity.clientserverapi.model.rooms.SendMessageEvent
import net.folivo.trixnity.clientserverapi.model.rooms.SendStateEvent
import net.folivo.trixnity.clientserverapi.model.rooms.SetDirectoryVisibility
import net.folivo.trixnity.clientserverapi.model.rooms.SetReadMarkers
import net.folivo.trixnity.clientserverapi.model.rooms.SetReceipt
import net.folivo.trixnity.clientserverapi.model.rooms.SetRoomAccountData
import net.folivo.trixnity.clientserverapi.model.rooms.SetRoomAlias
import net.folivo.trixnity.clientserverapi.model.rooms.SetRoomTag
import net.folivo.trixnity.clientserverapi.model.rooms.SetTyping
import net.folivo.trixnity.clientserverapi.model.rooms.UnbanUser
import net.folivo.trixnity.clientserverapi.model.rooms.UpgradeRoom
import net.folivo.trixnity.clientserverapi.model.server.GetCapabilities
import net.folivo.trixnity.clientserverapi.model.server.GetVersions
import net.folivo.trixnity.clientserverapi.model.server.Search
import net.folivo.trixnity.clientserverapi.model.server.WhoIs
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.users.GetAvatarUrl
import net.folivo.trixnity.clientserverapi.model.users.GetDisplayName
import net.folivo.trixnity.clientserverapi.model.users.GetFilter
import net.folivo.trixnity.clientserverapi.model.users.GetGlobalAccountData
import net.folivo.trixnity.clientserverapi.model.users.GetPresence
import net.folivo.trixnity.clientserverapi.model.users.GetProfile
import net.folivo.trixnity.clientserverapi.model.users.SearchUsers
import net.folivo.trixnity.clientserverapi.model.users.SendToDevice
import net.folivo.trixnity.clientserverapi.model.users.SetAvatarUrl
import net.folivo.trixnity.clientserverapi.model.users.SetDisplayName
import net.folivo.trixnity.clientserverapi.model.users.SetFilter
import net.folivo.trixnity.clientserverapi.model.users.SetGlobalAccountData
import net.folivo.trixnity.clientserverapi.model.users.SetPresence
import net.folivo.trixnity.core.MatrixEndpoint

interface InboundClientRoutes {
    fun Route.clientServerApiRoutes()
}

private val log = KotlinLogging.logger { }

class InboundClientRoutesImpl(
    private val config: ProxyConfiguration.InboundProxyConfiguration,
    private val logConfiguration: ProxyConfiguration.LogInfoConfig,
    private val httpClient: HttpClient,
    private val rawDataService: RawDataService
) : InboundClientRoutes {

    private fun ApplicationRequest.getDestinationUrl(): Url = uri.mergeToUrl(config.homeserverUrl)

    override fun Route.clientServerApiRoutes() {
        // authentication
        forwardEndpoint<WhoAmI>()
        forwardEndpoint<IsRegistrationTokenValid>()
        forwardEndpoint<IsUsernameAvailable>()
        forwardEndpoint<GetEmailRequestTokenForPassword>()
        forwardEndpoint<GetEmailRequestTokenForRegistration>()
        forwardEndpoint<GetMsisdnRequestTokenForPassword>()
        forwardEndpoint<GetMsisdnRequestTokenForRegistration>()
        forwardEndpoint<Register>()
        forwardWithRawData<Login>(Operation.MP_CLIENT_LOGIN_REQUEST_ACCESS_TOKEN)
        forwardWithRawData<GetLoginTypes>(Operation.MP_CLIENT_LOGIN_SUPPORTED_LOGIN_TYPES)
        forwardEndpointWithRedirect<SsoLoginOIDC>()
        forwardEndpointWithRedirect<SsoLogin>()
        forwardEndpointWithRedirect<SsoCallback>()
        forwardWithRawData<GetOIDCRequestToken>(Operation.MP_CLIENT_LOGIN_REQUEST_OPENID_TOKEN)
        forwardEndpoint<Logout>()
        forwardEndpoint<LogoutAll>()
        forwardEndpoint<DeactivateAccount>()
        forwardEndpoint<ChangePassword>()
        forwardEndpoint<GetThirdPartyIdentifiers>()
        forwardEndpoint<AddThirdPartyIdentifiers>()
        forwardEndpoint<BindThirdPartyIdentifiers>()
        forwardEndpoint<DeleteThirdPartyIdentifiers>()
        forwardEndpoint<UnbindThirdPartyIdentifiers>()
        forwardEndpoint<Refresh>()

        // devices
        forwardEndpoint<GetDevices>()
        forwardEndpoint<GetDevice>()
        forwardEndpoint<UpdateDevice>()
        forwardEndpoint<DeleteDevices>()
        forwardEndpoint<DeleteDevice>()

        // discovery
        forwardEndpoint<GetWellKnown>()

        // keys
        forwardEndpoint<SetKeys>()
        forwardEndpoint<GetKeys>()
        forwardEndpoint<ClaimKeys>()
        forwardEndpoint<GetKeyChanges>()
        forwardEndpoint<SetCrossSigningKeys>()
        forwardEndpoint<AddSignatures>()
        forwardEndpoint<GetRoomsKeyBackup>()
        forwardEndpoint<GetRoomKeyBackup>()
        forwardEndpoint<GetRoomKeyBackupData>()
        forwardEndpoint<SetRoomsKeyBackup>()
        forwardEndpoint<SetRoomKeyBackup>()
        forwardEndpoint<SetRoomKeyBackupData>()
        forwardEndpoint<DeleteRoomsKeyBackup>()
        forwardEndpoint<DeleteRoomKeyBackup>()
        forwardEndpoint<DeleteRoomKeyBackupData>()
        forwardEndpoint<GetRoomKeyBackupVersion>()
        forwardEndpoint<GetRoomKeyBackupVersionByVersion>()
        forwardEndpoint<SetRoomKeyBackupVersion>()
        forwardEndpoint<SetRoomKeyBackupVersionByVersion>()
        forwardEndpoint<DeleteRoomKeyBackupVersion>()

        // media
        forwardEndpoint<GetMediaConfig>()
        forwardEndpointWithoutCallRecieval<UploadMedia>()
        forwardEndpointWithoutCallRecieval<DownloadMedia>()
        forwardEndpoint<DownloadThumbnail>()
        forwardEndpoint<GetUrlPreview>()

        // push
        forwardEndpoint<GetPushers>()
        forwardEndpoint<SetPushers>()
        forwardEndpoint<GetNotifications>()
        forwardEndpoint<GetPushRules>()
        forwardEndpoint<GetPushRule>()
        forwardEndpoint<SetPushRule>()
        forwardEndpoint<DeletePushRule>()
        forwardEndpoint<GetPushRuleActions>()
        forwardEndpoint<SetPushRuleActions>()
        forwardEndpoint<GetPushRuleEnabled>()
        forwardEndpoint<SetPushRuleEnabled>()

        // rooms
        forwardWithRawData<GetEvent>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetStateEvent>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetState>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetMembers>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetJoinedMembers>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetEvents>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetRelations>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetRelationsByRelationType>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetRelationsByRelationTypeAndEventType>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<SendStateEvent>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)

        forwardWithRawData<SendMessageEvent>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<RedactEvent>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<CreateRoom>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<SetRoomAlias>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetRoomAlias>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetRoomAliases>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<DeleteRoomAlias>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetJoinedRooms>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)

        matrixEndpointResource<InviteUser> {
            val request = call.receive<JsonObject>()
            val eventJson = request["user_id"]
            checkNotNull(eventJson)
            forwardRequest(call, httpClient, call.request.getDestinationUrl(), request.toString()).let {
                rawDataService.serverRawDataForward(
                    it.first, it.second, it.third,
                    if (eventJson.jsonPrimitive.content.contains(logConfiguration.homeFQDN)) Operation.MP_INVITE_WITHIN_ORGANISATION_INVITE else Operation.MP_INVITE_OUTSIDE_ORGANISATION_INVITE_SENDER,
                    it.fourth
                )
            }

        }


        forwardWithRawData<KickUser>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<BanUser>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<UnbanUser>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        ///_matrix/client/v3/rooms/{roomId}/join
        forwardWithRawData<RoomJoin>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        ///_matrix/client/v3/join/{roomIdOrRoomAliasId}
        forwardWithRawData<JoinRoom>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<KnockRoom>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<ForgetRoom>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<LeaveRoom>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<SetReceipt>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<SetReadMarkers>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<SetTyping>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetRoomAccountData>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<SetRoomAccountData>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetDirectoryVisibility>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<SetDirectoryVisibility>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetPublicRooms>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetPublicRoomsWithFilter>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetRoomTags>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<SetRoomTag>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<DeleteRoomTag>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetEventContext>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<ReportEvent>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<UpgradeRoom>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetHierarchy>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)

        // server
        forwardEndpoint<GetVersions>()
        forwardEndpoint<GetCapabilities>()
        forwardEndpoint<Search>()
        forwardEndpoint<WhoIs>()

        // sync
        forwardEndpoint<Sync>()

        // users
        forwardEndpoint<GetDisplayName>()
        forwardEndpoint<SetDisplayName>()
        forwardEndpoint<GetAvatarUrl>()
        forwardEndpoint<SetAvatarUrl>()
        forwardEndpoint<GetProfile>()
        forwardEndpoint<GetPresence>()
        forwardEndpoint<SetPresence>()
        forwardEndpoint<SendToDevice>()
        forwardEndpoint<GetFilter>()
        forwardEndpoint<SetFilter>()
        forwardEndpoint<GetGlobalAccountData>()
        forwardEndpoint<SetGlobalAccountData>()
        forwardWithRawData<SearchUsers>(Operation.MP_INVITE_WITHIN_ORGANISATION_SEARCH)
    }

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardWithRawData(timOperation: Operation) = matrixEndpointResource<ENDPOINT> {
        forwardRequest(call, httpClient, call.request.uri.mergeToUrl(config.homeserverUrl), null).let {
            rawDataService.clientRawDataForward(
                it.first.headers, it.second.status.value, it.third, timOperation, it.fourth
            )
        }
    }

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardEndpoint() {
        matrixEndpointResource<ENDPOINT> {
            forwardRequest(call, httpClient, call.request.uri.mergeToUrl(config.homeserverUrl), null)
        }
    }

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardEndpointWithRedirect() {
        matrixEndpointResource<ENDPOINT> {
            forwardRedirect(call, call.request.uri.mergeToUrl(config.homeserverUrl), call.request.host())
        }
    }

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardEndpointWithoutCallRecieval() {
        matrixEndpointResource<ENDPOINT> {
            forwardRequestWithoutCallReceival(call, httpClient, call.request.uri.mergeToUrl(config.homeserverUrl))
        }
    }
}
