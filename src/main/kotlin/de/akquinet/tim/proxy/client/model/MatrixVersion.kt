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

package de.akquinet.tim.proxy.client.model

/**
 * This class represents a typical matrix version string such as v1.3 and makes it comparable to other matrix versions.
 */
data class MatrixVersion(
    val version: String
) : Comparable<MatrixVersion> {

    var major: Int = 0
        private set
    var minor: Int = 0
        private set
    var patch: Int = 0
        private set

    init {
        loadFromVersion()
    }

    private fun loadFromVersion() {
        if (this.version.matches(VERSION_REGEX)) {
            val parts = this.version.split(".")
            this.major = parts[0].replace("[^0-9]".toRegex(), "").toInt()
            this.minor = parts[1].toInt()
            if (parts.count() > 2) {
                this.patch = parts[2].takeWhile { it.isDigit() }.toInt()
            }
        }else {
            throw IllegalArgumentException("${this.version} is not a valid matrix version string, must match $VERSION_REGEX")
        }
    }

    override fun compareTo(other: MatrixVersion): Int {
        return if (this.major == other.major) {
            if (this.minor == other.minor) {
                this.patch - other.patch
            } else {
                this.minor - other.minor
            }
        } else {
            this.major - other.major
        }
    }

    companion object {
        private val VERSION_REGEX = "^(r|v)?(\\d+)(\\.)(\\d+)(\\.?\\d*).*$".toRegex()
    }
}
