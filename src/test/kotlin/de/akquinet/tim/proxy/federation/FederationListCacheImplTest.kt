/*
 * Copyright © 2023 - 2026 akquinet GmbH (https://www.akquinet.de)
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
package de.akquinet.tim.proxy.federation

import de.akquinet.tim.proxy.FileCacheImpl.RequestFileResult
import de.akquinet.tim.proxy.ProxyConfiguration
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import okio.fakefilesystem.FakeFileSystem

/** The federation list is polled continuously, so every response path must release its body. */
class FederationListCacheImplTest :
  ShouldSpec({
    val regServiceConfig =
      ProxyConfiguration.RegistrationServiceConfiguration(
        baseUrl = "http://localhost",
        servicePort = "8070",
        healthPort = "8071",
        readinessEndpoint = "/actuator/health/readiness",
        federationListEndpoint = "/backend/federation",
        wellKnownSupportEndpoint = "/backend/well-known-support",
      )
    val federationListConfig =
      ProxyConfiguration.FederationListCacheConfiguration(
        "federationList",
        "federationList/federationList.json",
        "federationList/federationList-meta.json",
        1,
      )

    fun cacheRespondingWith(status: HttpStatusCode, body: String) =
      FederationListCacheImpl(
        federationListConfig,
        regServiceConfig,
        HttpClient(MockEngine { respond(body, status) }),
        FakeFileSystem(),
      )

    should("report a not modified response and consume its body") {
      val cut = cacheRespondingWith(HttpStatusCode.NotModified, "")
      cut.requestFile(version = "1").shouldBeInstanceOf<RequestFileResult.NotModified<*>>()
    }

    should("report an unexpected response and consume its body") {
      val cut = cacheRespondingWith(HttpStatusCode.InternalServerError, "kaputt")
      cut.requestFile(version = "1").shouldBeInstanceOf<RequestFileResult.Error<*>>()
    }
  })
