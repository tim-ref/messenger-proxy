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

import com.sksamuel.hoplite.Secret
import de.akquinet.tim.ErrorResponse
import de.akquinet.tim.proxy.InviteRejectionPolicy
import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.TimAuthorizationCheckConcept
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.client.AccessTokenToUserIdAuthenticationFunctionImpl
import de.akquinet.tim.proxy.client.AccessTokenToUserIdImpl
import de.akquinet.tim.proxy.config.SynapseClientConfig
import de.akquinet.tim.proxy.federation.testutils.getEventAuthenticated
import de.akquinet.tim.proxy.federation.testutils.makeJoin
import de.akquinet.tim.proxy.federation.testutils.matrixAuthorizationHeader
import de.akquinet.tim.proxy.federation.testutils.postKeyClaimAuthenticated
import de.akquinet.tim.proxy.federation.testutils.putInvite
import de.akquinet.tim.proxy.federation.testutils.sendJoin
import de.akquinet.tim.proxy.mocks.ContactManagementStub
import de.akquinet.tim.proxy.mocks.FederationListCacheMock
import de.akquinet.tim.proxy.mocks.VZDPublicIDCheckMock
import de.akquinet.tim.proxy.rawdata.RawDataServiceImpl
import de.akquinet.tim.proxy.rawdata.model.RawDataMetaData
import de.akquinet.tim.proxy.synapse.SynapseService
import de.akquinet.tim.proxy.synapse.client.SynapseClient
import de.akquinet.tim.proxy.util.customMatrixServer
import de.akquinet.tim.proxy.validation.A26515ValidationService
import de.akquinet.tim.shouldEqualJsonMatrixStandard
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.MethodNotAllowed
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.spyk
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.clientserverapi.server.matrixAccessTokenAuth
import net.folivo.trixnity.core.model.RoomId

