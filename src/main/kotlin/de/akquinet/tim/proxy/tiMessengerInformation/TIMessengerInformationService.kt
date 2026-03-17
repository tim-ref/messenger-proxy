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
package de.akquinet.tim.proxy.tiMessengerInformation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import de.akquinet.tim.proxy.federation.FederationListCache

class TIMessengerInformationService(private val federationListCache: FederationListCache) {
  fun getServerNameByIkNumber(
    ikNumber: String
  ): Either<TiMessengerInformationError, ServerNameResult> = either {
    val domain =
      federationListCache.domains.value.firstOrNull { it.ik?.contains(ikNumber) ?: false }

    ensureNotNull(domain) {
      TiMessengerInformationError.NoMatch("no domain associated with ikNumber=$ikNumber")
    }

    ServerNameResult(domain.domain)
  }

  fun getIsInsuranceByServerName(
    serverName: String
  ): Either<TiMessengerInformationError, IsInsuranceResult> = either {
    val domain = federationListCache.domains.value.firstOrNull { it.domain == serverName }

    ensureNotNull(domain) {
      TiMessengerInformationError.NoMatch("no domain associated with serverName=$serverName")
    }

    IsInsuranceResult(domain.isInsurance)
  }
}
