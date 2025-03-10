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
import com.vdurmont.emoji.EmojiParser
import de.akquinet.tim.proxy.*
import de.akquinet.tim.proxy.ProxyConfiguration.TimAuthorizationCheckConfiguration
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.client.model.route.*
import de.akquinet.tim.proxy.client.model.route.GetDirectoryVisibility
import de.akquinet.tim.proxy.client.model.route.GetPublicRooms
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
import de.akquinet.tim.proxy.rawdata.RawDataService
import de.akquinet.tim.proxy.rawdata.model.Operation
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import mu.KotlinLogging
import net.folivo.trixnity.api.server.matrixEndpointResource
import net.folivo.trixnity.clientserverapi.model.authentication.*
import net.folivo.trixnity.clientserverapi.model.devices.*
import net.folivo.trixnity.clientserverapi.model.discovery.GetSupport
import net.folivo.trixnity.clientserverapi.model.discovery.GetWellKnown
import net.folivo.trixnity.clientserverapi.model.keys.*
import net.folivo.trixnity.clientserverapi.model.media.*
import net.folivo.trixnity.clientserverapi.model.push.*
import net.folivo.trixnity.clientserverapi.model.rooms.*
import net.folivo.trixnity.clientserverapi.model.server.GetCapabilities
import net.folivo.trixnity.clientserverapi.model.server.GetVersions
import net.folivo.trixnity.clientserverapi.model.server.Search
import net.folivo.trixnity.clientserverapi.model.server.WhoIs
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.users.*
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.ReactionEventContent

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
    private val regServiceConfig: ProxyConfiguration.RegistrationServiceConfiguration
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

            kLog.info { "Forward request on ${call.request.path()} to $url"  }

            forwardRequest(
                call = call,
                httpClient = httpClient,
                destinationUrl = url,
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
            @Suppress("DEPRECATION")
            forwardEndpointWithoutCallReceival<DownloadMediaLegacy>()
            @Suppress("DEPRECATION")
            forwardEndpoint<DownloadThumbnailLegacy>()
            forwardEndpoint<DownloadThumbnail>()
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
            if (config.enforceDomainList && !berechtigungsstufeEinsService.areDomainsFederated(relevantDomains)) {
                throw MatrixServerException(
                    statusCode = HttpStatusCode.Forbidden,
                    errorResponse = ErrorResponse.Forbidden("${userId ?: "unbekannter Benutzer"} konnte nicht eingeladen werden. Die Föderationsliste enthält nicht alle Domains: ${relevantDomains.joinToString()}.")
                )
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

            if (config.enforceDomainList && !berechtigungsstufeEinsService.areDomainsFederated(
                    setOf(
                        invited.domain,
                        inviter.userId.domain
                    )
                )
            ) {
                throw MatrixServerException(
                    statusCode = HttpStatusCode.Forbidden,
                    errorResponse = ErrorResponse.Forbidden("$invited konnte nicht eingeladen werden")
                )
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
        forwardEndpoint<GetUrlPreview>()
        forwardEndpoint<GetUrlPreviewLegacy>()
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

    private fun assertEmojiLength(key: String?, relType: String?) {
        if (key.isNullOrBlank()) return

        val emojiHasSize1 = EmojiParser.extractEmojis(key).size == 1
        val isEmptyWithEmojisRemoved = EmojiParser.removeAllEmojis(key).isEmpty()
        val isAnnotation = relType == "m.annotation"

        if (emojiHasSize1 && isEmptyWithEmojisRemoved && isAnnotation) return

        throw MatrixServerException(
            HttpStatusCode.BadRequest,
            ErrorResponse.TooLarge("Key is longer than 1 character")
        )
    }

    private fun assertNotThreading(relType: String?) {
        if (relType == "m.thread") {
            throw MatrixServerException(
                HttpStatusCode.BadRequest,
                ErrorResponse.Forbidden("threading is not allowed")
            )
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private fun Route.sendMessageEvent() {

        matrixEndpointResource<SendMessageEvent> {
            val requestBody = call.receiveText()
            val request = json.decodeFromString<ReactionEventContent>(requestBody)
            val relatesTo = request.relatesTo
            val key = relatesTo?.key
            val relType = relatesTo?.relationType?.name

            assertEmojiLength(key, relType)
            assertNotThreading(relType)

            forwardRequest(
                call,
                httpClient,
                call.request.uri.mergeToUrl(config.homeserverUrl),
                requestBody.toByteArray()
            ).let {
                rawDataService.clientRawDataForward(
                    it.first.headers,
                    it.second.status.value,
                    it.third,
                    Operation.MP_EXCHANGE_EVENT_WITHIN_ORGANISATION,
                    it.fourth
                )
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
            forwardRequestWithoutCallReceival(call, httpClient, call.request.uri.mergeToUrl(config.homeserverUrl))
        }
    }
}
