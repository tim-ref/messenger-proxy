/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.contactmgmt

import de.akquinet.timref.proxy.contactmgmt.authorization.MatrixAuthorizationService
import de.akquinet.timref.proxy.contactmgmt.database.ContactManagementService
import de.akquinet.timref.proxy.contactmgmt.model.Contact
import de.akquinet.timref.proxy.contactmgmt.model.ContactDTO
import de.akquinet.timref.proxy.rawdata.RawDataService
import de.akquinet.timref.proxy.rawdata.model.Operation
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

interface ContactRoutes {
    fun Route.apiRoutes()

}

private val log = KotlinLogging.logger { }

class ContactRoutesImpl(
    private val contactManagementService: ContactManagementService,
    private val rawDataService: RawDataService,
    private val matrixAuthorizationService: MatrixAuthorizationService
) : ContactRoutes {

    private val TIM_CONTACT_MGMT = "/tim-contact-mgmt"
    override fun Route.apiRoutes() {
        get(TIM_CONTACT_MGMT) {
            call.respondText(
                Json.encodeToString(contactManagementService.getInfo()), ContentType.Application.Json, HttpStatusCode.OK
            )
        }
        get("$TIM_CONTACT_MGMT/contacts") {
            (matrixAuthorizationService.authorize(call.request.headers)).let {
                if (it && call.request.headers["mxid"] != null) {
                    call.respond(
                        HttpStatusCode.OK,
                        Json.encodeToString(contactManagementService.getContacts(call.request.headers["mxid"]!!).map { contact -> contact.toContactDTO() })
                    )
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED")
                }
            }

        }
        get("$TIM_CONTACT_MGMT/contacts/{id}") {
            val approvedMxid = call.parameters["id"] ?: return@get call.respondText(
                status = HttpStatusCode.BadRequest,
                text = "Missing id"
            )
            (matrixAuthorizationService.authorize(call.request.headers)).let {
                if (it && call.request.headers["mxid"] != null) {
                    val contact = contactManagementService.getContact(call.request.headers["mxid"]!!, approvedMxid)
                    if (contact != null)
                        call.respond(HttpStatusCode.OK, Json.encodeToString(contact.toContactDTO()))
                    else {
                        call.respond(HttpStatusCode.UnprocessableEntity, "No Contact Found")

                    }
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED")
                }
            }

        }
        post("$TIM_CONTACT_MGMT/contacts") {
            val start = System.nanoTime()
            val contact = call.receive<ContactDTO>()
            (matrixAuthorizationService.authorize(call.request.headers)).let {
                var wasCreated: Contact? = null
                if (it && call.request.headers["mxid"] != null) {
                    if (contactManagementService.getContact(call.request.headers["mxid"]!!, contact.mxid) == null)
                        try {
                            wasCreated = contactManagementService.createContactSetting(call.request.headers["mxid"]!!, contact.toContactEntity(call.request.headers["mxid"]!!, null))
                        } catch (_: Exception) {
                            //no logging necessary
                        }
                    val status = if (wasCreated != null) HttpStatusCode.Created else HttpStatusCode.Conflict
                    val body = if (wasCreated != null) Json.encodeToString(wasCreated.toContactDTO()) else "Contact could not be created"
                    call.respond(status, body).also {
                        rawDataService.contactRawDataForward(
                            call.request,
                            status,
                            body.length.toLong(),
                            System.nanoTime() - start,
                            Operation.MP_INVITE_OUTSIDE_ORGANISATION_ADD_TO_CONTACT_MANAGEMENT_LIST
                        )
                    }

                } else {
                    call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED")
                }.also {
                    rawDataService.contactRawDataForward(
                        call.request,
                        HttpStatusCode.Unauthorized,
                        0,
                        System.nanoTime() - start,
                        Operation.MP_INVITE_OUTSIDE_ORGANISATION_ADD_TO_CONTACT_MANAGEMENT_LIST
                    )
                }
            }

        }
        delete("$TIM_CONTACT_MGMT/contacts/{id}") {
            val approvedMxid = call.parameters["id"] ?: return@delete call.respondText(
                status = HttpStatusCode.BadRequest,
                text = "Missing id"
            )
            (matrixAuthorizationService.authorize(call.request.headers)).let {
                if (it && call.request.headers["mxid"] != null) {
                    if (contactManagementService.deleteContactSetting(call.request.headers["mxid"]!!, approvedMxid)) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respondText(text = "contact does not exist", status = HttpStatusCode.UnprocessableEntity)
                    }
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED")
                }
            }

        }
        put("$TIM_CONTACT_MGMT/contacts") {
            val contact = call.receive<ContactDTO>()
            (matrixAuthorizationService.authorize(call.request.headers)).let {
                if (it && call.request.headers["mxid"] != null) {
                    val foundContact = contactManagementService.getContact(call.request.headers["mxid"]!!, contact.mxid)
                    if (foundContact != null) {
                        contactManagementService.updateContactSetting(call.request.headers["mxid"]!!, contact.toContactEntity(call.request.headers["mxid"]!!, foundContact.id.toString()))
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respondText(text = "contact does not exist", status = HttpStatusCode.UnprocessableEntity)
                    }
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED")
                }
            }

        }
    }

}
