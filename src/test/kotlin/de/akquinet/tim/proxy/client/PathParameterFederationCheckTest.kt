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

import de.akquinet.tim.ErrorResponse
import de.akquinet.tim.proxy.bs.BerechtigungsstufeEinsService
import de.akquinet.tim.proxy.federation.FederationList
import de.akquinet.tim.proxy.mocks.FederationListCacheMock
import de.akquinet.tim.shouldEqualJsonMatrixStandard
import io.kotest.assertions.ktor.client.shouldHaveContentType
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.flow.update

class PathParameterFederationCheckTest : ShouldSpec({

    fun io.ktor.server.application.Application.testModule() {
        val federationListCacheMock = FederationListCacheMock()
        federationListCacheMock.domains.update {
            it + FederationList.FederationDomain(
                domain = "federated",
                isInsurance = true,
                telematikID = "telematik"
            )
        }
        val berechtigungsstufeEinsService = BerechtigungsstufeEinsService(federationListCacheMock)

        install(ContentNegotiation) {
            json()
        }

        routing {
            install(PathParameterFederationCheck) {
                service = berechtigungsstufeEinsService
            }
            get("/{serverName}") {
                call.respond("I'm a resource")
            }
        }
    }

    should("reject requests for media from unfederated servers") {
        testApplication {
            application { testModule() }

            val response = client.get("/unfederated")

            response shouldHaveStatus BadRequest
            response.shouldHaveContentType(Application.Json.withCharset(Charsets.UTF_8))
            response.bodyAsText() shouldEqualJsonMatrixStandard ErrorResponse(
                errcode = "M_SERVER_NOT_TRUSTED",
                error = "Server 'unfederated' is not part of federation."
            )
        }
    }

    should("allow requests for resources of federated servers") {
        testApplication {
            application { testModule() }

            val response = client.get("/federated")

            response.bodyAsText() shouldBe "I'm a resource"
        }
    }

})
