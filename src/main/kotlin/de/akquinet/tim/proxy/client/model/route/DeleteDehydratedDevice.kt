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
package de.akquinet.tim.proxy.client.model.route

import io.ktor.resources.*
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.DELETE
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a
 *   href="https://github.com/uhoreg/matrix-doc/blob/shrivelled_sessions/proposals/3814-dehydrated-devices-with-ssss.md#deleting-a-dehydrated-device">MSC3814
 *   proposal</a>
 */
@Serializable
@Resource("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device")
@HttpMethod(DELETE)
object DeleteDehydratedDevice : MatrixEndpoint<Unit, Unit> {}
