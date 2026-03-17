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
package de.akquinet.tim.proxy.orphanedrooms

import de.akquinet.tim.proxy.ProxyConfiguration
import de.akquinet.tim.proxy.synapse.SynapseService
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private const val PREFIX_A_28564 = "A_28564-01"

interface OrphanedRoomCleanupService {
  suspend fun start()
}

class OrphanedRoomCleanupServiceImpl(
  private val synapseService: SynapseService,
  private val orphanedRoomChecker: OrphanedRoomChecker,
  private val cleanupConfig: ProxyConfiguration.OrphanedRoomCleanupConfig,
) : OrphanedRoomCleanupService {

  override suspend fun start() {
    if (!cleanupConfig.enabled) {
      logger.info { "$PREFIX_A_28564 Orphaned room cleanup is disabled" }
      return
    }

    logger.info {
      "$PREFIX_A_28564 Starting orphaned room cleanup service with " +
        "interval=${cleanupConfig.checkIntervalDays} days, " +
        "threshold=${cleanupConfig.roomAgeThresholdDays} days"
    }

    while (true) {
      try {
        synapseService.performOrphanedRoomCleanup(orphanedRoomChecker)
      } catch (e: CancellationException) {
        logger.info { "$PREFIX_A_28564 Orphaned room cleanup service cancelled" }
        throw e
      } catch (e: Exception) {
        logger.error(e) { "$PREFIX_A_28564 Error during orphaned room cleanup: ${e.message}" }
      }
      delay(cleanupConfig.checkIntervalDays.days)
    }
  }
}
