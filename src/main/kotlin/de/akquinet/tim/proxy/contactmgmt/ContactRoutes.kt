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
package de.akquinet.tim.proxy.contactmgmt

import de.akquinet.tim.proxy.contactmgmt.authorization.MatrixAuthorizationService
import de.akquinet.tim.proxy.contactmgmt.database.ContactManagementService
import de.akquinet.tim.proxy.contactmgmt.model.ContactDTO
import de.akquinet.tim.proxy.rawdata.RawDataService
import de.akquinet.tim.proxy.rawdata.model.Operation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

fun interface ContactRoutes {
    fun Route.apiRoutes()
}

private val log = KotlinLogging.logger { }

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
            call.respondText(
                Json.encodeToString(contactManagementService.getInfo()), ContentType.Application.Json, HttpStatusCode.OK
            )
        }
        get("$TIM_CONTACT_MGMT/contacts") {
            getAllContacts(call)
        }
        get("$TIM_CONTACT_MGMT/contacts/{id}") {
            val approvedMxid = call.parameters["id"] ?: return@get call.respondText(
                status = HttpStatusCode.BadRequest,
                text = "Missing id"
            )

            getContactById(approvedMxid, call)
        }
        post("$TIM_CONTACT_MGMT/contacts") {
            val start = System.nanoTime()
            val contactDTO = call.receive<ContactDTO>()

            createContact(contactDTO, start, call)
        }
        delete("$TIM_CONTACT_MGMT/contacts/{id}") {
            val approvedMxid = call.parameters["id"] ?: return@delete call.respondText(
                status = HttpStatusCode.BadRequest,
                text = "Missing id"
            )
            deleteContactById(approvedMxid, call)

        }
        put("$TIM_CONTACT_MGMT/contacts") {
            updateContact(call)
        }
    }

    private suspend fun updateContact(call: ApplicationCall) {
        val contactDTO = call.receive<ContactDTO>()
        if (matrixAuthorizationService.authorize(call.request.headers)) {
            call.request.headers["mxid"]?.let { mxId ->
                contactManagementService.getContact(mxId, contactDTO.mxid)?.let { contact ->
                    contactManagementService.updateContactSetting(
                        mxId, contactDTO.toContactEntity(mxId, contact.id.toString())
                    )
                    call.respond(HttpStatusCode.OK)
                } ?: call.respondText(
                    text = "contact does not exist",
                    status = HttpStatusCode.UnprocessableEntity
                )
            } ?: call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED")
        } else {
            call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED")
        }
    }

    private suspend fun deleteContactById(
        approvedMxid: String,
        call: ApplicationCall
    ) {
        if (matrixAuthorizationService.authorize(call.request.headers)) {
            call.request.headers["mxid"]?.let { mxId ->
                if (contactManagementService.deleteContactSetting(mxId, approvedMxid)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respondText(text = "contact does not exist", status = HttpStatusCode.UnprocessableEntity)
                }
            } ?: call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED")
        } else {
            call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED")
        }
    }

    private suspend fun createContact(
        contactDTO: ContactDTO,
        start: Long,
        call: ApplicationCall
    ) {
        if (matrixAuthorizationService.authorize(call.request.headers)) {
            call.request.headers["mxid"]?.let { mxId ->
                val createdContact = if (contactManagementService.getContact(mxId, contactDTO.mxid) == null) {
                    try {
                        contactManagementService.createContactSetting(mxId, contactDTO.toContactEntity(mxId, null))
                    } catch (_: Exception) {
                        null // no logging necessary
                    }
                } else {
                    null
                }

                val (status, body) = createdContact?.let {
                    Pair(HttpStatusCode.Created, Json.encodeToString(it.toContactDTO()))
                } ?: Pair(HttpStatusCode.Conflict, "Contact could not be created")

                call.respond(status, body).also {
                    rawDataService.contactRawDataForward(
                        call.request,
                        status,
                        body.length.toLong(),
                        System.nanoTime() - start,
                        Operation.MP_INVITE_OUTSIDE_ORGANISATION_ADD_TO_CONTACT_MANAGEMENT_LIST
                    )
                }

            } ?: call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED").also {
                rawDataService.contactRawDataForward(
                    call.request,
                    HttpStatusCode.Unauthorized,
                    0,
                    System.nanoTime() - start,
                    Operation.MP_INVITE_OUTSIDE_ORGANISATION_ADD_TO_CONTACT_MANAGEMENT_LIST
                )
            }
        } else {
            call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED")
        }
    }

    private suspend fun getContactById(
        approvedMxid: String,
        call: ApplicationCall
    ) {
        if (matrixAuthorizationService.authorize(call.request.headers)) {
            call.request.headers["mxid"]?.let { mxId ->
                contactManagementService.getContact(mxId, approvedMxid)?.let { contact ->
                    call.respond(HttpStatusCode.OK, Json.encodeToString(contact.toContactDTO()))
                } ?: call.respond(HttpStatusCode.UnprocessableEntity, "No Contact Found")
            } ?: call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED")
        } else {
            call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED")
        }
    }

    private suspend fun getAllContacts(call: ApplicationCall) {
        if (matrixAuthorizationService.authorize(call.request.headers)) {
            call.request.headers["mxid"]?.let { mxid ->
                call.respond(
                    HttpStatusCode.OK,
                    Json.encodeToString(
                        contactManagementService.getContacts(mxid)
                            .map { contact -> contact.toContactDTO() }
                    )
                )
            } ?: call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED")
        } else {
            call.respond(HttpStatusCode.Unauthorized, "M_UNAUTHORIZED")
        }
    }
}
