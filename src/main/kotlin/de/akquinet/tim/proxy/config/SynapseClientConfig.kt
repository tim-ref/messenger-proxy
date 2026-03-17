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
package de.akquinet.tim.proxy.config

import com.sksamuel.hoplite.Masked

/** Configuration for the Synapse's admin API client */
data class SynapseClientConfig(
  /**
   * This is the domain part in any matrix identifier belonging to this matrix server. Must match
   * 'server_name' from homeserver.yaml. Used for validation.
   */
  val matrixDomain: String,

  /** The URL of this messenger proxy's Synapse's admin API */
  val baseUrl: String,

  /** Synapse admin username */
  val username: Masked? = null,

  /** Synapse admin password */
  val password: Masked? = null,
)
