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
package de.akquinet.tim.proxy.integrationtests

import de.akquinet.tim.ErrorResponse
import de.akquinet.tim.jsonMatrixStandardErrorResponse
import de.akquinet.tim.proxy.*
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.client.AccessTokenToUserIdAuthenticationFunctionImpl
import de.akquinet.tim.proxy.client.AccessTokenToUserIdImpl
import de.akquinet.tim.proxy.client.InboundClientRoutesImpl
import de.akquinet.tim.proxy.federation.FederationList
import de.akquinet.tim.proxy.federation.InboundFederationRoutesImpl
import de.akquinet.tim.proxy.mocks.ContactManagementStub
import de.akquinet.tim.proxy.mocks.FederationListCacheMock
import de.akquinet.tim.proxy.mocks.RawDataServiceStub
import de.akquinet.tim.proxy.mocks.VZDPublicIDCheckMock
import de.akquinet.tim.shouldEqualJsonMatrixStandard
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldContainJsonKeyValue
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.MethodNotAllowed
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.api.client.MatrixApiClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

const val proxy = "http://localhost:8090"
const val homeserver = "http://localhost:8083"

class InboundProxyIT {

    private lateinit var federationListCacheMock: FederationListCacheMock

    private lateinit var rawDataServiceStub: RawDataServiceStub
    private lateinit var bsEinsService: BerechtigungsstufeEinsService
    private val contactManagementServiceMock = ContactManagementStub()
    private val vzdPublicIDCheckMock = VZDPublicIDCheckMock()

