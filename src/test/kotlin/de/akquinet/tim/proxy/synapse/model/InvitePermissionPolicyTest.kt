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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.model.UserId

class InvitePermissionPolicyTest :
  ShouldSpec({
    context("Invite policy with default 'allow all'") {
      val userId = UserId("@someUser:matrix.org")
      should("allow invite") {
        val policy = AllowAllInvites()
        val result =
          policy.isInviteAllowed(invitationSender = userId, isSenderAnInsuredPerson = true)
        result shouldBe true
      }

      should("not allow invite if invitation sender is blocked by user id") {
        val policy = AllowAllInvites(blockedUsers = setOf("@someUser:matrix.org"))
        val result = policy.isInviteAllowed(invitationSender = userId)
        result shouldBe false
      }

      should("not allow invite if invitation sender is blocked by server") {
        val policy = AllowAllInvites(blockedServers = setOf("matrix.org"))
        val result = policy.isInviteAllowed(invitationSender = userId)
        result shouldBe false
      }

      should("not allow invite if invitation sender is blocked by user group") {
        val policy = AllowAllInvites(blockedUserGroups = setOf(UserGroup.INSURED_USERS))
        val result =
          policy.isInviteAllowed(invitationSender = userId, isSenderAnInsuredPerson = true)
        result shouldBe false
      }

      should("not reject everyone") {
        val policy = AllowAllInvites()
        val result = policy.rejectsEveryone()
        result shouldBe false
      }
    }

    context("Invite policy with default 'block all'") {
      val userId = UserId("@someUser:matrix.org")
      should("not allow invite") {
        val policy = BlockAllInvites()
        val result =
          policy.isInviteAllowed(invitationSender = userId, isSenderAnInsuredPerson = true)
        result shouldBe false
      }

      should("allow invite if invitation sender is allowed by user id") {
        val policy = BlockAllInvites(allowedUsers = setOf("@someUser:matrix.org"))
        val result = policy.isInviteAllowed(invitationSender = userId)
        result shouldBe true
      }

      should("allow invite if invitation sender is allowed by server") {
        val policy = BlockAllInvites(allowedServers = setOf("matrix.org"))
        val result = policy.isInviteAllowed(invitationSender = userId)
        result shouldBe true
      }

      should("allow invite if invitation sender is allowed by user group") {
        val policy = BlockAllInvites(allowedUserGroups = setOf(UserGroup.INSURED_USERS))
        val result =
          policy.isInviteAllowed(invitationSender = userId, isSenderAnInsuredPerson = true)
        result shouldBe true
      }

      should("reject everyone") {
        val policy = BlockAllInvites()
        val result = policy.rejectsEveryone()
        result shouldBe true
      }

      should("not reject everyone") {
        val policy = BlockAllInvites(allowedUsers = setOf("@someUser:matrix.org"))
        val result = policy.rejectsEveryone()
        result shouldBe false
      }
    }
  })
