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

import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.Contact
import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.Contacts
import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.Error
import de.akquinet.tim.proxy.contactmgmt.authorization.MatrixAuthorizationService
import de.akquinet.tim.proxy.contactmgmt.database.ContactManagementService
import de.akquinet.tim.proxy.contactmgmt.model.ContactEntity
import de.akquinet.tim.proxy.extensions.*
import de.akquinet.tim.proxy.rawdata.RawDataService
import de.akquinet.tim.proxy.rawdata.model.Operation
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
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

    override fun Route.apiRoutes() {

        get(TIM_CONTACT_MGMT) {
            call.respond(
                OK, contactManagementService.getInfo()
            )
        }

        get("$TIM_CONTACT_MGMT/contacts") {
            val requestHeaders = call.request.headers
            val mxid = requestHeaders["mxid"]

            if (!matrixAuthorizationService.authorize(requestHeaders) || mxid == null) return@get call.unauthorized()

            val contactList = contactManagementService.findContactsOf(mxid).map { it.toDto() }
            val contacts = Contacts(contactList)
            call.respond(OK, contacts)
        }

        get("$TIM_CONTACT_MGMT/contacts/") {
            call.badRequestMissingParameter("id")
        }

        get("$TIM_CONTACT_MGMT/contacts/{id}") {
            val approvedMxid = call.parameters["id"] ?: return@get call.badRequestMissingParameter("id")
            val requestHeaders = call.request.headers
            val mxid = requestHeaders["mxid"]

            if (!matrixAuthorizationService.authorize(requestHeaders) || mxid == null) return@get call.unauthorized()

            contactManagementService.getContact(mxid, approvedMxid)?.toDto()?.let {
                call.respond(OK, it)
            } ?: call.notFound()
        }

        post("$TIM_CONTACT_MGMT/contacts") {
            val start = System.nanoTime()

            val contact = try {
                call.receive<Contact>()
            } catch (e: Exception) {
                return@post call.badRequestEmptyOrIncorrectBody()
            }

            val mxid = call.request.headers["mxid"]
            if (!matrixAuthorizationService.authorize(call.request.headers) || mxid == null) {
                call.unauthorized().also {
                    rawDataService.contactRawDataForward(
                        call.request,
                        Unauthorized,
                        0,
                        System.nanoTime() - start,
                        Operation.MP_INVITE_OUTSIDE_ORGANISATION_ADD_TO_CONTACT_MANAGEMENT_LIST
                    )
                }
                return@post
            }

            var wasCreated: ContactEntity? = null
            if (contactManagementService.getContact(mxid, contact.mxid) == null) try {
                wasCreated = contactManagementService.addContactTo(
                    ownerMxid = mxid, contactEntity = contact.toEntity(
                        ownerId = mxid, uuid = null
                    )
                )
            } catch (_: Exception) {
                //no logging necessary
            }

            val status = if (wasCreated != null) OK else InternalServerError
            wasCreated?.toDto()?.let { contactDto ->
                call.respond(status, contactDto).also {
                    rawDataService.contactRawDataForward(
                        call.request,
                        status,
                        Json.encodeToString(contactDto).length.toLong(),
                        System.nanoTime() - start,
                        Operation.MP_INVITE_OUTSIDE_ORGANISATION_ADD_TO_CONTACT_MANAGEMENT_LIST
                    )
                }
            } ?: call.respond(
                status = status, message = Error(
                    errorCode = status.toString(), errorMessage = "Contact could not be created"
                )
            )
        }

        delete("$TIM_CONTACT_MGMT/contacts/") {
            call.badRequestMissingParameter("id")
        }

        delete("$TIM_CONTACT_MGMT/contacts/{id}") {
            val approvedMxid = call.parameters["id"] ?: return@delete call.badRequestMissingParameter("id")
            val requestHeaders = call.request.headers
            val mxid = requestHeaders["mxid"]

            if (!matrixAuthorizationService.authorize(requestHeaders) || mxid == null) return@delete call.unauthorized()

            if (contactManagementService.deleteContactSetting(mxid, approvedMxid)) {
                call.respond(NoContent)
            } else {
                call.notFound()
            }
        }

        put("$TIM_CONTACT_MGMT/contacts") {
            val contact = try {
                call.receive<Contact>()
            } catch (e: Exception) {
                return@put call.badRequestEmptyOrIncorrectBody()
            }

            val requestHeaders = call.request.headers
            val mxid = requestHeaders["mxid"]

            if (!matrixAuthorizationService.authorize(requestHeaders) || mxid == null) return@put call.unauthorized()

            contactManagementService.getContact(mxid, contact.mxid)?.let { foundContact ->
                if (contactManagementService.updateContactSetting(
                        ownerMxid = mxid, contactEntity = contact.toEntity(
                            ownerId = mxid, uuid = foundContact.id.toString()
                        )
                    )
                ) {
                    contactManagementService.getContact(mxid, contact.mxid)?.let {
                        call.respond(OK, it.toDto())
                    } ?: run {
                        log.error { "Could not find updated ContactEntity" }
                        call.internalServerError("Contact could not be updated")
                    }
                } else {
                    log.error { "Could not update ContactEntity" }
                    call.internalServerError("Contact could not be updated")
                }
            } ?: call.notFound()
        }
    }
}
