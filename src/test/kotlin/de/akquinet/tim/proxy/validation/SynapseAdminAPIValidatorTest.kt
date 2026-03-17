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

import arrow.core.right
import de.akquinet.tim.proxy.InviteRejectionPolicy
import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.mocks.mockAccountDataResponse
import de.akquinet.tim.proxy.outcomes.InviteBlocked
import de.akquinet.tim.proxy.outcomes.ReferencedEventTooOld
import de.akquinet.tim.proxy.outcomes.ValidationSuccess
import de.akquinet.tim.proxy.synapse.SynapseService
import de.akquinet.tim.proxy.tiMessengerInformation.IsInsuranceResult
import de.akquinet.tim.proxy.tiMessengerInformation.TIMessengerInformationService
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import net.folivo.trixnity.core.model.UserId

class SynapseAdminAPIValidatorTest :
  ShouldSpec({
    val now = Instant.parse("2025-11-18T12:00:00Z")
    val fixedClock = Clock.fixed(now, ZoneOffset.UTC)
    val synapseService = mockk<SynapseService>()
    val tiMessengerInformationService = mockk<TIMessengerInformationService>()
    val authCheckConfig = mockk<ProxyConfiguration.TimAuthorizationCheckConfiguration>()
    val validator =
      SynapseAdminAPIValidator(
        synapseService,
        tiMessengerInformationService,
        fixedClock,
        authCheckConfig,
      )
    val validationMeasure = Duration.ofHours(24)

    context("invite permission validation") {
      coEvery { authCheckConfig.inviteRejectionPolicy } returns InviteRejectionPolicy.BLOCK_ALL
      coEvery { tiMessengerInformationService.getIsInsuranceByServerName(any()) } returns
        IsInsuranceResult(isInsurance = true).right()
      should("permit invite") {
        coEvery { synapseService.getAccountData(any()) } returns
          mockAccountDataResponse("allow all").right()

        validator.validateInvitePermission(
          "@invitedUser:domain.de",
          UserId("@invitingUser:domain.de"),
        ) shouldBeRight ValidationSuccess
      }

      should("block invite") {
        coEvery { synapseService.getAccountData(any()) } returns
          mockAccountDataResponse("block all").right()

        validator.validateInvitePermission(
          "@invitedUser:domain.de",
          UserId("@invitingUser:domain.de"),
        ) shouldBeLeft InviteBlocked
      }
    }
    context("redact event validation") {
      should("succeed with timestamp one minute younger than twenty four hours") {
        val lessThan24Hours = validationMeasure.minusMinutes(1)
        val timestamp = now.minus(lessThan24Hours).toEpochMilli()
        coEvery { synapseService.getEventTimestamp(any(), any()) } returns timestamp.right()
        validator.validateRedactEvent("roomId", "redactedEventId") shouldBeRight ValidationSuccess
      }

      should("succeed with timestamp one hour younger than twenty four hours") {
        val lessThan24Hours = validationMeasure.minusHours(1)
        val timestamp = now.minus(lessThan24Hours).toEpochMilli()
        coEvery { synapseService.getEventTimestamp(any(), any()) } returns timestamp.right()
        validator.validateRedactEvent("roomId", "redactedEventId") shouldBeRight ValidationSuccess
      }

      should("succeed with timestamp exactly twenty four hours old") {
        val timestamp = now.minus(validationMeasure).toEpochMilli()
        coEvery { synapseService.getEventTimestamp(any(), any()) } returns timestamp.right()
        validator.validateRedactEvent("roomId", "redactedEventId") shouldBeRight ValidationSuccess
      }

      should("fail with timestamp one minute older than twenty four hours") {
        val moreThan24Hours = validationMeasure.plusMinutes(1)
        val timestamp = now.minus(moreThan24Hours).toEpochMilli()
        coEvery { synapseService.getEventTimestamp(any(), any()) } returns timestamp.right()

        validator.validateRedactEvent("roomId", "redactedEventId").shouldBeLeft().apply {
          shouldBeTypeOf<ReferencedEventTooOld>()
        }
      }

      should("fail with timestamp one hour older than twenty four hours") {
        val moreThan24Hours = validationMeasure.plusHours(1)
        val timestamp = now.minus(moreThan24Hours).toEpochMilli()
        coEvery { synapseService.getEventTimestamp(any(), any()) } returns timestamp.right()

        validator.validateRedactEvent("roomId", "redactedEventId").shouldBeLeft().apply {
          shouldBeTypeOf<ReferencedEventTooOld>()
        }
      }
    }
  })
