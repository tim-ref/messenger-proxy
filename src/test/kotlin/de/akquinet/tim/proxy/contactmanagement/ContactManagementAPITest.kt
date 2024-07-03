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

package de.akquinet.tim.proxy.contactmanagement

import de.akquinet.tim.proxy.contactmgmt.ContactRoutesImpl
import de.akquinet.tim.proxy.contactmgmt.authorization.MatrixAuthorizationService
import de.akquinet.tim.proxy.contactmgmt.contactApiServer
import de.akquinet.tim.proxy.contactmgmt.model.Contact
import de.akquinet.tim.proxy.contactmgmt.model.ContactDTO
import de.akquinet.tim.proxy.contactmgmt.model.ContactManagementInfo
import de.akquinet.tim.proxy.contactmgmt.model.InviteSettings
import de.akquinet.tim.proxy.mocks.ContactManagementStub
import de.akquinet.tim.proxy.mocks.RawDataServiceStub
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

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

    fun givenAuthenticatedUser(block: () -> Unit) {
        runBlocking {
            coEvery { matrixAuthorizationServiceMock.authorize(any()) } returns true
            block()
            coVerify(exactly = 1) { matrixAuthorizationServiceMock.authorize(any()) }
        }
    }

    fun givenUnauthenticatedUser(block: () -> Unit) {
        runBlocking {
            coEvery { matrixAuthorizationServiceMock.authorize(any()) } returns false
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
                    response.status.shouldBe(HttpStatusCode.OK)
                    response.bodyAsText().shouldBe(
                        Json.encodeToString(
                            ContactManagementInfo(
                                title = "Contact Management des TI-Messengers",
                                description = "Contact Management des TI-Messengers. Betreiber: <Betreibername>",
                                contact = "Kontaktinformationen",
                                version = "1.0.0"
                            )
                        )
                    )
                }
            }
        }

        /**
         * Tests for GET "/tim-contact-mgmt/contacts"
         * */
        should("should throw unauthorized when autorization fails") {
            givenUnauthenticatedUser {
                withCut {


                    val response = client.get("/tim-contact-mgmt/contacts") {
                        headers.append("mxid", "1234")
                        headers.append("authorization", "Bearer SuperToken")
                    }
                    response.status.shouldBe(HttpStatusCode.Unauthorized)
                    response.bodyAsText().shouldBe("M_UNAUTHORIZED")
                }
            }

        }
        should("should return emptyList") {
            givenAuthenticatedUser {
                withCut {

                    val response = client.get("/tim-contact-mgmt/contacts") {
                        headers.append("mxid", "1234")
                        headers.append("authorization", "Bearer SuperToken")
                    }

                    response.status.shouldBe(HttpStatusCode.OK)
                    response.bodyAsText().shouldBe(Json.encodeToString(emptyList<Contact>()))
                }
            }
        }
        should("should return non-empty List") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.get("/tim-contact-mgmt/contacts") {
                        headers.append("mxid", "12345")
                        headers.append("authorization", "Bearer SuperToken")
                    }
                    val list =
                        listOf(
                            Contact(
                                id = UUID.fromString("8f0874ee-8db6-4056-baf8-eeaa1c23aed0"),
                                ownerId = "owner4",
                                approvedId = "12345",
                                displayName = "Alice",
                                inviteStart = 17
                            ).toContactDTO()
                        )
                    response.status.shouldBe(HttpStatusCode.OK)
                    response.bodyAsText().shouldBe(Json.encodeToString(list))
                }
            }
        }

        /**
         * Tests for GET "/tim-contact-mgmt/contacts/{mxid}"
         * */
        should("get someone else's mxid should throw unauthorized with authenticated user") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.get("/tim-contact-mgmt/contacts/5555")
                    response.status.shouldBe(HttpStatusCode.Unauthorized)
                    response.bodyAsText().shouldBe("M_UNAUTHORIZED")
                }
            }
        }
        should("get someone else's mxid should throw unauthorized") {
            givenUnauthenticatedUser {
                withCut {
                    val response = client.get("/tim-contact-mgmt/contacts/5555")
                    response.status.shouldBe(HttpStatusCode.Unauthorized)
                    response.bodyAsText().shouldBe("M_UNAUTHORIZED")
                }
            }
        }

        should("get all should not be found") {
            noAuthenticationNeeded {
                withCut {
                    val response = client.get("/tim-contact-mgmt/contacts/")
                    response.status.shouldBe(HttpStatusCode.NotFound)
                }
            }
        }
        should("get one should return unprocessable") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.get("/tim-contact-mgmt/contacts/5555") {
                        headers.append("mxid", "1234")
                        headers.append("authorization", "Bearer SuperToken")
                    }
                    response.status.shouldBe(HttpStatusCode.UnprocessableEntity)
                    response.bodyAsText().shouldBe("No Contact Found")
                }
            }
        }
        should("get one should return Alice") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.get("/tim-contact-mgmt/contacts/4444") {
                        headers.append("mxid", "1234")
                        headers.append("authorization", "Bearer SuperToken")
                    }
                    response.status.shouldBe(HttpStatusCode.OK)
                    response.bodyAsText().shouldBe(
                        Json.encodeToString(
                            Contact(
                                id = UUID.fromString("8f0874ee-8db6-4056-baf8-eeaa1c23aed0"),
                                ownerId = "1234",
                                approvedId = "4444",
                                displayName = "Alice",
                                inviteStart = 17
                            ).toContactDTO()
                        )
                    )
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
                        setBody(
                            Json.encodeToString(
                                "{}"
                            )
                        )
                        contentType(ContentType.Application.Json)
                    }
                    response.status.shouldBe(HttpStatusCode.BadRequest)
                }
            }
        }

        should("post should throw unauthorized when user is not authenticated") {
            givenUnauthenticatedUser {
                withCut {
                    val response = client.post("/tim-contact-mgmt/contacts") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            Json.encodeToString(
                                ContactDTO(
                                    mxid = "333",
                                    displayName = "Bob",
                                    inviteSettings = InviteSettings(17)
                                )
                            )
                        )

                    }
                    response.status.shouldBe(HttpStatusCode.Unauthorized)
                    response.bodyAsText().shouldBe("M_UNAUTHORIZED")
                }
            }
        }
        should("post should respond created") {
            givenAuthenticatedUser {
                withCut {
                    val contact = ContactDTO(
                        mxid = "4445",
                        displayName = "Alice",
                        inviteSettings = InviteSettings(17)
                    )
                    val response = client.post("/tim-contact-mgmt/contacts") {
                        headers.append("mxid", "1234")
                        headers.append("authorization", "Bearer SuperToken")
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(contact))
                    }
                    response.status.shouldBe(HttpStatusCode.Created)
                    response.bodyAsText().shouldBe(Json.encodeToString(contact))
                }
            }
        }
        should("post should throw conflict") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.post("/tim-contact-mgmt/contacts") {
                        headers.append("mxid", "1234")
                        headers.append("authorization", "Bearer SuperToken")
                        contentType(ContentType.Application.Json)
                        setBody(
                            Json.encodeToString(
                                ContactDTO(
                                    mxid = "333",
                                    displayName = "Bob",
                                    inviteSettings = InviteSettings(17)
                                )
                            )
                        )

                    }
                    response.status.shouldBe(HttpStatusCode.Conflict)
                    response.bodyAsText().shouldBe("Contact could not be created")
                }
            }
        }
        /**
         * Tests for Delete "/tim-contact-mgmt/contacts/{id}"
         * */
        should("delete without id should throw bad request") {
            noAuthenticationNeeded {
                withCut {
                    val response = client.delete("/tim-contact-mgmt/contacts/") {
                        contentType(ContentType.Application.Json)
                    }
                    response.status.shouldBe(HttpStatusCode.NotFound)
                }
            }
        }

        should("delete someone else's mxidshould throw unauthorized") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.delete("/tim-contact-mgmt/contacts/5555") {
                        contentType(ContentType.Application.Json)
                    }
                    response.status.shouldBe(HttpStatusCode.Unauthorized)
                    response.bodyAsText().shouldBe("M_UNAUTHORIZED")
                }
            }
        }
        should("delete should respond deleted") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.delete("/tim-contact-mgmt/contacts/5555") {
                        headers.append("mxid", "1234")
                        headers.append("authorization", "Bearer SuperToken")
                        contentType(ContentType.Application.Json)
                    }
                    response.status.shouldBe(HttpStatusCode.OK)
                    response.bodyAsText().shouldBeEmpty()

                }
            }
        }
        should("delete should respond unprocessable when contact does not exist") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.delete("/tim-contact-mgmt/contacts/2222") {
                        headers.append("mxid", "1234")
                        headers.append("authorization", "Bearer SuperToken")
                        contentType(ContentType.Application.Json)
                    }
                    response.status.shouldBe(HttpStatusCode.UnprocessableEntity)
                    response.bodyAsText().shouldBe("contact does not exist")
                }
            }
        }

        /**
         * Tests for POST "/tim-contact-mgmt/contacts"
         * */
        should("put should throw bad request") {
            noAuthenticationNeeded {
                withCut {
                    val response = client.put("/tim-contact-mgmt/contacts") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            Json.encodeToString(
                                "{}"
                            )
                        )
                    }
                    response.status.shouldBe(HttpStatusCode.BadRequest)
                }
            }
        }

        should("put should throw unauthorized") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.put("/tim-contact-mgmt/contacts") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            Json.encodeToString(
                                ContactDTO(
                                    mxid = "4444",
                                    displayName = "Alice",
                                    inviteSettings = InviteSettings(17)
                                )
                            )
                        )

                    }
                    response.status.shouldBe(HttpStatusCode.Unauthorized)
                    response.bodyAsText().shouldBe("M_UNAUTHORIZED")
                }
            }
        }
        should("put should respond updated") {
            givenAuthenticatedUser {
                withCut {
                    val contact = ContactDTO(
                        mxid = "4444",
                        displayName = "Alice",
                        inviteSettings = InviteSettings(17)
                    )
                    val response = client.put("/tim-contact-mgmt/contacts") {
                        headers.append("mxid", "1234")
                        headers.append("authorization", "Bearer SuperToken")
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(contact))
                    }
                    response.status.shouldBe(HttpStatusCode.OK)
                }
            }
        }
        should("put should throw unprocessable") {
            givenAuthenticatedUser {
                withCut {
                    val response = client.put("/tim-contact-mgmt/contacts") {
                        headers.append("mxid", "1234")
                        headers.append("authorization", "Bearer SuperToken")
                        contentType(ContentType.Application.Json)
                        setBody(
                            Json.encodeToString(
                                ContactDTO(
                                    mxid = "333",
                                    displayName = "Bob",
                                    inviteSettings = InviteSettings(17)
                                )
                            )
                        )

                    }
                    response.status.shouldBe(HttpStatusCode.UnprocessableEntity)
                    response.bodyAsText().shouldBe("contact does not exist")
                }
            }
        }

    }
})

