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
package de.akquinet.tim.proxy.validation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.vdurmont.emoji.EmojiParser
import de.akquinet.tim.proxy.error.InvalidKeyLength
import de.akquinet.tim.proxy.error.InvalidRelationType
import de.akquinet.tim.proxy.error.JSONDeserializationFailure
import de.akquinet.tim.proxy.error.KeyMustOnlyContainEmoji
import de.akquinet.tim.proxy.error.SendMessageIsValid
import de.akquinet.tim.proxy.error.ThreadingIsNotAllowed
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SendMessageValidationService {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun validateSendMessage(requestBody: String, eventType: String?) = either {
        val request = Either
            .catch { json.decodeFromString<JsonObject>(requestBody) }
            .mapLeft { JSONDeserializationFailure(it) }
            .bind()

        validateRelTypeNotThreading(request).bind()

        when (eventType) {
            "m.reaction" -> validateEmojiLength(request).bind()
            else -> SendMessageIsValid
        }
    }

    private fun validateEmojiLength(request: JsonObject) = either {
        val relatesTo: JsonObject? = request["m.relates_to"]?.jsonObject

        val key = relatesTo?.get("key")?.jsonPrimitive?.contentOrNull
        if (key == null || key.isBlank()) {
            SendMessageIsValid
        } else {
            ensure(EmojiParser.extractEmojis(key).size == 1) { InvalidKeyLength }
            ensure(EmojiParser.removeAllEmojis(key).isEmpty()) { KeyMustOnlyContainEmoji }

            val relType = relatesTo["rel_type"]?.jsonPrimitive?.contentOrNull
            ensure(relType == "m.annotation") { InvalidRelationType }

            SendMessageIsValid
        }
    }

    private fun validateRelTypeNotThreading(request: JsonObject) = either {
        val relType = request["m.relates_to"]?.jsonObject?.get("rel_type")?.jsonPrimitive?.contentOrNull
        ensure(relType != "m.thread") { ThreadingIsNotAllowed }

        SendMessageIsValid
    }
}