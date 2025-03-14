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
package de.akquinet.tim.proxy.authorization

import de.akquinet.tim.proxy.ProxyConfiguration
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

class MatrixOpenIdClient(
    private val httpClient: HttpClient,
    private val inboundProxyConfiguration: ProxyConfiguration.InboundProxyConfiguration
) {

    suspend fun authenticatedUser(token: String): UserAuthenticationResult {
        val openIdResponse = sendOpenIdRequest(token)
        return when (openIdResponse.status) {
            HttpStatusCode.OK -> {
                val responseBody = openIdResponse.call.response.bodyAsText()
                val mxid = Json.decodeFromString<SuccessfulOpenIdResponse>(responseBody).sub
                UserAuthenticationResult.Success(token, mxid)
            }

            else -> UserAuthenticationResult.Failure(token)
        }
    }

    private suspend fun sendOpenIdRequest(token: String): HttpResponse = httpClient.get(buildOpenIdUri(token))

    private fun buildOpenIdUri(token: String): String =
        inboundProxyConfiguration.let {
            "${it.homeserverUrl}/_matrix/federation/v1/openid/userinfo?access_token=${token}"
        }
}
