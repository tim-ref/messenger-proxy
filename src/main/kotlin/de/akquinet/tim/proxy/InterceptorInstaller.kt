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

package de.akquinet.tim.proxy

import de.akquinet.tim.proxy.transformer.GetVersionsTransformer
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponseContainer
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.fullPath

class InterceptorInstaller(val httpClient: HttpClient) {

    fun install() {
        httpClient.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, body) ->
            when(context.request.url.fullPath) {
                GetVersionsTransformer.path ->  {
                    if (GetVersionsTransformer.applicableStates.contains(context.response.status)) {
                        proceedWith(HttpResponseContainer(info, GetVersionsTransformer.transform(body)))
                    }else {
                        return@intercept
                    }
                }
                else -> return@intercept
            }
        }
    }
}
