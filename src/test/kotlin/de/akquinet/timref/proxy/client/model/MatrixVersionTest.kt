/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.client.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.shouldBe

class MatrixVersionTest : ShouldSpec({

    should("should parse proper version string") {
        val mVersion = MatrixVersion("v1.3")
        mVersion.version shouldBe "v1.3"
        mVersion.major shouldBe 1
        mVersion.minor shouldBe 3
        mVersion.patch shouldBe 0
    }

    should("should throw if invalid string is handed in for parsing") {
        shouldThrow<IllegalArgumentException> {
            MatrixVersion("invalid.version.string")
        }
    }

    should("should compare to equal") {
        val one = MatrixVersion("v1.3")
        val two = MatrixVersion("v1.3")

        (one == two) shouldBe true
        (one <= two) shouldBe true
        (one >= two) shouldBe true
        (one > two) shouldBe false
        (one < two) shouldBe false
        (one != two) shouldBe false
    }

    should("should compare to smaller than or equal") {
        val one = MatrixVersion("v1.3")
        val two = MatrixVersion("v1.4")

        (one < two) shouldBe true
        (one <= two) shouldBe true
        (one != two) shouldBe true
        (one > two) shouldBe false
        (one >= two) shouldBe false
        (one == two) shouldBe false
    }

    should("should compare to greater than or equal") {
        val one = MatrixVersion("v1.3")
        val two = MatrixVersion("v1.2")

        (one > two) shouldBe true
        (one >= two) shouldBe true
        (one != two) shouldBe true
        (one == two) shouldBe false
        (one < two) shouldBe false
        (one <= two) shouldBe false
    }

    should("find only versions less than or equal to v1.3") {
        val maxVersion = MatrixVersion("v1.3")
        val allVersionStrings = setOf("r0.0.1","r0.1.0","r0.2.0","r0.3.0","r0.4.0","r0.5.0","r0.6.0","r0.6.1","v1.1","v1.2","v1.3","v1.4","v1.5","v1.6")
        val filteredVersions = allVersionStrings
            .map { MatrixVersion(it) }
            .filter { it <= maxVersion }

        val filteredVersionStrings = listOf("r0.0.1","r0.1.0","r0.2.0","r0.3.0","r0.4.0","r0.5.0","r0.6.0","r0.6.1","v1.1","v1.2","v1.3")

        filteredVersions.size shouldBe 11
        filteredVersions shouldContainAll filteredVersionStrings.map { MatrixVersion(it) }
        filteredVersions shouldNotContainAnyOf listOf("v1.4","v1.5","v1.6").map { MatrixVersion(it) }

        // all the way back to
        filteredVersions.map { it.version } shouldContainAll filteredVersionStrings
    }
})
