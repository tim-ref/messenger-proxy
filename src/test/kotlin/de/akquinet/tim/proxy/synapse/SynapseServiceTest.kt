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
package de.akquinet.tim.proxy.synapse

import arrow.core.Either
import com.sksamuel.hoplite.Secret
import de.akquinet.tim.proxy.config.SynapseClientConfig
import de.akquinet.tim.proxy.error.CouldNotGetAdminAccessToken
import de.akquinet.tim.proxy.error.CouldNotGetRoomDetails
import de.akquinet.tim.proxy.synapse.client.SynapseClient
import de.akquinet.tim.proxy.synapse.client.resources.JoinRules
import de.akquinet.tim.proxy.synapse.client.resources.LoginResponseBody
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk

class SynapseServiceTest :
  ShouldSpec({
    lateinit var mockSynapseClient: SynapseClient
    lateinit var synapseService: SynapseService

    beforeTest {
      mockSynapseClient = mockk<SynapseClient>(relaxed = true)
      val config =
        SynapseClientConfig(
          matrixDomain = "snpse.org",
          baseUrl = "https://snpse.org/",
          username = Secret("username"),
          password = Secret("password"),
        )
      synapseService = SynapseService(mockSynapseClient, config)
    }

    context("getRoomState") {
      should("return correct join rules") {
        coEvery {
          mockSynapseClient.login(userIdOrLocalpart = "username", password = "password")
        } returns LoginResponseBody("access token", "@test-user:snpse.org")
        coEvery { mockSynapseClient.getRoomDetails("!room-001:snpse.org") } returns
          Either.Right(JoinRules.PUBLIC)

        val result = synapseService.getRoomJoinRules(roomId = "!room-001:snpse.org")

        coVerifyOrder {
          mockSynapseClient.login("username", "password")
          mockSynapseClient.bearerToken = "access token"
        }

        result shouldBeRight JoinRules.PUBLIC
      }

      should("return error if can not get access token") {
        coEvery {
          mockSynapseClient.login(userIdOrLocalpart = "username", password = "password")
        } throws RuntimeException("unauthorized")

        val result = synapseService.getRoomJoinRules(roomId = "!room1:matrix.org")
        result.shouldBeLeft().apply {
          shouldBeTypeOf<CouldNotGetAdminAccessToken>()
          details shouldBe
            "Could not get admin access token cause: Failed to acquire admin user access token"
        }
      }

      should("return error if server responds with error or something goes wrong") {
        coEvery {
          mockSynapseClient.login(userIdOrLocalpart = "username", password = "password")
        } returns LoginResponseBody("access token", "@test-user:snpse.org")
        coEvery { mockSynapseClient.getRoomDetails("!irg3ndeinLangerRoom01:localhost") } returns
          Either.Left(CouldNotGetRoomDetails("!irg3ndeinLangerRoom01:localhost", "test error"))

        val result = synapseService.getRoomJoinRules(roomId = "!irg3ndeinLangerRoom01:localhost")

        coVerifyOrder {
          mockSynapseClient.login("username", "password")
          mockSynapseClient.bearerToken = "access token"
          mockSynapseClient.getRoomDetails("!irg3ndeinLangerRoom01:localhost")
        }

        result shouldBeLeft
          CouldNotGetRoomDetails(roomId = "!irg3ndeinLangerRoom01:localhost", error = "test error")
      }
    }
  })
