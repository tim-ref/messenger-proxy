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
package de.akquinet.tim.proxy.client

import SSORedirect
import SSORedirectTo
import arrow.core.raise.either
import arrow.core.raise.ensure
import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.ProxyConfiguration.TimAuthorizationCheckConfiguration
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.client.model.route.ChangeVisibilityAppServiceRoom
import de.akquinet.tim.proxy.client.model.route.DownloadMediaWithFilename
import de.akquinet.tim.proxy.client.model.route.DownloadMediaWithFilenameLegacy
import de.akquinet.tim.proxy.client.model.route.DownloadThumbnailLegacyWithOptionalMethod
import de.akquinet.tim.proxy.client.model.route.DownloadThumbnailWithOptionalMethod
import de.akquinet.tim.proxy.client.model.route.EventsR0
import de.akquinet.tim.proxy.client.model.route.GetDirectoryVisibility
import de.akquinet.tim.proxy.client.model.route.GetEmailRequestTokenFor3Pid
import de.akquinet.tim.proxy.client.model.route.GetPublicRooms
import de.akquinet.tim.proxy.client.model.route.GetThirdPartyProtocols
import de.akquinet.tim.proxy.client.model.route.RoomJoin
import de.akquinet.tim.proxy.client.model.route.SsoCallback
import de.akquinet.tim.proxy.client.model.route.account_data.AccountDataType
import de.akquinet.tim.proxy.client.model.route.account_data.asPermissionConfig
import de.akquinet.tim.proxy.client.model.route.account_data.asProPermissionConfig
import de.akquinet.tim.proxy.client.model.route.cas.CasRedirect
import de.akquinet.tim.proxy.client.model.route.cas.CasTicket
import de.akquinet.tim.proxy.client.model.route.pushrules.GetPushRuleWithoutId
import de.akquinet.tim.proxy.client.model.route.pushrules.GetPushRulesForScope
import de.akquinet.tim.proxy.client.model.route.thirdparty.GetLocationFromThirdParty
import de.akquinet.tim.proxy.client.model.route.thirdparty.GetThirdPartyProtocolByName
import de.akquinet.tim.proxy.client.model.route.thirdparty.GetUserFromThirdParty
import de.akquinet.tim.proxy.error.TypeParameterIsMissingFailure
import de.akquinet.tim.proxy.federation.unfederatedDomainException
import de.akquinet.tim.proxy.forwardMediaRequest
import de.akquinet.tim.proxy.forwardRequest
import de.akquinet.tim.proxy.forwardRequestWithDefaultResponse
import de.akquinet.tim.proxy.mergeToUrl
import de.akquinet.tim.proxy.rawdata.RawDataService
import de.akquinet.tim.proxy.rawdata.model.Operation
import de.akquinet.tim.proxy.validation.SendMessageValidationService
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
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
import net.folivo.trixnity.clientserverapi.model.discovery.GetSupport
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
import net.folivo.trixnity.clientserverapi.model.media.DownloadMediaLegacy
import net.folivo.trixnity.clientserverapi.model.media.GetMediaConfig
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
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvent
import net.folivo.trixnity.clientserverapi.model.rooms.GetEventContext
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.clientserverapi.model.rooms.GetHierarchy
import net.folivo.trixnity.clientserverapi.model.rooms.GetJoinedMembers
import net.folivo.trixnity.clientserverapi.model.rooms.GetJoinedRooms
import net.folivo.trixnity.clientserverapi.model.rooms.GetMembers
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
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.UserId

private val kLog = KotlinLogging.logger { }

interface InboundClientRoutes {
    fun Route.clientServerApiRoutes()
    fun Route.openClientServerApiRoutes()
}

