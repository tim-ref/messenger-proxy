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
package de.akquinet.tim.proxy.mocks

import de.akquinet.tim.proxy.rawdata.RawDataService
import de.akquinet.tim.proxy.rawdata.model.Operation
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.request.*

class RawDataServiceStub : RawDataService {
    override suspend fun serverRawDataForward(request: ApplicationRequest, response: HttpResponse, duration: Long, timOperation: Operation, sizeOut: Int) {
    }

    override suspend fun contactRawDataForward(request: ApplicationRequest, statusCode: HttpStatusCode, responseLength: Long, duration: Long, timOperation: Operation) {
    }

    override suspend fun clientRawDataForward(requestHeaders: Headers, responseCode: Int, duration: Long, timOperation: Operation, sizeOut: Int) {
    }


}
