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
package de.akquinet.tim.proxy.contactmanagement

import arrow.core.left
import arrow.core.right
import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.Contact
import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.ContactInviteSettings
import de.akquinet.tim.proxy.authorization.MatrixAuthorizationError
import de.akquinet.tim.proxy.authorization.MatrixAuthorizationService
import de.akquinet.tim.proxy.contactmgmt.ContactRoutesImpl
import de.akquinet.tim.proxy.contactmgmt.contactApiServer
import de.akquinet.tim.proxy.mocks.ContactManagementStub
import de.akquinet.tim.proxy.mocks.RawDataServiceStub
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.ktor.client.shouldHaveContentType
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ContactManagementAPITest : ShouldSpec({
    val matrixAuthorizationServiceMock: MatrixAuthorizationService = mockk()

    fun withCut(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            install(ContentNegotiation) {
                json()
            }

            application {
                val contactManagementService = ContactManagementStub()
                val rawDataService = RawDataServiceStub()

                contactApiServer {
                    with(ContactRoutesImpl(contactManagementService, rawDataService, matrixAuthorizationServiceMock)) {
                        apiRoutes()
                    }
                }
            }
            block()
        }
    }

    fun givenAuthenticatedUser(mxid: String = "1234", block: () -> Unit) {
        runBlocking {
            coEvery { matrixAuthorizationServiceMock.authorize(any()) } returns mxid.right()
            block()
            coVerify(exactly = 1) { matrixAuthorizationServiceMock.authorize(any()) }
        }
    }

    fun givenUnauthenticatedUser(block: () -> Unit) {
        val dummyMatrixAuthorizationError = mockk<MatrixAuthorizationError> {
            every { message } returns "UNAUTHORIZED"
        }

        runBlocking {
            coEvery { matrixAuthorizationServiceMock.authorize(any()) } returns dummyMatrixAuthorizationError.left()
            block()
            coVerify(exactly = 1) { matrixAuthorizationServiceMock.authorize(any()) }
        }
    }

    fun noAuthenticationNeeded(block: () -> Unit) {
        runBlocking {
            block()
            coVerify(exactly = 0) { matrixAuthorizationServiceMock.authorize(any()) }
        }
    }

    context("contact-mgm-api") {
        beforeEach {
            clearMocks(matrixAuthorizationServiceMock)
        }

        should("get basic api info") {
            noAuthenticationNeeded {
                withCut {
                    val response = client.get("/tim-contact-mgmt")

                    response shouldHaveStatus OK
                    response.shouldHaveContentType(Application.Json.withCharset(Charsets.UTF_8))
                    response.bodyAsText() shouldEqualJson """{"title":"Contact Management des TI-Messengers",
                        "version":"1.0.2","description":
                        "Contact Management des TI-Messengers. Betreiber: <Betreibername>",
                        "contact":"Contact information"}"""
                }
            }
        }

        /**
         * Tests for GET "/tim-contact-mgmt/contacts"
         * */
        should("should throw unauthorized when authorization fails") {
            givenUnauthenticatedUser {
                withCut {
                    val response = client.get("/tim-contact-mgmt/contacts")

                    response shouldHaveStatus Unauthorized
                    response.bodyAsText() shouldBe "UNAUTHORIZED"
                }
            }

        }

        should("should return an empty list") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.get("/tim-contact-mgmt/contacts")

                    response shouldHaveStatus OK
                    response.shouldHaveContentType(Application.Json.withCharset(Charsets.UTF_8))
                    response.bodyAsText() shouldEqualJson """{"contacts":[]}"""
                }
            }
        }

        should("should return non-empty List") {
            givenAuthenticatedUser("12345") {
                withCut {
                    val response = client.get("/tim-contact-mgmt/contacts")

                    response shouldHaveStatus OK
                    response.shouldHaveContentType(Application.Json.withCharset(Charsets.UTF_8))
                    response.bodyAsText() shouldEqualJson """{"contacts":[{"displayName":"Alice","mxid":"12345",
                        "inviteSettings":{"start":17,"end":null}}]}"""
                }
            }
        }

        /**
         * Tests for GET "/tim-contact-mgmt/contacts/{mxid}"
         * */
        should("get contact settings answers unauthorized if unauthorized") {
            givenUnauthenticatedUser {
                withCut {
                    val response = client.get("/tim-contact-mgmt/contacts/5555")

                    response shouldHaveStatus Unauthorized
                    response.bodyAsText() shouldBe "UNAUTHORIZED"
                }
            }
        }

        should("get contacts unauthenticated should throw unauthorized") {
            givenUnauthenticatedUser {
                withCut {
                    val response = client.get("/tim-contact-mgmt/contacts/5555")

                    response shouldHaveStatus Unauthorized
                    response.bodyAsText() shouldBe "UNAUTHORIZED"
                }
            }
        }

        should("get all should not be found") {
            noAuthenticationNeeded {
                withCut {
                    val response = client.get("/tim-contact-mgmt/contacts/")

                    response shouldHaveStatus BadRequest
                    response.shouldHaveContentType(Application.Json.withCharset(Charsets.UTF_8))
                    response.bodyAsText() shouldEqualJson """{"errorCode":"400 Bad Request","errorMessage":"missing parameter 'id'"}"""
                }
            }
        }

        should("get missing one should return not found") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.get("/tim-contact-mgmt/contacts/5555")

                    response shouldHaveStatus NotFound
                    response.shouldHaveContentType(Application.Json.withCharset(Charsets.UTF_8))
                    response.bodyAsText() shouldEqualJson """{"errorCode":"404 Not Found","errorMessage":"contact not found: 5555"}"""
                }
            }
        }

        should("get available one should return contact") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.get("/tim-contact-mgmt/contacts/4444")

                    response shouldHaveStatus OK
                    response.shouldHaveContentType(Application.Json.withCharset(Charsets.UTF_8))
                    response.bodyAsText() shouldEqualJson """{"displayName":"Alice","mxid":"4444","inviteSettings":
                        {"start":17,"end":null}}"""
                }
            }
        }


        /**
         * Tests for POST "/tim-contact-mgmt/contacts"
         * */
        should("post should throw bad request when request body is empty") {
            noAuthenticationNeeded {
                withCut {
                    val response = client.post("/tim-contact-mgmt/contacts") {
                        setBody("\"{}\"")
                        contentType(Application.Json)
                    }

                    response shouldHaveStatus BadRequest
                    response.shouldHaveContentType(Application.Json.withCharset(Charsets.UTF_8))
                    response.bodyAsText() shouldEqualJson """{"errorCode":"400 Bad Request","errorMessage":"contact malformed"}"""
                }
            }
        }

        should("post should throw unauthorized when user is not authenticated") {
            givenUnauthenticatedUser {
                withCut {
                    val response = client.post("/tim-contact-mgmt/contacts") {
                        contentType(Application.Json)
                        setBody(
                            Json.encodeToString(
                                Contact(
                                    mxid = "333", displayName = "Bob", inviteSettings = ContactInviteSettings(17)
                                )
                            )
                        )

                    }

                    response shouldHaveStatus Unauthorized
                    response.bodyAsText() shouldBe "UNAUTHORIZED"
                }
            }
        }

        should("post should respond created") {
            givenAuthenticatedUser {
                withCut {
                    val contact = Contact(
                        mxid = "4445", displayName = "Alice", inviteSettings = ContactInviteSettings(17)
                    )
                    val response = client.post("/tim-contact-mgmt/contacts") {
                        contentType(Application.Json)
                        setBody(Json.encodeToString(contact))
                    }

                    response shouldHaveStatus OK
                    response.shouldHaveContentType(Application.Json.withCharset(Charsets.UTF_8))
                    response.bodyAsText() shouldEqualJson """{"displayName":"Alice","mxid":"4445","inviteSettings":
                        {"start":17,"end":null}}"""
                }
            }
        }

        should("post should throw conflict") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.post("/tim-contact-mgmt/contacts") {
                        contentType(Application.Json)
                        setBody(
                            Json.encodeToString(
                                Contact(
                                    mxid = "333", displayName = "Bob", inviteSettings = ContactInviteSettings(17)
                                )
                            )
                        )

                    }

                    response shouldHaveStatus InternalServerError
                    response.shouldHaveContentType(Application.Json.withCharset(Charsets.UTF_8))
                    response.bodyAsText() shouldEqualJson """{"errorCode":"500 Internal Server Error",
                        "errorMessage":"contact could not be created"}"""
                }
            }
        }

        /**
         * Tests for Delete "/tim-contact-mgmt/contacts/{id}"
         * */
        should("delete without id should throw bad request") {
            noAuthenticationNeeded {
                withCut {
                    val response = client.delete("/tim-contact-mgmt/contacts/")

                    response shouldHaveStatus BadRequest
                    response.shouldHaveContentType(Application.Json.withCharset(Charsets.UTF_8))
                    response.bodyAsText() shouldEqualJson """{"errorCode":"400 Bad Request","errorMessage":"missing parameter 'id'"}"""
                }
            }
        }

        should("delete unauthenticated should throw unauthorized") {
            givenUnauthenticatedUser {
                withCut {
                    val response = client.delete("/tim-contact-mgmt/contacts/5555")

                    response shouldHaveStatus Unauthorized
                    response.bodyAsText() shouldBe "UNAUTHORIZED"
                }
            }
        }

        should("delete without answers unauthorized if unauthorized") {
            givenUnauthenticatedUser {
                withCut {
                    val response = client.delete("/tim-contact-mgmt/contacts/5555")

                    response shouldHaveStatus Unauthorized
                    response.bodyAsText() shouldBe "UNAUTHORIZED"
                }
            }
        }

        should("delete should respond deleted") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.delete("/tim-contact-mgmt/contacts/5555")

                    response shouldHaveStatus NoContent
                    response.bodyAsText().shouldBeEmpty()
                }
            }
        }

        should("delete should respond not found when contact does not exist") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.delete("/tim-contact-mgmt/contacts/2222")

                    response shouldHaveStatus NotFound
                    response.shouldHaveContentType(Application.Json.withCharset(Charsets.UTF_8))
                    response.bodyAsText() shouldEqualJson """{"errorCode":"404 Not Found","errorMessage":"contact not found: 2222"}"""
                }
            }
        }

        /**
         * Tests for PUT "/tim-contact-mgmt/contacts"
         * */
        should("put should throw bad request with empty body") {
            noAuthenticationNeeded {
                withCut {
                    val response = client.put("/tim-contact-mgmt/contacts") {
                        contentType(Application.Json)
                        setBody("\"{}\"")
                    }

                    response shouldHaveStatus BadRequest
                    response.shouldHaveContentType(Application.Json.withCharset(Charsets.UTF_8))
                    response.bodyAsText() shouldEqualJson """{"errorCode":"400 Bad Request","errorMessage":"contact malformed"}"""
                }
            }
        }

        should("put should respond unauthorized if authorization fails") {
            givenUnauthenticatedUser {
                withCut {
                    val response = client.put("/tim-contact-mgmt/contacts") {
                        contentType(Application.Json)
                        setBody(
                            Json.encodeToString(
                                Contact(
                                    mxid = "4444", displayName = "Alice", inviteSettings = ContactInviteSettings(17)
                                )
                            )
                        )

                    }

                    response shouldHaveStatus Unauthorized
                    response.bodyAsText() shouldBe "UNAUTHORIZED"
                }
            }
        }

        should("put should respond updated") {
            givenAuthenticatedUser {
                withCut {
                    val contact = Contact(
                        mxid = "4444", displayName = "Alice", inviteSettings = ContactInviteSettings(17)
                    )
                    val response = client.put("/tim-contact-mgmt/contacts") {
                        contentType(Application.Json)
                        setBody(Json.encodeToString(contact))
                    }

                    response shouldHaveStatus OK
                    response.shouldHaveContentType(Application.Json.withCharset(Charsets.UTF_8))
                    response.bodyAsText() shouldEqualJson """{"displayName":"Alice","mxid":"4444","inviteSettings":
                        {"start":17,"end":null}}"""
                }
            }
        }

        should("put should throw NotFound") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.put("/tim-contact-mgmt/contacts") {
                        contentType(Application.Json)
                        setBody(
                            Json.encodeToString(
                                Contact(
                                    mxid = "333", displayName = "Bob", inviteSettings = ContactInviteSettings(17)
                                )
                            )
                        )

                    }

                    response shouldHaveStatus NotFound
                    response.bodyAsText() shouldEqualJson """{"errorCode":"404 Not Found","errorMessage":"contact not found: 333"}"""
                }
            }
        }

    }
})

