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
package de.akquinet.tim.proxy.extensions

import de.akquinet.tim.proxy.extensions.toAtFormat
import de.akquinet.tim.proxy.extensions.toUriFormat
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.model.UserId

class UserIdExtensionTest : ShouldSpec({

    should("convert matrix user id from @ format to uri format") {
        val atFormat = "@somebody:somewhere"
        val uriFormat = "matrix:u/somebody:somewhere"

        UserId(atFormat).toUriFormat().full shouldBe uriFormat
        UserId(uriFormat).toUriFormat().full shouldBe uriFormat
    }

    should("convert matrix user id from uri format to @ format") {
        val uriFormat = "matrix:u/somebody:somewhere"
        val atFormat = "@somebody:somewhere"

        UserId(uriFormat).toAtFormat().full shouldBe atFormat
        UserId(atFormat).toAtFormat().full shouldBe atFormat
    }
})
