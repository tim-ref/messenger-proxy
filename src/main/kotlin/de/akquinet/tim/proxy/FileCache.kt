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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import okio.FileSystem
import okio.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

interface FileCache<T> {
    val cacheValue: StateFlow<T?>

    suspend fun start()
}

abstract class FileCacheImpl<T>(
    private val baseDirectory: Path,
    private val file: Path,
    private val metaFile: Path,
    private val fileSystem: FileSystem,
) : FileCache<T> {

    private val _cacheValue: MutableStateFlow<T?> = MutableStateFlow(null)
    override val cacheValue: StateFlow<T?> = _cacheValue.asStateFlow()

    override suspend fun start() {
        fileSystem.createDirectories(baseDirectory)
        if (fileSystem.exists(file).not()) fileSystem.write(file) { }
        if (fileSystem.exists(metaFile).not()) fileSystem.write(metaFile) { }
        _cacheValue.value = readFile()
        while (true) {
            try {
                updateCache()
            } catch (exception: Exception) {
                if (exception is CancellationException) return
                log.error(exception) { "could not renew file cache $file - this uncatched exception should be fixed immediately" }
                delay(1.minutes)
            }
            delay(1.seconds) // just in case something goes completely wrong
        }
    }


    sealed interface RequestFileResult<T> {
        data class NewFile<T>(val content: String, val versionExtractor: (T) -> String?) :
            RequestFileResult<T>

        class NotModified<T> : RequestFileResult<T>
        data class Error<T>(val message: String) : RequestFileResult<T>
    }

    abstract fun nextUpdate(lastWasError: Boolean): Instant
    abstract suspend fun parseFile(content: String): T
    abstract suspend fun requestFile(version: String?): RequestFileResult<T>

    private suspend fun saveFile(content: String, versionExtractor: (T) -> String?) {
        val value = parseFile(content) // do at first, so it can fail (and is not changed locally)
        fileSystem.write(file) { writeUtf8(content) }
        _cacheValue.value = value
        saveMeta(versionExtractor(value))
    }


    private fun saveMeta(etag: String?, lastWasError: Boolean = false) {
        fileSystem.write(metaFile) {
            writeUtf8(Json.encodeToString(
                FileCacheMeta(
                    nextUpdate(
                        lastWasError
                    ), etag ?: ""
                )
            ))
        }
    }

    private suspend fun readFile(): T? =
        fileSystem.read(file) { readUtf8() }.ifEmpty { null }
            ?.let { parseFile(it) }

    private fun readMeta(): FileCacheMeta? =
        fileSystem.read(metaFile) { readUtf8() }.ifEmpty { null }
            ?.let { Json.decodeFromString(it) }


    private suspend fun updateCache() {
        val meta = readMeta()
        if (meta != null) {
            val duration = meta.updateAfter - Clock.System.now()
            log.info { "wait for next update of $file at ${meta.updateAfter} (in $duration)" }
            delay(duration)
        } else {
            log.info { "first update of $file" }
        }
        when (val requestFileResult = requestFile(meta?.version)) {
            is RequestFileResult.NewFile<T> -> {
                val content = requestFileResult.content
                saveFile(content, requestFileResult.versionExtractor)
                log.info { "updated $file" }
            }

            is RequestFileResult.NotModified -> {
                saveMeta(meta?.version)
                log.info { "$file did not change" }
            }

            is RequestFileResult.Error -> {
                saveMeta(meta?.version, true)
                log.error { "$file could not be updated: ${requestFileResult.message}" }
            }
        }
    }
}

@Serializable
data class FileCacheMeta(
    @SerialName("updateAfter") val updateAfter: Instant,
    @SerialName("version") val version: String,
)
