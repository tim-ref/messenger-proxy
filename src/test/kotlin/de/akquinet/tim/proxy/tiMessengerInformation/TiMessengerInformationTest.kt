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
package de.akquinet.tim.proxy.tiMessengerInformation

import arrow.core.left
import arrow.core.right
import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.authorization.MatrixAuthorizationError
import de.akquinet.tim.proxy.authorization.MatrixAuthorizationService
import de.akquinet.tim.proxy.federation.FederationList
import de.akquinet.tim.proxy.federation.FederationListCache
import de.akquinet.tim.proxy.mocks.federationListCacheOf
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json

class TiMessengerInformationTest : FunSpec({
    val tiMessengerInformationConfig = ProxyConfiguration.TiMessengerInformationConfiguration(
        port = 7777
    )

    fun withTiMessengerInformationTestApplication(
        federationListCache: FederationListCache,
        matrixAuthorizationService: MatrixAuthorizationService,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) {
        val apiUnderTest = TiMessengerInformationApi(
            tiMessengerInformationConfig,
            federationListCache,
            matrixAuthorizationService,
        )

        testApplication {
            routing {
                with(apiUnderTest) {
                    tiMessengerInformationRoutes()
                }
            }

            block()
        }
    }

    context("unauthorized") {
        val dummyMatrixAuthorizationError = mockk<MatrixAuthorizationError> {
            every { message } returns "unauthorized"
        }

        val userIsNotAuthorizedService = mockk<MatrixAuthorizationService> {
            coEvery { authorizeWithoutMxid(any()) } returns dummyMatrixAuthorizationError.left()
        }

        listOf(
            "/tim-information/",
            "/tim-information/v1/server/findByIk",
            "/tim-information/v1/server/isInsurance"
        ).forEach { endpoint ->
            test("$endpoint should answer UNAUTHORIZED if unauthorized") {
                withTiMessengerInformationTestApplication(
                    federationListCache = mockk(),
                    matrixAuthorizationService = userIsNotAuthorizedService
                ) {
                    val response = client.get(endpoint)
                    response.status shouldBe HttpStatusCode.Unauthorized
                    response.bodyAsText() shouldEqualJson
                            """{"errorCode":"401", "errorMessage":"unauthorized"}"""
                }
            }
        }
    }

    val userIsAuthorizedService = mockk<MatrixAuthorizationService> {
        coEvery { authorizeWithoutMxid(any()) } returns Unit.right()
    }

    test("/tim-information/ answers OK with TiMessengerInformation") {
        withTiMessengerInformationTestApplication(
            federationListCache = mockk(),
            matrixAuthorizationService = userIsAuthorizedService
        ) {
            val response = client.get("/tim-information/")
            response.status shouldBe HttpStatusCode.OK

            val tiMessengerInformation = response.bodyAsText().let { Json.decodeFromString<TiMessengerInformation>(it) }
            tiMessengerInformation.title.shouldNotBeBlank()
            tiMessengerInformation.version shouldBe "1.0.0"
        }
    }

    val someFederationListCache = FederationList.FederationDomain(
        domain = "example.org",
        isInsurance = false,
        telematikID = "example-org-id",
        ik = listOf("123")
    ).let { federationListCacheOf(it) }

    context("findByIk") {
        test("findByIk answers BAD_REQUEST if parameter is missing") {
            withTiMessengerInformationTestApplication(
                federationListCache = mockk(),
                matrixAuthorizationService = userIsAuthorizedService
            ) {
                val response = client.get("/tim-information/v1/server/findByIk")
                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldEqualJson
                        """{"errorCode":"400", "errorMessage":"missing query parameter 'ikNumber'"}"""
            }
        }

        test("findByIk answers OK with serverName if domain exists for ikNumber") {
            withTiMessengerInformationTestApplication(
                federationListCache = someFederationListCache,
                matrixAuthorizationService = userIsAuthorizedService
            ) {
                val response = client.get("/tim-information/v1/server/findByIk?ikNumber=123")
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldEqualJson """{"serverName":"example.org"}"""
            }
        }

        test("findByIk answers NOT_FOUND if domain does not exist for ikNumber") {
            withTiMessengerInformationTestApplication(
                federationListCache = someFederationListCache,
                matrixAuthorizationService = userIsAuthorizedService
            ) {
                val response = client.get("/tim-information/v1/server/findByIk?ikNumber=456")
                response.status shouldBe HttpStatusCode.NotFound
                response.bodyAsText() shouldEqualJson
                        """{"errorCode":"404", "errorMessage":"no domain associated with ikNumber=456"}"""
            }
        }
    }

    context("isInsurance") {
        test("isInsurance answers BAD_REQUEST if parameter is missing") {
            withTiMessengerInformationTestApplication(
                federationListCache = mockk(),
                matrixAuthorizationService = userIsAuthorizedService
            ) {
                val response = client.get("/tim-information/v1/server/isInsurance")
                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldEqualJson
                        """{"errorCode":"400", "errorMessage":"missing query parameter 'serverName'"}"""
            }
        }

        test("isInsurance answers OK with isInsurance if domain exists for serverName") {
            withTiMessengerInformationTestApplication(
                federationListCache = someFederationListCache,
                matrixAuthorizationService = userIsAuthorizedService
            ) {
                val response = client.get("/tim-information/v1/server/isInsurance?serverName=example.org")
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldEqualJson """{"isInsurance":false}"""
            }
        }

        test("isInsurance answers NOT_FOUND if domain does not exist for serverName") {
            withTiMessengerInformationTestApplication(
                federationListCache = someFederationListCache,
                matrixAuthorizationService = userIsAuthorizedService
            ) {
                val response = client.get("/tim-information/v1/server/isInsurance?serverName=nope")
                response.status shouldBe HttpStatusCode.NotFound
                response.bodyAsText() shouldEqualJson
                        """{"errorCode":"404", "errorMessage":"no domain associated with serverName=nope"}"""
            }
        }
    }
})