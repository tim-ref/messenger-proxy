/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.net.URL

private val log = KotlinLogging.logger { }

interface VZDPublicIDCheck {
    suspend fun areMXIDsPublic(inviter: String, invited: String): Boolean

}

class VZDPublicIDCheckImpl(
    private val httpClient: HttpClient,
    private val regServiceConfig: ProxyConfiguration.RegistrationServiceConfiguration
    ) : VZDPublicIDCheck {
    override suspend fun areMXIDsPublic(inviter: String, invited: String): Boolean {
        val invitePermissionUrl = URL(
            regServiceConfig.baseUrl + ":" + regServiceConfig.servicePort + regServiceConfig.invitePermissionCheckEndpoint
        ).toString()
        val response = httpClient.post(invitePermissionUrl) {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(Pair(inviter, invited)))
        }
        return response.status == HttpStatusCode.OK
    }
}

