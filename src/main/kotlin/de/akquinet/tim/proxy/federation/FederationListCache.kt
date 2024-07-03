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

package de.akquinet.tim.proxy.federation

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import de.akquinet.tim.proxy.FileCacheImpl
import de.akquinet.tim.proxy.ProxyConfiguration
import okio.FileSystem
import okio.Path.Companion.toPath
import java.net.URL
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface FederationListCache {
    val domains: StateFlow<Set<String>>
}

class FederationListCacheImpl(
    private val config: ProxyConfiguration.FederationListCacheConfiguration,
    private val regServiceConfig: ProxyConfiguration.RegistrationServiceConfiguration,
    httpClient: HttpClient,
    fileSystem: FileSystem,
) : FederationListCache, FileCacheImpl<FederationList>(
    baseDirectory = config.baseDirectory.toPath(),
    file = config.file.toPath(),
    metaFile = config.metaFile.toPath(),
    fileSystem = fileSystem
) {
    private val httpClient = httpClient.config {
        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors()
            exponentialDelay(maxDelayMs = 10.minutes.inWholeMilliseconds)
        }
    }

    private val _domains = MutableStateFlow<Set<String>>(emptySet())
    override val domains: StateFlow<Set<String>> = _domains.asStateFlow()

    override suspend fun start() = coroutineScope {
        launch {
            cacheValue
                .mapNotNull { value -> value?.domainList?.map { it.domain }?.toSet() }
                .collect { _domains.value = it }
        }
        super.start()
    }

    override fun nextUpdate(lastWasError: Boolean): Instant =
        if (lastWasError) Clock.System.now() + 5.minutes + Random.nextInt(-120, 120).seconds
        else Clock.System.now() + 1.hours + Random.nextInt(-360, 360).seconds


    override suspend fun parseFile(content: String): FederationList {
        return Json.decodeFromString<FederationList>(content)
    }


    override suspend fun requestFile(version: String?): RequestFileResult<FederationList> {
        val federationListUrl = URL(regServiceConfig.baseUrl + ":" + regServiceConfig.servicePort + regServiceConfig.federationListEndpoint).toString()
        val response = httpClient.get(federationListUrl) {}
        return when (val status = response.status) {
            HttpStatusCode.OK -> {
                val content = response.bodyAsText()
                RequestFileResult.NewFile(content) { it.version.toString() }
            }

            HttpStatusCode.NoContent -> RequestFileResult.NotModified()

            else -> {
                val content = response.bodyAsText()
                RequestFileResult.Error("receiving new federation list with an unexpected response: status=$status body=$content")
            }
        }
    }
}

class FederationListParseException(message: String) : RuntimeException(message)

@Serializable
data class FederationList(
    @SerialName("version") val version: Int,
    @SerialName("domainList") val domainList: List<FederationDomain>,
) {
    @Serializable
    data class FederationDomain(
        @SerialName("domain") val domain: String,
        @SerialName("isInsurance") val isInsurance: Boolean,
        @SerialName("telematikID") val telematikID: String,
    )
}