class InboundClientRoutesImpl(
    private val config: ProxyConfiguration.InboundProxyConfiguration,
    private val logConfiguration: ProxyConfiguration.LogInfoConfig,
    val timAuthorizationCheckConfiguration: TimAuthorizationCheckConfiguration,
    private val httpClient: HttpClient,
    private val rawDataService: RawDataService,
    private val berechtigungsstufeEinsService: BerechtigungsstufeEinsService,
    private val regServiceConfig: ProxyConfiguration.RegistrationServiceConfiguration,
    private val sendMessageValidationService: SendMessageValidationService,
) : InboundClientRoutes {

    companion object {
        const val ROOM_VERSION = "room_version"
        const val NEW_VERSION = "new_version"
        const val CREATE_ROOM_VERSION = "room_version"
        const val UPGRADE_ROOM_VERSION = "new_version"
        val supportedRoomVersions = setOf("9", "10")
    }

    private fun ApplicationRequest.getDestinationUrl(): Url = uri.mergeToUrl(config.homeserverUrl)

    override fun Route.openClientServerApiRoutes() {
        // TODO @veronika.bertels: so kann das aus meiner Sicht nicht bleiben, weil m.login.sso variabel ist. Behebt aber erstmal das Problem.
        get("/_matrix/client/v3/auth/m.login.sso/fallback/{...}") {
            forwardRequest(
                call = call,
                httpClient = httpClient,
                destinationUrl = call.request.uri.mergeToUrl(config.homeserverUrl),
                bodyJson = null
            )
        }


        // A_26265 - TI-M FD Org-Admin Support
        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#A_26265
        matrixEndpointResource<GetSupport> {
            val serverName = logConfiguration.homeFQDN.split('.').first()

            val url = Url(
                "${regServiceConfig.baseUrl}:${regServiceConfig.servicePort}" +
                        "${regServiceConfig.wellKnownSupportEndpoint}/$serverName"
            )

            kLog.info { "Forward request on ${call.request.path()} to $url" }

            forwardRequestWithDefaultResponse(
                call = call,
                httpClient = httpClient,
                destinationUrl = url,
                defaultResponseText = """{
  "contacts": [
    {
      "email_address": "Referenzimplementierung",
      "matrix_id": "Referenzimplementierung",
      "role": "Referenzimplementierung"
    }
  ],
  "support_page": "Referenzimplementierung"
}""",
                bodyJson = call.receive()
            )
        }

        route("/") {
            if (config.enforceDomainList) {
                install(PathParameterFederationCheck) {
                    service = berechtigungsstufeEinsService
                }
            }

            forwardEndpointWithoutCallReceival<DownloadMedia>()
            forwardEndpointWithoutCallReceival<DownloadMediaWithFilename>()
            @Suppress("DEPRECATION")
            forwardEndpointWithoutCallReceival<DownloadMediaLegacy>()
            @Suppress("DEPRECATION")
            forwardEndpointWithoutCallReceival<DownloadMediaWithFilenameLegacy>()
            @Suppress("DEPRECATION")
//            forwardEndpoint<DownloadThumbnailLegacy>()  // Fehlerhaft
            forwardEndpoint<DownloadThumbnailLegacyWithOptionalMethod>()
//            forwardEndpoint<DownloadThumbnail>()        // Fehlerhaft
            forwardEndpoint<DownloadThumbnailWithOptionalMethod>()
        }
    }

    override fun Route.clientServerApiRoutes() {
        // Berechtigungsstufe 1
        createRoom()
        inviteUser()

        // matrix 1.11
        upgradeRoom()

        // authentication
        authenticationRoutes()

        // devices
        devicesRoutes()

        // discovery
        discoveryRoute()

        // keys
        keyRoutes()

        // media
        mediaRoutes()

        // push
        pushRoutes()

        // rooms
        roomRoutes()

        // server
        serverRoutes()

        // sync
        syncRoutes()

        // users
        usersRoutes()
    }

    private fun Route.upgradeRoom() {
        matrixEndpointResource<UpgradeRoom> {
            val originalRequestBody = call.receiveText()
            val originalRequest = Json.decodeFromString<JsonObject>(originalRequestBody)

            val roomVersion = validateRoomVersion(request = originalRequest, requestParameter = NEW_VERSION)

            val request = JsonObject(originalRequest.plus(UPGRADE_ROOM_VERSION to JsonPrimitive(roomVersion)))
            val requestBody = request.toString()

            forwardRequest(
                call, httpClient, call.request.getDestinationUrl(), requestBody.toByteArray()
            ).let {
                rawDataService.serverRawDataForward(
                    request = it.first,
                    response = it.second,
                    duration = it.third,
                    timOperation = Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION,
                    sizeOut = it.fourth
                )
            }
        }
    }

    private fun Route.createRoom() {
        matrixEndpointResource<CreateRoom> {
            val inviter = call.principal<UserIdPrincipal>()
                ?: throw MatrixServerException(
                    statusCode = HttpStatusCode.Unauthorized,
                    errorResponse = ErrorResponse.Unauthorized("")
                )
            val originalRequestBody = call.receiveText()
            val originalRequest = Json.decodeFromString<JsonObject>(originalRequestBody)

            val roomVersion = validateRoomVersion(request = originalRequest, requestParameter = ROOM_VERSION)

            val request = JsonObject(originalRequest.plus(CREATE_ROOM_VERSION to JsonPrimitive(roomVersion)))
            val requestBody = request.toString()

            val (userId, rawdataOperation) = extractInvitedDetails(request)

            val relevantDomains = userId?.let {
                setOf(it.domain, inviter.userId.domain)
            } ?: setOf(inviter.userId.domain)
            if (config.enforceDomainList) {
                for (domain in relevantDomains) {
                    checkFederatedDomain(domain)
                }
            }

            forwardRequest(
                call, httpClient, call.request.getDestinationUrl(), requestBody.toByteArray()
            ).let {
                rawDataService.serverRawDataForward(
                    request = it.first,
                    response = it.second,
                    duration = it.third,
                    timOperation = rawdataOperation,
                    sizeOut = it.fourth
                )
            }
        }
    }

    /*
        gemSpec_TI-M_Basis_1.1.0 A_26202, A_26203
        https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/latest/#A_26202
        https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/latest/#A_26203
     */
    private fun validateRoomVersion(request: JsonObject, requestParameter: String): String {
        val jsonPrimitive = request[requestParameter]?.jsonPrimitive
        val isString = jsonPrimitive?.isString != false || jsonPrimitive is JsonNull
        val roomVersion = jsonPrimitive?.contentOrNull ?: supportedRoomVersions.last()

        when {
            !isString -> throw MatrixServerException(
                statusCode = HttpStatusCode.BadRequest,
                errorResponse = ErrorResponse.BadJson(
                    "Ungültige Raumversion: Der Wert muss ein String sein."
                )
            )

            roomVersion !in supportedRoomVersions -> throw MatrixServerException(
                statusCode = HttpStatusCode.BadRequest,
                errorResponse = ErrorResponse.UnsupportedRoomVersion(
                    "Ungültige Raumversion: $roomVersion ist keine gültige Raumversion. Es werden nur die Versionen ${supportedRoomVersions.joinToString()} unterstützt."
                )
            )
        }

        return roomVersion
    }


    private fun extractInvitedDetails(request: JsonObject): Pair<UserId?, Operation> {
        val invited = request["invite"]?.jsonArray?.map {
            it.jsonPrimitive.content.let(::UserId)
        }?.toSet() ?: emptySet()

        if (invited.size > 1) {
            throw MatrixServerException(
                statusCode = HttpStatusCode.Forbidden,
                errorResponse = ErrorResponse.Forbidden("Es darf nur maximal ein anderer Teilnehmer eingeladen werden.")
            )
        }

        val rawdataOperation = if (invited.isEmpty()) {
            Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION
        } else {
            if (invited.first().full.contains(logConfiguration.homeFQDN)) {
                Operation.MP_INVITE_WITHIN_ORGANISATION_INVITE
            } else {
                Operation.MP_INVITE_OUTSIDE_ORGANISATION_INVITE_SENDER
            }
        }

        return Pair(invited.firstOrNull(), rawdataOperation)
    }

    private fun Route.inviteUser() {
        matrixEndpointResource<InviteUser> {
            val inviter = call.principal<UserIdPrincipal>()
                ?: throw MatrixServerException(
                    statusCode = HttpStatusCode.Unauthorized,
                    errorResponse = ErrorResponse.Unauthorized("")
                )

            val requestBody = call.receiveText()

            val request = Json.decodeFromString<JsonObject>(requestBody)
            val invited =
                checkNotNull(request["user_id"]?.jsonPrimitive?.content?.let(::UserId)) {
                    "Required value InviteUser.Request.userId is null"
                }

            if (config.enforceDomainList) {
                checkFederatedDomain(invited.domain)
                checkFederatedDomain(inviter.userId.domain)
            }

            forwardRequest(
                call, httpClient, call.request.getDestinationUrl(), requestBody.toByteArray()
            ).let {
                rawDataService.serverRawDataForward(
                    request = it.first,
                    response = it.second,
                    duration = it.third,
                    timOperation = if (invited.full.contains(logConfiguration.homeFQDN)) {
                        Operation.MP_INVITE_WITHIN_ORGANISATION_INVITE
                    } else {
                        Operation.MP_INVITE_OUTSIDE_ORGANISATION_INVITE_SENDER
                    },
                    sizeOut = it.fourth
                )
            }
        }
    }

    private fun checkFederatedDomain(domain: String) {
        if (berechtigungsstufeEinsService.isUnfederatedDomain(domain)) {
            throw unfederatedDomainException(domain)
        }
    }

    private fun Route.authenticationRoutes() {
        forwardEndpoint<WhoAmI>()
        forwardEndpoint<IsRegistrationTokenValid>()
        forwardEndpoint<IsUsernameAvailable>()
        forwardEndpoint<GetEmailRequestTokenForPassword>()
        forwardEndpoint<GetEmailRequestTokenForRegistration>()
        forwardEndpoint<GetMsisdnRequestTokenForPassword>()
        forwardEndpoint<GetMsisdnRequestTokenForRegistration>()
        forwardEndpoint<GetEmailRequestTokenFor3Pid>()
        register()
        forwardWithRawData<Login>(Operation.MP_CLIENT_LOGIN_REQUEST_ACCESS_TOKEN)
        forwardWithRawData<GetLoginTypes>(Operation.MP_CLIENT_LOGIN_SUPPORTED_LOGIN_TYPES)
        forwardEndpoint<SSORedirectTo>()
        forwardEndpoint<SSORedirect>()
        forwardEndpoint<CasRedirect>()
        forwardEndpoint<SsoCallback>()
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
    }

    private fun Route.devicesRoutes() {
        forwardEndpoint<GetDevices>()
        forwardEndpoint<GetDevice>()
        forwardEndpoint<UpdateDevice>()
        forwardEndpoint<DeleteDevices>()
        forwardEndpoint<DeleteDevice>()
    }

    private fun Route.discoveryRoute() {
        forwardEndpoint<GetWellKnown>()
    }


    private fun Route.keyRoutes() {
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
    }


    private fun Route.mediaRoutes() {
        forwardEndpoint<GetMediaConfig>()
        forwardEndpointWithoutCallReceival<UploadMedia>()
    }

    private fun Route.pushRoutes() {
        forwardEndpoint<GetPushers>()
        forwardEndpoint<SetPushers>()
        forwardEndpoint<GetNotifications>()
        forwardEndpoint<GetPushRules>()
        forwardEndpoint<GetPushRulesForScope>()
        forwardEndpoint<GetPushRule>()
        forwardEndpoint<GetPushRuleWithoutId>()
        forwardEndpoint<SetPushRule>()
        forwardEndpoint<DeletePushRule>()
        forwardEndpoint<GetPushRuleActions>()
        forwardEndpoint<SetPushRuleActions>()
        forwardEndpoint<GetPushRuleEnabled>()
        forwardEndpoint<SetPushRuleEnabled>()
    }

    private fun Route.roomRoutes() {
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
        sendMessageEvent()
        forwardWithRawData<RedactEvent>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<SetRoomAlias>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetRoomAlias>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetRoomAliases>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<DeleteRoomAlias>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)
        forwardWithRawData<GetJoinedRooms>(Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION)

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
    }

    private fun Route.serverRoutes() {
        forwardEndpoint<GetVersions>()
        forwardEndpoint<GetCapabilities>()
        forwardEndpoint<Search>()
        forwardEndpoint<WhoIs>()
    }

    private fun Route.syncRoutes() {
        forwardEndpoint<Sync>()
        forwardEndpoint<EventsR0>()
    }


    private fun Route.usersRoutes() {
        forwardEndpoint<GetDisplayName>()
        forwardEndpoint<SetDisplayName>()
        forwardEndpoint<GetAvatarUrl>()
        forwardEndpoint<SetAvatarUrl>()
        forwardEndpoint<GetProfile>()
        forwardEndpoint<GetPresence>()
        setPresence()
        forwardEndpoint<SendToDevice>()
        forwardEndpoint<GetFilter>()
        forwardEndpoint<SetFilter>()
        getGlobalAccountData()
        forwardEndpoint<SetGlobalAccountData>()
        forwardWithRawData<SearchUsers>(Operation.MP_INVITE_WITHIN_ORGANISATION_SEARCH)

        // third party protocols
        forwardEndpoint<GetThirdPartyProtocols>()
        forwardEndpoint<GetThirdPartyProtocolByName>()
        forwardEndpoint<GetUserFromThirdParty>()
        forwardEndpoint<GetLocationFromThirdParty>()

        // app service
        forwardEndpoint<ChangeVisibilityAppServiceRoom>()

        // cas
        forwardEndpoint<CasTicket>()
    }

    private fun Route.sendMessageEvent() {
        matrixEndpointResource<SendMessageEvent> {
            val requestBody = call.receiveText()

            either {
                ensure(call.parameters["type"] != null) { TypeParameterIsMissingFailure }
                val eventType = call.parameters["type"]
                sendMessageValidationService.validateSendMessage(requestBody, eventType).bind()
            }.onRight {
                val (request, response, duration, sizeOut) = forwardRequest(
                    call = call,
                    httpClient = httpClient,
                    destinationUrl = call.request.uri.mergeToUrl(config.homeserverUrl),
                    bodyJson = requestBody.toByteArray()
                )
                rawDataService.clientRawDataForward(
                    requestHeaders = request.headers,
                    responseCode = response.status.value,
                    duration = duration,
                    timOperation = Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION,
                    sizeOut = sizeOut
                )
            }.onLeft { failure ->
                call.respond<ErrorResponse>(failure.httpStatusCode, failure.errorResponse)
            }
        }
    }

    private fun Route.register() {
        matrixEndpointResource<Register> {
            val kind = call.parameters["kind"]
            if (kind == "guest") {
                throw MatrixServerException(
                    HttpStatusCode.Forbidden,
                    ErrorResponse.Forbidden("Guest access is disabled")
                )
            } else {
                forwardRequest(
                    call = call,
                    httpClient = httpClient,
                    destinationUrl = call.request.getDestinationUrl(),
                    bodyJson = null
                )
            }
        }
    }

    private fun Route.setPresence() {
        matrixEndpointResource<SetPresence> {
            val requestBody = call.receiveText()
            val request = Json.decodeFromString<JsonObject>(requestBody)
            val statusMsg = request["status_msg"]?.jsonPrimitive?.content
            val preConditionFailed = statusMsg?.let { it.length > 250 } ?: false


            if (preConditionFailed) {
                throw MatrixServerException(
                    HttpStatusCode.Forbidden,
                    ErrorResponse.TooLarge("'status_msg' is longer than 250 characters.")
                )
            }

            forwardRequest(call, httpClient, call.request.getDestinationUrl(), requestBody.toByteArray())
        }
    }

    private fun Route.getGlobalAccountData() {
        matrixEndpointResource<GetGlobalAccountData> {
            val accountDataType = call.parameters["type"]
            when (accountDataType) {
                AccountDataType.PERMISSION_CONFIG.type -> {
                    val defaultPermissions = timAuthorizationCheckConfiguration.asPermissionConfig()
                    forwardRequestWithDefaultResponse(
                        call = call,
                        httpClient = httpClient,
                        destinationUrl = call.request.getDestinationUrl(),
                        defaultResponseText = Json.encodeToString(defaultPermissions),
                        bodyJson = call.receiveText().toByteArray()
                    )
                }

                AccountDataType.PRO_PERMISSION_CONFIG.type -> {
                    val defaultPermissions = timAuthorizationCheckConfiguration.asProPermissionConfig()
                    forwardRequestWithDefaultResponse(
                        call = call,
                        httpClient = httpClient,
                        destinationUrl = call.request.getDestinationUrl(),
                        defaultResponseText = Json.encodeToString(defaultPermissions),
                        bodyJson = call.receiveText().toByteArray()
                    )
                }

                else -> {
                    forwardRequest(
                        call = call,
                        httpClient = httpClient,
                        destinationUrl = call.request.getDestinationUrl(),
                        bodyJson = call.receiveText().toByteArray()
                    )
                }
            }
        }
    }

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardWithRawData(timOperation: Operation) =
        matrixEndpointResource<ENDPOINT> {
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

    private inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.forwardEndpointWithoutCallReceival() {
        matrixEndpointResource<ENDPOINT> {
            forwardMediaRequest(call, httpClient, call.request.uri.mergeToUrl(config.homeserverUrl))
        }
    }
}
