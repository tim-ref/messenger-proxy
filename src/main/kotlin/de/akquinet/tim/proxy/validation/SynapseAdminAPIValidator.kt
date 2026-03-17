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

import arrow.core.raise.either
import arrow.core.raise.ensure
import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.outcomes.InviteBlocked
import de.akquinet.tim.proxy.outcomes.ReferencedEventTooOld
import de.akquinet.tim.proxy.outcomes.ValidationSuccess
import de.akquinet.tim.proxy.synapse.SynapseService
import de.akquinet.tim.proxy.synapse.model.parseInvitePolicyFromJson
import de.akquinet.tim.proxy.tiMessengerInformation.TIMessengerInformationService
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import net.folivo.trixnity.core.model.UserId

/** Validates requests based on further information retrieved from synapse admin api */
class SynapseAdminAPIValidator(
  private val synapseService: SynapseService,
  private val tiMessengerInformationService: TIMessengerInformationService,
  private val clock: Clock = Clock.systemUTC(),
  private val authCheckConfig: ProxyConfiguration.TimAuthorizationCheckConfiguration,
) {
  /**
   * Implements check for invite permission
   *
   * @see <a
   *   href="https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.2.0/#A_26021-01"
   *   A_26021-01 - Durchsetzung der akteurspezifischen Berechtigungskonfiguration</a>
   */
  suspend fun validateInvitePermission(invited: String, inviter: UserId) = either {
    val accountDataJson = synapseService.getAccountData(invited).bind()

    val policy =
      parseInvitePolicyFromJson(accountDataJson, authCheckConfig.inviteRejectionPolicy).bind()
    val isSenderInsuredPerson =
      tiMessengerInformationService.getIsInsuranceByServerName(inviter.domain).bind()
    ensure(
      policy.isInviteAllowed(
        invitationSender = inviter,
        isSenderAnInsuredPerson = isSenderInsuredPerson.isInsurance,
      )
    ) {
      InviteBlocked
    }
    ValidationSuccess
  }

  /**
   * Implements checks for redacting events
   *
   * @see <a
   *   href="https://gemspec.gematik.de/docs/gemSpec/gemSpec_TI-M_Basis/gemSpec_TI-M_Basis_V1.2.0/#A_28358"
   *   A_28358 - Serverseitige Zeitgrenze für Redactions</a>
   */
  suspend fun validateRedactEvent(roomId: String, redactedEventId: String) = either {
    val timestamp = synapseService.getEventTimestamp(roomId, redactedEventId).bind()
    checkRedactedEventsTimestamp(timestamp).bind()
    ValidationSuccess
  }

  private fun checkRedactedEventsTimestamp(timestamp: Long) = either {
    val eventTimestamp = Instant.ofEpochMilli(timestamp)
    val now = Instant.now(clock)
    val durationInMins = Duration.between(eventTimestamp, now).toMinutes()
    val twentyFourHoursInMins = 24.toDuration(DurationUnit.HOURS).inWholeMinutes
    ensure(durationInMins <= twentyFourHoursInMins) { ReferencedEventTooOld }
  }
}
