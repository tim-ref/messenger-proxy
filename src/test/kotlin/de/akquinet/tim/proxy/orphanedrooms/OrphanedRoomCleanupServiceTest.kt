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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class OrphanedRoomCleanupServiceTest :
  ShouldSpec({
    context("OrphanedRoomCleanupService configuration") {
      should("have correct default configuration values") {
        val defaultConfig = ProxyConfiguration.OrphanedRoomCleanupConfig()

        defaultConfig.enabled shouldBe true
        defaultConfig.checkIntervalDays shouldBe 1
        defaultConfig.roomAgeThresholdDays shouldBe 1
      }
    }

    context("Service disabled") {
      should("not perform cleanup when disabled") {
        val disabledConfig =
          ProxyConfiguration.OrphanedRoomCleanupConfig(
            enabled = false,
            checkIntervalDays = 1,
            roomAgeThresholdDays = 14,
          )

        disabledConfig.enabled shouldBe false
      }

      should("return immediately without calling synapseService when disabled") {
        runTest {
          val synapseService = mockk<SynapseService>()
          val orphanedRoomChecker = mockk<OrphanedRoomChecker>()
          val disabledConfig =
            ProxyConfiguration.OrphanedRoomCleanupConfig(
              enabled = false,
              checkIntervalDays = 1,
              roomAgeThresholdDays = 14,
            )

          val service =
            OrphanedRoomCleanupServiceImpl(synapseService, orphanedRoomChecker, disabledConfig)

          service.start()

          coVerify(exactly = 0) { synapseService.performOrphanedRoomCleanup(any()) }
        }
      }
    }

    context("Service enabled") {
      should("call performOrphanedRoomCleanup when enabled") {
        runTest {
          val synapseService = mockk<SynapseService>()
          val orphanedRoomChecker = mockk<OrphanedRoomChecker>()
          val enabledConfig =
            ProxyConfiguration.OrphanedRoomCleanupConfig(
              enabled = true,
              checkIntervalDays = 1,
              roomAgeThresholdDays = 14,
            )

          coEvery { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) } returns Unit

          val service =
            OrphanedRoomCleanupServiceImpl(synapseService, orphanedRoomChecker, enabledConfig)

          val job = launch { service.start() }

          // Let first cleanup run
          advanceTimeBy(1)
          coVerify(exactly = 1) { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) }

          job.cancel()
        }
      }

      should("call performOrphanedRoomCleanup multiple times according to interval") {
        runTest {
          val synapseService = mockk<SynapseService>()
          val orphanedRoomChecker = mockk<OrphanedRoomChecker>()
          val enabledConfig =
            ProxyConfiguration.OrphanedRoomCleanupConfig(
              enabled = true,
              checkIntervalDays = 1,
              roomAgeThresholdDays = 14,
            )

          coEvery { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) } returns Unit

          val service =
            OrphanedRoomCleanupServiceImpl(synapseService, orphanedRoomChecker, enabledConfig)

          val job = launch { service.start() }

          // First cleanup runs immediately
          advanceTimeBy(1)
          coVerify(exactly = 1) { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) }

          // Advance by 1 day - second cleanup
          advanceTimeBy(1.days.inWholeMilliseconds)
          coVerify(exactly = 2) { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) }

          // Advance by another day - third cleanup
          advanceTimeBy(1.days.inWholeMilliseconds)
          coVerify(exactly = 3) { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) }

          job.cancel()
        }
      }

      should("respect custom checkIntervalDays configuration") {
        runTest {
          val synapseService = mockk<SynapseService>()
          val orphanedRoomChecker = mockk<OrphanedRoomChecker>()
          val customIntervalConfig =
            ProxyConfiguration.OrphanedRoomCleanupConfig(
              enabled = true,
              checkIntervalDays = 7,
              roomAgeThresholdDays = 14,
            )

          coEvery { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) } returns Unit

          val service =
            OrphanedRoomCleanupServiceImpl(
              synapseService,
              orphanedRoomChecker,
              customIntervalConfig,
            )

          val job = launch { service.start() }

          // First cleanup runs immediately
          advanceTimeBy(1)
          coVerify(exactly = 1) { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) }

          // Advance by 6 days - still only 1 cleanup
          advanceTimeBy(6.days.inWholeMilliseconds)
          coVerify(exactly = 1) { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) }

          // Advance by 1 more day (total 7 days) - second cleanup
          advanceTimeBy(1.days.inWholeMilliseconds)
          coVerify(exactly = 2) { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) }

          job.cancel()
        }
      }
    }

    context("Exception handling") {
      should("catch and log non-CancellationException without stopping the loop") {
        runTest {
          val synapseService = mockk<SynapseService>()
          val orphanedRoomChecker = mockk<OrphanedRoomChecker>()
          val enabledConfig =
            ProxyConfiguration.OrphanedRoomCleanupConfig(
              enabled = true,
              checkIntervalDays = 1,
              roomAgeThresholdDays = 14,
            )

          // First call throws, second call succeeds
          coEvery { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) } throws
            RuntimeException("Test error") andThen
            Unit

          val service =
            OrphanedRoomCleanupServiceImpl(synapseService, orphanedRoomChecker, enabledConfig)

          val job = launch { service.start() }

          // First cleanup runs and throws
          advanceTimeBy(1)
          coVerify(exactly = 1) { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) }

          // Advance by 1 day - second cleanup should still run despite previous error
          advanceTimeBy(1.days.inWholeMilliseconds)
          coVerify(exactly = 2) { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) }

          job.cancel()
        }
      }

      should("rethrow CancellationException to stop the service") {
        runTest {
          val synapseService = mockk<SynapseService>()
          val orphanedRoomChecker = mockk<OrphanedRoomChecker>()
          val enabledConfig =
            ProxyConfiguration.OrphanedRoomCleanupConfig(
              enabled = true,
              checkIntervalDays = 1,
              roomAgeThresholdDays = 14,
            )

          coEvery { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) } throws
            CancellationException("Service cancelled")

          val service =
            OrphanedRoomCleanupServiceImpl(synapseService, orphanedRoomChecker, enabledConfig)

          shouldThrow<CancellationException> { service.start() }

          coVerify(exactly = 1) { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) }
        }
      }

      should("continue running after multiple consecutive errors") {
        runTest {
          val synapseService = mockk<SynapseService>()
          val orphanedRoomChecker = mockk<OrphanedRoomChecker>()
          val enabledConfig =
            ProxyConfiguration.OrphanedRoomCleanupConfig(
              enabled = true,
              checkIntervalDays = 1,
              roomAgeThresholdDays = 14,
            )

          // All calls throw exceptions
          coEvery { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) } throws
            RuntimeException("Error 1") andThenThrows
            IllegalStateException("Error 2") andThenThrows
            RuntimeException("Error 3") andThen
            Unit

          val service =
            OrphanedRoomCleanupServiceImpl(synapseService, orphanedRoomChecker, enabledConfig)

          val job = launch { service.start() }

          // Run through 4 cycles
          advanceTimeBy(1)
          advanceTimeBy(1.days.inWholeMilliseconds)
          advanceTimeBy(1.days.inWholeMilliseconds)
          advanceTimeBy(1.days.inWholeMilliseconds)

          // All 4 calls should have been made despite errors
          coVerify(exactly = 4) { synapseService.performOrphanedRoomCleanup(orphanedRoomChecker) }

          job.cancel()
        }
      }
    }
  })
