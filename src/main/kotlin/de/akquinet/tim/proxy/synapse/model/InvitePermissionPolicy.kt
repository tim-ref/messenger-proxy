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

import net.folivo.trixnity.core.model.UserId

sealed interface InvitePermissionPolicy {
  fun isInviteAllowed(invitationSender: UserId, isSenderAnInsuredPerson: Boolean? = false): Boolean

  fun rejectsEveryone(): Boolean
}

enum class UserGroup {
  INSURED_USERS
}

data class AllowAllInvites(
  /**
   * List of Matrix server names. Example ["matrix.org"] Invites from users on these domains are to
   * be automatically rejected.
   */
  val blockedServers: Set<String> = emptySet(),

  /**
   * List of Matrix user IDs. Example ["@user:matrix.org"] Invites from these user are to be
   * automatically rejected.
   */
  val blockedUsers: Set<String> = emptySet(),

  /**
   * List of TI-M user groups. Example [UserGroup.INSURED_USERS] Invites from user in these groups
   * are to be automatically rejected.
   */
  val blockedUserGroups: Set<UserGroup> = emptySet(),
) : InvitePermissionPolicy {
  override fun isInviteAllowed(
    invitationSender: UserId,
    isSenderAnInsuredPerson: Boolean?,
  ): Boolean =
    !(this.blockedServers.contains(invitationSender.domain) ||
      this.blockedUsers.contains(invitationSender.full) ||
      (this.blockedUserGroups.contains(UserGroup.INSURED_USERS) && isSenderAnInsuredPerson == true))

  override fun rejectsEveryone(): Boolean = false
}

data class BlockAllInvites(
  /**
   * List of Matrix server names. Example ["matrix.org"] Invites from users on these domains are
   * exempt from being automatically rejected.
   */
  val allowedServers: Set<String> = emptySet(),

  /**
   * List of Matrix user IDs. Example ["@user:matrix.org"] Invites from these user are exempt from
   * being automatically rejected.
   */
  val allowedUsers: Set<String> = emptySet(),

  /**
   * List of TI-M user groups. Example [UserGroup.INSURED_USERS] Invites from user in these groups
   * are exempt from being automatically rejected.
   */
  val allowedUserGroups: Set<UserGroup> = emptySet(),
) : InvitePermissionPolicy {
  override fun isInviteAllowed(
    invitationSender: UserId,
    isSenderAnInsuredPerson: Boolean?,
  ): Boolean =
    this.allowedServers.contains(invitationSender.domain) ||
      this.allowedUsers.contains(invitationSender.full) ||
      (this.allowedUserGroups.contains(UserGroup.INSURED_USERS) && isSenderAnInsuredPerson == true)

  override fun rejectsEveryone(): Boolean =
    allowedServers.isEmpty() && allowedUsers.isEmpty() && allowedUserGroups.isEmpty()
}