    private val virtualHostname = ""
    private val externalMatrixHostname = ""
    private val matrixHttpsPort = 8090
    private val proxyInboundHostPort = 8090
    private val wellKnownEndpoint = "$proxy/.well-known/matrix/server"
    private val unknownEndpoint = "$proxy/_matrix/client/v3/asdf"
    private val pushRuleWithoutTemplateEndpoint = "$proxy/_matrix/client/v3/pushrules/global/not_a_template/foo"
    private val getTokenEndpoint = "$proxy/_matrix/client/v1/login/get_token"
    private val registerAsGuestEndpoint = "$proxy/_matrix/client/v3/register?kind=guest"
    private val eventEndpoint = "/_matrix/client/v3/rooms/room1%3Asynapse/send/m.room.encrypted/abc"
    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json()
        }
        followRedirects = false

        install(Logging) {
            level = LogLevel.ALL
        }
    }
    private val logInfoConfig = ProxyConfiguration.LogInfoConfig(
        "rawdata/path",
        "doctor",
        "2384234234",
        "MP-1",
        "home.de"
    )

    private lateinit var inboundApplicationEngine: ApplicationEngine

    private var timAuthorizationCheckConfiguration = ProxyConfiguration.TimAuthorizationCheckConfiguration(
        TimAuthorizationCheckConcept.CLIENT,
        InviteRejectionPolicy.ALLOW_ALL
    )

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        val inboundProxyConfig = ProxyConfiguration.InboundProxyConfiguration(
            homeserverUrl = "http://localhost:8083",
            port = proxyInboundHostPort,
            synapseHealthEndpoint = "/health",
            synapsePort = 443,
            enforceDomainList = true,
            accessTokenToUserIdCacheDuration = 1.hours
        )

        federationListCacheMock = FederationListCacheMock()
        bsEinsService = BerechtigungsstufeEinsService(federationListCacheMock)
        rawDataServiceStub = RawDataServiceStub()
        // always trust server itself
        federationListCacheMock.domains.update {
            it + FederationList.FederationDomain(
                domain = "$virtualHostname:$matrixHttpsPort$externalMatrixHostname:$matrixHttpsPort",
                isInsurance = true,
                telematikID = "telematik"
            )
        }

        inboundApplicationEngine =
            InboundProxyImpl(
                inboundProxyConfiguration = inboundProxyConfig,
                berechtigungsstufeEinsService = bsEinsService,
                accessTokenToUserIdAuthenticationFunction = AccessTokenToUserIdAuthenticationFunctionImpl(
                    AccessTokenToUserIdImpl(inboundProxyConfig, MatrixApiClient())
                ),
                inboundClientRoutes = InboundClientRoutesImpl(
                    config = inboundProxyConfig,
                    logConfiguration = logInfoConfig,
                    timAuthorizationCheckConfiguration = timAuthorizationCheckConfiguration,
                    httpClient = httpClient,
                    rawDataService = rawDataServiceStub,
                    berechtigungsstufeEinsService = bsEinsService,
                    ProxyConfiguration.RegistrationServiceConfiguration(
                        baseUrl = "https://reg-service",
                        servicePort = "8080",
                        healthPort = "8081",
                        federationListEndpoint = "/backend/federation",
                        invitePermissionCheckEndpoint = "/backend/vzd/invite",
                        readinessEndpoint = "/actuator/health/readiness",
                        wellKnownSupportEndpoint = "/backend/well-known-support"
                    )
                ),
                inboundFederationRoutes = InboundFederationRoutesImpl(
                    config = inboundProxyConfig,
                    httpClient = httpClient,
                    rawDataService = rawDataServiceStub,
                    contactManagementService = contactManagementServiceMock,
                    vzdPublicIDCheck = vzdPublicIDCheckMock,
                    timAuthorizationCheckConfiguration = timAuthorizationCheckConfiguration,
                    berechtigungsstufeEinsService = bsEinsService
                ),
                httpClient = httpClient
            ).start()

    }

    @AfterTest
    fun afterEach() {
        inboundApplicationEngine.stop()
        httpClient.close()
    }

    @Test
    fun shouldReturnWellknownHostnameFromRequest() = runTest {
        val response = httpClient.get(wellKnownEndpoint)

        response.status shouldBe OK
        response.bodyAsText() shouldBe "{\"m.server\":\"localhost:443\"}"
    }

    // These tests verify the changes made in the CustomMatrixServer.kt in comparison to the original MatrixApiServer.kt
    @Test
    fun `should return NotFound on not-existent route`() = runTest {
        val response = httpClient.get(unknownEndpoint)

        response.status shouldBe NotFound
        response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
            errcode = "M_NOT_FOUND",
            error = "unsupported (or unknown) endpoint"
        )
    }

    /*
     * Similarly, a 405 M_UNRECOGNIZED error is used to denote an unsupported
     * method to a known endpoint.
     *
     * See https://spec.matrix.org/v1.11/server-server-api/#unsupported-endpoints
     */
    @Test
    fun `respond with M_UNRECOGNIZED for unsupported method of known endpoint`() = runTest {
        val response = httpClient.post(wellKnownEndpoint)

        assertSoftly {
            response shouldHaveStatus MethodNotAllowed
            response.bodyAsText() shouldBe jsonMatrixStandardErrorResponse()
            response.bodyAsText().shouldContainJsonKeyValue("$.errcode", "M_UNRECOGNIZED")
        }
    }

    @Test
    fun shouldReturnBadRequestOnBadRequestException() = runTest {
        val response = httpClient.put(pushRuleWithoutTemplateEndpoint)

        response.status shouldBe BadRequest
        // the error message in the real application environment is "Can't transform call to resource"
        response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
            errcode = "M_UNKNOWN",
            error = "net.folivo.trixnity.core.model.push.PushRuleKind does not contain element with name 'not_a_template'"
        )
    }

    @Test
    fun shouldReturnBadRequestForRequestToGetToken() = runTest {
        val response = httpClient.post(getTokenEndpoint)

        response.status shouldBe NotFound

        response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
            errcode = "M_NOT_FOUND",
            error = "No resource was found for this request."
        )
    }

    @Test
    fun shouldReturnForbiddenForGuestRegister() = runTest {
        val response = httpClient.post(registerAsGuestEndpoint) {
            headers {
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody("{}")
        }

        response.status shouldBe Forbidden

        response.bodyAsText() shouldEqualJson
                """{
                    "errcode": "M_FORBIDDEN",
                    "error":"Guest access is disabled"
                    }"""
    }

    @Test
    fun shouldReturnForbiddenForPublicRooms() = runTest {
        val response = httpClient.post("$proxy/_matrix/federation/v1/publicRooms") {
            headers {
                append(HttpHeaders.ContentType, "application/json")
                append(
                    HttpHeaders.Authorization,
                    """X-Matrix origin="fed",destination="otherHost:80",key="ed25519:ABC",sig="signature""""
                )
            }
            setBody("{}")
        }

        response.status shouldBe Forbidden
    }

    @Test
    fun shouldReturnTooLongErrorMessageForThreeEmojis() = runTest {
        val requestBody = """
            {
            "m.relates_to":{
            "event_id":"event123",
            "rel_type":"m.annotation",
            "key":"👨‍👩‍👧👨‍👩‍"}}
        """.trimIndent()

        testApplication {
            proxyWithClientServerRoutes(defaultConfig(httpClient = client))
            homeserverWithRouting {
                put(eventEndpoint) {
                    call.respondText(
                        contentType = Json,
                        status = OK,
                        text = "blubb"
                    )
                }
            }

            val response = client.put(eventEndpoint) {
                headers {
                    append(HttpHeaders.ContentType, "application/json")
                    append("authorization", "Bearer Super key")
                }
                setBody(requestBody)
            }

            response.status shouldBe BadRequest
            response.bodyAsText() shouldEqualJson
                    """{
                    "errcode": "M_TOO_LARGE",
                    "error":"Key is longer than 1 character"
                }"""
        }
    }

    @Test
    fun shouldReturnSuccessMessageForOneEmoji() {
        val eventResponse = """
            {
              "event_id": "event345"
            }
        """
        val requestBody = """
            {
            "m.relates_to":{
            "event_id":"event123",
            "rel_type":"m.annotation",
            "key":"👨‍👩‍👧"}}
        """.trimIndent()

        testApplication {
            proxyWithClientServerRoutes(defaultConfig(httpClient = client))
            homeserverWithRouting {
                put(eventEndpoint) {
                    call.respondText(
                        contentType = Json,
                        status = OK,
                        text = eventResponse
                    )
                }
            }

            val response =
                client.put(eventEndpoint) {
                    contentType(Json)
                    accept(Json)
                    setBody(requestBody)
                }

            assertSoftly(response) {
                status shouldBe OK
                bodyAsText() shouldEqualJson eventResponse
            }
        }
    }

    @Test
    fun shouldReturnForbiddenErrorMessageForThreading() = runTest {
        val requestBody = """
            {
              "m.relates_to": {
                "event_id": "event123",
                "rel_type": "m.thread"
              }
            }
        """.trimIndent()

        testApplication {
            proxyWithClientServerRoutes(defaultConfig(httpClient = client))
            homeserverWithRouting {
                put(eventEndpoint) {
                    call.respondText(
                        contentType = Json,
                        status = OK,
                        text = "blubb"
                    )
                }
            }

            val response = client.put(eventEndpoint) {
                headers {
                    append(HttpHeaders.ContentType, "application/json")
                    append("authorization", "Bearer Super key")
                }
                setBody(requestBody)
            }

            response.status shouldBe BadRequest
            response.bodyAsText() shouldEqualJson """
            {
              "errcode": "M_FORBIDDEN",
              "error": "threading is not allowed"
            }"""
        }
    }

    @Test
    fun shouldNotFindPreviewUrlEndpoint() = runTest {
        testApplication {
            proxyWithClientServerRoutes(defaultConfig(httpClient = client))
            homeserverWithRouting {
                get("/_matrix/client/v1/media/preview_url") {
                    call.respondText("Hi!")
                }
            }

            val response = client.get("/_matrix/client/v1/media/preview_url")
            response shouldHaveStatus NotFound
        }
    }

    @Test
    fun shouldNotFindLegacyPreviewUrlEndpoint() = runTest {
        testApplication {
            proxyWithClientServerRoutes(defaultConfig(httpClient = client))
            homeserverWithRouting {
                get("/_matrix/media/v3/preview_url") {
                    call.respondText("Hi!")
                }
            }

            val response = client.get("/_matrix/media/v3/preview_url")
            response shouldHaveStatus NotFound
        }
    }
}
