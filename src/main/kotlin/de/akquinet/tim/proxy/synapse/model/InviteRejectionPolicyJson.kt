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
package de.akquinet.tim.proxy.synapse.model

import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import de.akquinet.tim.proxy.InviteRejectionPolicy
import de.akquinet.tim.proxy.client.model.route.account_data.asDefaultSetting
import de.akquinet.tim.proxy.outcomes.UnexpectedRejectionPolicyFormat
import de.akquinet.tim.proxy.synapse.client.resources.SynapseAdminAccountData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val permissionConfig = "de.gematik.tim.account.permissionconfig.pro.v1"
private const val defaultSetting = "defaultSetting"
private const val global = "global"
private const val blockAll = "block all"
private const val allowAll = "allow all"
private const val userExceptions = "userExceptions"
private const val serverExceptions = "serverExceptions"
private const val groupExceptions = "groupExceptions"
private const val inInsuredPerson = "isInsuredPerson"

private fun parseKeys(json: JsonElement?): Set<String> = json?.jsonObject?.keys ?: emptySet()

private fun parseGroups(json: JsonElement?): Set<UserGroup> =
  if (json?.jsonArray?.map(JsonElement::toString)?.contains(inInsuredPerson) ?: false)
    setOf(UserGroup.INSURED_USERS)
  else emptySet()

private fun parsePermissionConfig(response: SynapseAdminAccountData.Response): JsonObject? {
  val globalAccountData = response.accountData[global]?.jsonObject
  return globalAccountData?.get(permissionConfig)?.jsonObject
}

private fun getInvitePolicy(json: JsonObject) =
  when (json[defaultSetting]?.jsonPrimitive?.contentOrNull) {
    allowAll ->
      AllowAllInvites(
          blockedServers = parseKeys(json[serverExceptions]),
          blockedUsers = parseKeys(json[userExceptions]),
          blockedUserGroups = parseGroups(json[groupExceptions]),
        )
        .right()

    blockAll ->
      BlockAllInvites(
          allowedServers = parseKeys(json[serverExceptions]),
          allowedUsers = parseKeys(json[userExceptions]),
          allowedUserGroups = parseGroups(json[groupExceptions]),
        )
        .right()

    else -> UnexpectedRejectionPolicyFormat.left()
  }

/// Expected schema of json can be found here: see:
// https://github.com/gematik/api-ti-messenger/commit/9b9f21b87949e778de85dbbc19e25f53495871e2#diff-497ec6e8851cb404e681bc28551f0c288d09a3d496d05ee8a13643699a3f6798
fun parseInvitePolicyFromJson(
  response: SynapseAdminAccountData.Response,
  defaultConfig: InviteRejectionPolicy,
) = either {
  val permissionConfig =
    parsePermissionConfig(response)
      ?: JsonObject(
        content =
          mapOf(defaultSetting to Json.encodeToJsonElement(defaultConfig.asDefaultSetting()))
      )
  getInvitePolicy(permissionConfig).bind()
}