class InboundFederationCheckerImplTest : ShouldSpec({
    val destinationUrl = "https://internal-matrix-server:8090"
    val rawDataServiceUrl = "https://localhost:1234"
    val rawDataPath = "/add-performance-data"
    val federationListCacheMock = FederationListCacheMock()
    val proxyInboundHostPort = 8090
    val hostname = "localhost"
    val matrixHttpPort = 8093
    val inboundHomeserverUrl = "$hostname:$matrixHttpPort"
    val inboundProxyConfig = ProxyConfiguration.InboundProxyConfiguration(
        homeserverUrl = "http://$inboundHomeserverUrl",
        port = proxyInboundHostPort,
        synapseHealthEndpoint = "/health",
        synapsePort = 443,
        enforceDomainList = true,
        accessTokenToUserIdCacheDuration = 1.hours
    )
    val logInfoConfig = ProxyConfiguration.LogInfoConfig(
        "$rawDataServiceUrl$rawDataPath",
        "doctor",
        "2384234234",
        "MP-1",
        "home.de"
    )
    val synapseClientConfig =
        SynapseClientConfig(
            matrixDomain = "internal-matrix-server",
            baseUrl = destinationUrl,
            username = Secret("username"),
            password = Secret("password"),
        )
    lateinit var rawDataService: RawDataServiceImpl
    val contactManagementService = ContactManagementStub()
    val vzdPublicMock = VZDPublicIDCheckMock()
    val eventResponseString = """{
                                  "origin": "fed",
                                  "origin_server_ts": 1234567890,
                                  "pdus": [
                                    {
                                      "content": {
                                        "see_room_version_spec": "The event format changes depending on the room version."
                                      },
                                      "room_id": "!somewhere:otherHost",
                                      "type": "m.room.minimal_pdu"
                                    }
                                  ]
                                }
                                """
    val defaultTimAuthorizationCheckConfiguration = ProxyConfiguration.TimAuthorizationCheckConfiguration(
        TimAuthorizationCheckConcept.PROXY,
        InviteRejectionPolicy.ALLOW_ALL
    )
    val bsEinsService = BerechtigungsstufeEinsService(federationListCacheMock)

    val publicRooms = """
                    {
                      "chunk": [
                        {
                            "room_id": "public:myServer.com",
                            "name": "public",
                            "canonical_alias": "#public:myServer.com",
                            "num_joined_members": 2,
                            "world_readable": false,
                            "guest_can_join": false,
                            "join_rule": "public",
                            "room_type": "m.space"
                        }
                      ],
                      "total_room_count_estimate": 1
                    }
                """.trimIndent()

    beforeTest {
        federationListCacheMock.domains.value = setOf()
    }

    fun withCut(
        // gemSpec_TI-M_Basis_V1.0.0, AFO_25046: override configuration for test
        timAuthorizationCheckConfiguration: ProxyConfiguration.TimAuthorizationCheckConfiguration = defaultTimAuthorizationCheckConfiguration,
        block: suspend ApplicationTestBuilder.() -> Unit
    ) {
        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            val synapseClient = SynapseClient(engine = client.engine, config = synapseClientConfig)

            val synapseService =
                SynapseService(
                    synapseClient = synapseClient,
                    config = synapseClientConfig,
                )

            val a26515ValidationService = A26515ValidationService(synapseService)

            rawDataService = spyk(RawDataServiceImpl(logInfoConfig, client))

            application {
                install(Authentication) {
                    berechtigungsstufeEinsCheck(checkerService = bsEinsService) {
                        proxyMode = BerechtigungsstufeEinsAuthenticationProvider.ProxyMode.INBOUND
                    }
                    matrixAccessTokenAuth("matrix-access-token-auth") {
                        authenticationFunction = AccessTokenToUserIdAuthenticationFunctionImpl(
                            AccessTokenToUserIdImpl(inboundProxyConfig, MatrixApiClient())
                        )
                    }
                }

                customMatrixServer(Json) {
                    authenticate(BerechtigungsstufeEinsAuthenticationProvider.IDENTIFIER) {
                        with(
                            InboundFederationRoutesImpl(
                                config = ProxyConfiguration.InboundProxyConfiguration(
                                    enforceDomainList = true,
                                    homeserverUrl = destinationUrl,
                                    synapseHealthEndpoint = "/health",
                                    synapsePort = 8090,
                                    port = 443,
                                    accessTokenToUserIdCacheDuration = 1.hours
                                ),
                                httpClient = this@testApplication.client,
                                rawDataService = rawDataService,
                                contactManagementService = contactManagementService,
                                vzdPublicIDCheck = vzdPublicMock,
                                timAuthorizationCheckConfiguration = timAuthorizationCheckConfiguration,
                                berechtigungsstufeEinsService = bsEinsService,
                                a26515ValidationService = a26515ValidationService,
                            )
                        ) {
                            serverServerApiRoutes()
                            serverServerRawDataRoutes()
                        }
                    }
                }
            }
            externalServices {
                hosts(destinationUrl) {
                    routing {
                        get("/_matrix/federation/v1/event/1234") {
                            call.respondText(eventResponseString, Application.Json)
                        }
                        post("/_matrix/federation/v1/user/keys/claim") {
                            call.request.uri shouldBe "/_matrix/federation/v1/user/keys/claim?test=test"
                            call.receiveText() shouldEqualJson """{"one_time_keys":{}}"""
                            call.request.contentType() shouldBe Application.Json

                            call.respondText("""{"one_time_keys":{}}""", Application.Json)
                        }
                        put("/_matrix/federation/v2/invite/{roomId}/{eventId}") {
                            //horribly complicated event structure omitted for testing
                            call.respondText("{}", Application.Json)
                        }
                        put("/_matrix/federation/v2/send_join/{roomId}/{eventId}") {
                            call.respondText("{}", Application.Json)
                        }
                        get("/_matrix/federation/v1/make_join/{roomId}/{userId}") {
                            call.respondText("{}", Application.Json)
                        }
                        get("/_matrix/client/v3/publicRooms") {
                            call.respondText(
                                contentType = Application.Json,
                                status = OK,
                                text = publicRooms
                            )
                        }
                        post("/_matrix/client/v3/login") {
                            call.respondText(
                                """
              {
                "access_token": "access token", 
                "user_id": "@test-user:snpse.org"
              }
            """
                                    .trimIndent(),
                                Application.Json,
                            )
                        }
                        get("/_synapse/admin/v1/rooms/{roomId}") {
                            /// Use roomId opaqueId to choose joinRules setting
                            val roomIdPathParam =
                                call.parameters["roomId"] ?: throw BadRequestException("roomId is missing")
                            val roomId =
                                RoomId(roomIdPathParam)

                            val joinRuleStrings = listOf("public", "knock", "restricted", "knock_restricted", "private")

                            val joinRules =
                                joinRuleStrings.firstOrNull { roomId.localpart.contains(it) } ?: "invite"

                            call.respondText(
                                """
              {
                "room_id": "$roomIdPathParam",
                "name": "Music Theory",
                "avatar": "mxc://matrix.org/AQDaVFlbkQoErdOgqWRgiGSV",
                "topic": "Theory, Composition, Notation, Analysis",
                "canonical_alias": "#musictheory:matrix.org",
                "joined_members": 127,
                "joined_local_members": 2,
                "joined_local_devices": 2,
                "version": "1",
                "creator": "@foo:matrix.org",
                "encryption": null,
                "federatable": true,
                "public": true,
                "join_rules": "$joinRules",
                "guest_access": null,
                "history_visibility": "shared",
                "state_events": 93534,
                "room_type": "m.space",
                "forgotten": false
              }
            """
                                    .trimIndent(),
                                Application.Json,
                            )
                        }
                    }
                }
                hosts(rawDataServiceUrl) {
                    routing {
                        post(rawDataPath) {
                            Json.decodeFromString<RawDataMetaData>(call.receiveText()).shouldBeTypeOf<RawDataMetaData>()
                            call.request.contentType() shouldBe Application.Json
                        }
                    }
                }
            }
            block()
        }
    }

    context("authorization needed") {
        should("forward federated domain") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.postKeyClaimAuthenticated()

                assertSoftly(response) {
                    response shouldHaveStatus OK
                    bodyAsText() shouldEqualJson """{"one_time_keys":{}}"""
                }
            }
        }

        should("should forward get event with raw data send") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.getEventAuthenticated()

                assertSoftly(response) {
                    response shouldHaveStatus OK
                    bodyAsText() shouldEqualJson eventResponseString
                }
            }
        }

        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-Messenger-Dienst/gemSpec_TI-Messenger-Dienst_V1.1.1/#8.3
        should("deny unfederated domain") {
            withCut {
                val response = client.postKeyClaimAuthenticated()

                response shouldHaveStatus Forbidden
                response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                    errcode = "M_FORBIDDEN",
                    error = "not part of federation"
                )
            }
        }

        /**
         * A_26331
         * Der TI-M Fachdienst MUSS nicht authentisierte Requests zum Endpunkt /_matrix/federation/v1/version mit
         * einer HTTP 401 Response ablehnen.
         */
        should("reject unauthenticated request for server version") {
            withCut {
                val response = client.get("/_matrix/federation/v1/version")

                response shouldHaveStatus Unauthorized
            }
        }

        should("forward authenticated request for server version") {
            withCut {
                externalServices {
                    hosts(destinationUrl) {
                        routing {
                            get("/_matrix/federation/v1/version") {
                                call.respondText("""{ "id": "success" }""", Application.Json)
                            }
                        }
                    }
                }
                federationListCacheMock.domains.value += insuranceDomainFed()

                val response = client.get("/_matrix/federation/v1/version") {
                    matrixAuthorizationHeader()
                }

                response.bodyAsText() shouldEqualJson """{ "id": "success" }"""
            }
        }
    }

    should("ignore unknown url") {
        withCut {
            val response = client.get("/blubs")

            response shouldHaveStatus NotFound
            response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                errcode = "M_NOT_FOUND",
                error = "unsupported (or unknown) endpoint",
            )
        }
    }

    /*
     * Similarly, a 405 M_UNRECOGNIZED error is used to denote an unsupported
     * method to a known endpoint.
     *
     * See https://spec.matrix.org/v1.11/server-server-api/#unsupported-endpoints
     */
    context("respond with M_UNRECOGNIZED for unsupported method of known endpoint") {
        should("POST") {
            withCut {
                val response = client.post("/.well-known/matrix/server")

                assertSoftly {
                    response shouldHaveStatus MethodNotAllowed
                    response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                        errcode = "M_UNRECOGNIZED",
                        error = "This endpoint is implemented, but the method is not supported.",
                    )
                }
            }
        }

        should("PUT") {
            withCut {
                val response = client.put("/.well-known/matrix/server")

                assertSoftly {
                    response shouldHaveStatus MethodNotAllowed
                    response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                        errcode = "M_UNRECOGNIZED",
                        error = "This endpoint is implemented, but the method is not supported.",
                    )
                }
            }
        }

        should("DELETE") {
            withCut {
                val response = client.delete("/.well-known/matrix/server")

                assertSoftly {
                    response shouldHaveStatus MethodNotAllowed
                    response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                        errcode = "M_UNRECOGNIZED",
                        error = "This endpoint is implemented, but the method is not supported.",
                    )
                }
            }
        }

        should("PATCH") {
            withCut {
                val response = client.patch("/.well-known/matrix/server")

                assertSoftly {
                    response shouldHaveStatus MethodNotAllowed
                    response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                        errcode = "M_UNRECOGNIZED",
                        error = "This endpoint is implemented, but the method is not supported.",
                    )
                }
            }
        }
    }

    context("federation invite") {
        // gemSpec_TI-Messenger-Dienst_V1.1.1, 3.5.2.2+: after federation check, check invite permission on proxy
        should("fail if neither contact nor FHIR entry if check on proxy") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.putInvite(sender = "@someone:fed", invited = "@joe:fed")

                response shouldHaveStatus Forbidden
                response.bodyAsText() shouldEqualJson """{
                        "errcode":"M_FORBIDDEN",
                        "error":"can not invite this user"
                    }"""
            }
        }

        should("succeed for valid contact") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.putInvite(sender = "@4444:fed", invited = "@joe:fed")

                response shouldHaveStatus OK
            }
        }

        should("succeed for FHIR entry") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                vzdPublicMock.expectedResult = true
                val response = client.putInvite(sender = "@someone:fed", invited = "@joe:fed")

                response shouldHaveStatus OK
            }
        }

        // gemSpec_TI-M_Basis_V1.0.0, AFO_25046: after federation check, send invite to client to check invite permission

        should("succeed if invite permission check on client") {
            val clientTimAuthorizationCheckConfiguration = ProxyConfiguration.TimAuthorizationCheckConfiguration(
                TimAuthorizationCheckConcept.CLIENT,
                InviteRejectionPolicy.ALLOW_ALL
            )
            withCut(clientTimAuthorizationCheckConfiguration) {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.putInvite(sender = "@someone:fed", invited = "@joe:fed")

                response shouldHaveStatus OK
            }
        }

        // AF_10064-02 - Föderationszugehörigkeit eines Messenger-Service prüfen
        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#AF_10064-02
        // A_25534 - Fehlschlag Föderationsprüfung
        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#A_25534
        should("reject invites to invitees outside of federation") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.putInvite(sender = "@someone:fed", invited = "@invitee:invitee.org")

                response shouldHaveStatus Forbidden
                response.bodyAsText() shouldEqualJson """{
                        "errcode": "M_FORBIDDEN" ,
                        "error": "invitee.org kann nicht in der Föderation gefunden werden"
                    }"""
            }
        }

        // AF_10064-02 - Föderationszugehörigkeit eines Messenger-Service prüfen
        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#AF_10064-02
        // A_25534 - Fehlschlag Föderationsprüfung
        // https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.1.1/#A_25534
        should("reject invites from senders outside of federation") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.putInvite(sender = "@someone:sender.org", invited = "@invitee:fed")

                response shouldHaveStatus Forbidden
                response.bodyAsText() shouldEqualJson """{
                        "errcode": "M_FORBIDDEN" ,
                        "error": "sender.org kann nicht in der Föderation gefunden werden"
                    }"""
            }
        }
    }

    context("A_26520 - Öffentliche Räume Server-API") {
        context("public room is listed on publicRooms (visibility: public)") {
            should("fail, if user from other homeserver trys to join public room via make_join") {
                withCut {
                    federationListCacheMock.domains.value = setOf(
                        insuranceDomainFed()
                    )
                    val response = client.makeJoin("public")
                    response.status shouldBe Forbidden
                    response.bodyAsText() shouldBe """{"errcode":"M_FORBIDDEN","error":"Joining federated public rooms is forbidden"}"""
                }
            }

            should("fail, if user from other homeserver trys to join public room via send_join") {
                withCut {
                    federationListCacheMock.domains.value = setOf(
                        insuranceDomainFed()
                    )
                    val response = client.sendJoin("public")
                    response.status shouldBe Forbidden
                    response.bodyAsText() shouldBe """{"errcode":"M_FORBIDDEN","error":"Joining federated public rooms is forbidden"}"""
                }
            }
        }

        context("public room is not listed on publicRooms (visibility: private)") {
            should("fail, if user from other homeserver trys join public room via make_join") {
                withCut {
                    federationListCacheMock.domains.value = setOf(
                        insuranceDomainFed()
                    )
                    val response = client.makeJoin("publicInvisible")
                    response.status shouldBe Forbidden
                    response.bodyAsText() shouldBe """{"errcode":"M_FORBIDDEN","error":"Joining federated public rooms is forbidden"}"""
                }
            }

            should("fail, if user from other homeserver trys to join public room via send_join") {
                withCut {
                    federationListCacheMock.domains.value = setOf(
                        insuranceDomainFed()
                    )
                    val response = client.sendJoin("publicInvisible")
                    response.status shouldBe Forbidden
                    response.bodyAsText() shouldBe """{"errcode":"M_FORBIDDEN","error":"Joining federated public rooms is forbidden"}"""
                }
            }
        }
        should("succeed if user from other homeserver trys to join a private room via invite") {
            withCut {
                federationListCacheMock.domains.value = setOf(
                    insuranceDomainFed()
                )
                val response = client.sendJoin("private")
                response.status shouldBe OK
            }
        }
    }
})

private fun insuranceDomainFed() = FederationList.FederationDomain(
    domain = "fed",
    isInsurance = true,
    telematikID = "telematik"
)
