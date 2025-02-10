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
package de.akquinet.tim.proxy.extensions

import net.folivo.trixnity.core.model.UserId

fun UserId.toUriFormat(): UserId =
    if (this.full.contains(UserId.sigilCharacter)) {
        UserId("matrix:u/${this.localpart}:${this.domain}")
    } else {
        this
    }

fun UserId.toAtFormat(): UserId =
    if (this.full.contains(UserId.sigilCharacter)) {
        this
    } else {
        UserId(this.full.replace("matrix:u/", UserId.sigilCharacter.toString()))
    }
