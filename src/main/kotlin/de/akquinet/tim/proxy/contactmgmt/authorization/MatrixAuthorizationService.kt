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

package de.akquinet.tim.proxy.contactmgmt.authorization

import io.ktor.http.*

fun interface MatrixAuthorizationService {
    suspend fun authorize(headers: Headers): Boolean
}
class MatrixAuthorizationServiceImpl(private val matrixOpenIdClient: MatrixOpenIdClient):
    MatrixAuthorizationService {

    override suspend fun authorize(headers: Headers): Boolean {
        val mxid = headers["mxid"] ?: return false
        val auth = headers["authorization"] ?: return false
        val (token) = Regex("^Bearer (.+)").find(auth)?.destructured ?: return false

        val authenticatedMxid = matrixOpenIdClient.authenticatedUser(token)
        return mxid == authenticatedMxid
    }


}