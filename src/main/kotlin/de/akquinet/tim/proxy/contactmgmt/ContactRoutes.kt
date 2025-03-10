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
package de.akquinet.tim.proxy.contactmgmt

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.Contact
import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.Contacts
import de.akquinet.tim.proxy.authorization.MatrixAuthorizationService
import de.akquinet.tim.proxy.contactmgmt.database.ContactManagementService
import de.akquinet.tim.proxy.contactmgmt.model.ContactManagementError
import de.akquinet.tim.proxy.extensions.*
import de.akquinet.tim.proxy.rawdata.RawDataService
import de.akquinet.tim.proxy.rawdata.model.Operation
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val log = KotlinLogging.logger {}


fun interface ContactRoutes {
    fun Route.apiRoutes()
}

class ContactRoutesImpl(
    private val contactManagementService: ContactManagementService,
    private val rawDataService: RawDataService,
    private val matrixAuthorizationService: MatrixAuthorizationService
) : ContactRoutes {

    companion object {
        private const val TIM_CONTACT_MGMT = "/tim-contact-mgmt"
    }

    private suspend fun <R> Either<ContactManagementError, R>.handle(
        call: ApplicationCall,
        handleRight: suspend ApplicationCall.(R) -> Unit,
    ) =
        onLeft { error ->
            when (error) {
                is ContactManagementError.Unauthorized -> call.unauthorized(error.message)
                is ContactManagementError.MissingParameter -> call.badRequest(error.message)
                is ContactManagementError.ContactNotFound -> call.notFound(error.message)
                is ContactManagementError.ContactMalformed -> call.badRequest(error.message)
                is ContactManagementError.ContactAlreadyExists -> call.internalServerError(error.message)
                is ContactManagementError.ContactCouldNotBeCreated -> call.internalServerError(error.message)
                is ContactManagementError.ContactCouldNotBeUpdated -> call.internalServerError(error.message)
            }
        }.onRight { result -> call.handleRight(result) }

    override fun Route.apiRoutes() {

        get(TIM_CONTACT_MGMT) {
            call.respond(OK, contactManagementService.getInfo())
        }

        get("$TIM_CONTACT_MGMT/contacts") {
            val requestHeaders = call.request.headers

            matrixAuthorizationService.authorize(requestHeaders)
                .mapLeft { ContactManagementError.Unauthorized(it) }
                .handle(call) { mxid ->
                    val contactList = contactManagementService.findContactsOf(mxid).map { it.toDto() }
                    val contacts = Contacts(contactList)
                    respond(OK, contacts)
                }
        }

        get("$TIM_CONTACT_MGMT/contacts/") {
            ContactManagementError.MissingParameter("id").left().handle(call) {}
        }

        get("$TIM_CONTACT_MGMT/contacts/{id}") {
            either {
                val approvedMxid = ensureNotNull(call.parameters["id"]) {
                    ContactManagementError.MissingParameter("id")
                }

                val requestHeaders = call.request.headers

                val mxid = matrixAuthorizationService.authorize(requestHeaders).mapLeft {
                    ContactManagementError.Unauthorized(it)
                }.bind()

                val contact = ensureNotNull(contactManagementService.getContact(mxid, approvedMxid)) {
                    ContactManagementError.ContactNotFound(mxid, approvedMxid)
                }

                contact.toDto()
            }.handle(call) {
                respond(OK, it)
            }
        }

        post("$TIM_CONTACT_MGMT/contacts") {
            val start = System.nanoTime()

            either {
                val contact = Either.catch {
                    call.receive<Contact>()
                }.mapLeft {
                    ContactManagementError.ContactMalformed(it)
                }.bind()

                val mxid = matrixAuthorizationService.authorize(call.request.headers).mapLeft {
                    ContactManagementError.Unauthorized(it)
                }.onLeft {
                    rawDataService.contactRawDataForward(
                        call.request,
                        Unauthorized,
                        0,
                        System.nanoTime() - start,
                        Operation.MP_INVITE_OUTSIDE_ORGANISATION_ADD_TO_CONTACT_MANAGEMENT_LIST
                    )
                }.bind()

                ensure(contactManagementService.getContact(mxid, contact.mxid) == null) {
                    ContactManagementError.ContactAlreadyExists(mxid, contact.mxid)
                }

                val created = Either.catch {
                    contactManagementService.addContactTo(
                        ownerMxid = mxid, contactEntity = contact.toEntity(
                            ownerId = mxid, uuid = null
                        )
                    )
                }.mapLeft { throwable ->
                    ContactManagementError.ContactCouldNotBeCreated(mxid, throwable)
                }.bind()

                ensureNotNull(created) {
                    ContactManagementError.ContactCouldNotBeCreated(mxid)
                }

                created.toDto()
            }.handle(call) { contactDto ->
                rawDataService.contactRawDataForward(
                    call.request,
                    OK,
                    Json.encodeToString(contactDto).length.toLong(),
                    System.nanoTime() - start,
                    Operation.MP_INVITE_OUTSIDE_ORGANISATION_ADD_TO_CONTACT_MANAGEMENT_LIST
                )

                respond(OK, contactDto)
            }
        }

        delete("$TIM_CONTACT_MGMT/contacts/") {
            ContactManagementError.MissingParameter("id").left().handle(call) {}
        }

        delete("$TIM_CONTACT_MGMT/contacts/{id}") {
            either {
                val approvedMxid = ensureNotNull(call.parameters["id"]) {
                    ContactManagementError.MissingParameter("id")
                }

                val requestHeaders = call.request.headers

                val mxid = matrixAuthorizationService.authorize(requestHeaders)
                    .mapLeft { ContactManagementError.Unauthorized(it) }.bind()

                ensure(contactManagementService.deleteContactSetting(mxid, approvedMxid)) {
                    ContactManagementError.ContactNotFound(mxid, approvedMxid)
                }
            }.handle(call) {
                respond(NoContent)
            }
        }

        put("$TIM_CONTACT_MGMT/contacts") {
            either {
                val contact = Either.catch {
                    call.receive<Contact>()
                }.mapLeft {
                    ContactManagementError.ContactMalformed(it)
                }.bind()

                val requestHeaders = call.request.headers

                val mxid = matrixAuthorizationService.authorize(requestHeaders).mapLeft {
                    ContactManagementError.Unauthorized(it)
                }.bind()

                val foundContact = ensureNotNull(contactManagementService.getContact(mxid, contact.mxid)) {
                    ContactManagementError.ContactNotFound(mxid, contact.mxid)
                }

                val update = contactManagementService.updateContactSetting(
                    ownerMxid = mxid, contactEntity = contact.toEntity(
                        ownerId = mxid, uuid = foundContact.id.toString()
                    )
                )

                ensure(update) {
                    log.error { "Could not update ContactEntity" }
                    ContactManagementError.ContactCouldNotBeUpdated(mxid)
                }

                ensureNotNull(contactManagementService.getContact(mxid, contact.mxid)) {
                    log.error { "Could not find updated ContactEntity" }
                    ContactManagementError.ContactCouldNotBeUpdated(mxid)
                }.toDto()
            }.handle(call) {
                respond(OK, it)
            }
        }
    }
}
