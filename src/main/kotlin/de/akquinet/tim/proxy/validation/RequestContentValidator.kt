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
package de.akquinet.tim.proxy.validation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.vdurmont.emoji.EmojiParser
import de.akquinet.tim.proxy.commons.HttpFailure
import de.akquinet.tim.proxy.outcomes.InvalidKeyLength
import de.akquinet.tim.proxy.outcomes.InvalidRelationType
import de.akquinet.tim.proxy.outcomes.InvalidRoomStateRequest
import de.akquinet.tim.proxy.outcomes.JSONDeserializationFailure
import de.akquinet.tim.proxy.outcomes.KeyMustOnlyContainEmoji
import de.akquinet.tim.proxy.outcomes.MoreThanOneInvitedUserOnRoomCreation
import de.akquinet.tim.proxy.outcomes.NotSupportedRoomVersion
import de.akquinet.tim.proxy.outcomes.RoomTypeNotPermitted
import de.akquinet.tim.proxy.outcomes.ThreadingIsNotAllowed
import de.akquinet.tim.proxy.outcomes.ValidationSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.UserId

private const val GEMATIK_ROOM_TYPE_V2 = "de.gematik.tim.roomtype.default.v2"

class RequestContentValidator {

  private val json = Json { ignoreUnknownKeys = true }
  private val supportedRoomVersions = setOf("9", "10")

  fun validateJoinRule(type: String, requestBody: String) = either {
    val request = decodeJSON(requestBody).bind()

    if (type == "m.room.join_rules") {
      val joinRule = request["join_rule"]?.jsonPrimitive?.contentOrNull
      ensure(joinRule != "public") { InvalidRoomStateRequest }
    }
    ValidationSuccess
  }

  /*
     gemSpec_TI-M_Basis A_26202, A_26203
     https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/latest/#A_26202
     https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/latest/#A_26203
  */
  fun validateRoomVersion(requestedRoomVersion: String?): Either<HttpFailure, String> {
    val roomVersion = requestedRoomVersion ?: supportedRoomVersions.last()

    if (roomVersion !in supportedRoomVersions) {
      return Either.Left(NotSupportedRoomVersion(roomVersion, supportedRoomVersions))
    }
    return Either.Right(roomVersion)
  }

  fun validateRoomType(requestedRoomType: String?) = either {
    ensure(requestedRoomType != GEMATIK_ROOM_TYPE_V2) { RoomTypeNotPermitted(GEMATIK_ROOM_TYPE_V2) }
    requestedRoomType
  }

  /*
    gemSpec_TI-M_Basis A_25368-01
    https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/latest/#A_25368-01
  */
  fun ensureMaxOneInvitedUser(invite: Set<UserId>?): Either<HttpFailure, UserId?> {
    if (invite != null && invite.size > 1) {
      return Either.Left(MoreThanOneInvitedUserOnRoomCreation)
    }
    return Either.Right(invite?.firstOrNull())
  }

  fun validateSendMessage(requestBody: String, eventType: String?) = either {
    val request = decodeJSON(requestBody).bind()

    validateRelTypeNotThreading(request).bind()

    when (eventType) {
      "m.reaction" -> validateEmojiLength(request).bind()
      else -> ValidationSuccess
    }
  }

  private fun validateEmojiLength(request: JsonObject) = either {
    val relatesTo: JsonObject? = request["m.relates_to"]?.jsonObject

    val key = relatesTo?.get("key")?.jsonPrimitive?.contentOrNull
    if (key.isNullOrBlank()) {
      ValidationSuccess
    } else {
      ensure(EmojiParser.extractEmojis(key).size == 1) { InvalidKeyLength }
      ensure(key.none { it.isLetterOrDigit() || it.isWhitespace() }) { KeyMustOnlyContainEmoji }

      val relType = relatesTo["rel_type"]?.jsonPrimitive?.contentOrNull
      ensure(relType == "m.annotation") { InvalidRelationType }

      ValidationSuccess
    }
  }

  private fun validateRelTypeNotThreading(request: JsonObject) = either {
    val relType = request["m.relates_to"]?.jsonObject?.get("rel_type")?.jsonPrimitive?.contentOrNull
    ensure(relType != "m.thread") { ThreadingIsNotAllowed }

    ValidationSuccess
  }

  private fun decodeJSON(requestBody: String) =
    Either.catch { json.decodeFromString<JsonObject>(requestBody) }
      .mapLeft { JSONDeserializationFailure(it) }
}
