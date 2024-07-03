/*
 * Copyright © 2023 - 2024 akquinet GmbH (https://www.akquinet.de)
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

package de.akquinet.tim.proxy.rawdata.model

enum class Operation(private val value: String) {
    MP_CLIENT_LOGIN_SUPPORTED_LOGIN_TYPES("TIM.UC_100 57_01"),
    MP_CLIENT_LOGIN_REQUEST_ACCESS_TOKEN("TIM.UC_100 57_02"),
    MP_CLIENT_LOGIN_REQUEST_OPENID_TOKEN("TIM.UC_100 57_03"),
    MP_INVITE_WITHIN_ORGANISATION_SEARCH("TIM.UC_101 04_01"),
    MP_INVITE_WITHIN_ORGANISATION_INVITE("TIM.UC_101 04_02"),
    MP_EXCHANGE_EVENT_WITHIN_ORGANISATION("TIM.UC_100 63_01"),
    MP_INVITE_OUTSIDE_ORGANISATION_INVITE_SENDER("TIM.UC_100 61_02"),
    MP_INVITE_OUTSIDE_ORGANISATION_INVITE_RECEIVER("TIM.UC_100 61_03"),
    MP_EXCHANGE_EVENT_OUTSIDE_ORGANISATION_SENDER("TIM.UC_100 62_01"),
    MP_EXCHANGE_EVENT_OUTSIDE_ORGANISATION_RECEIVER("TIM.UC_100 62_02"),
    MP_INVITE_OUTSIDE_ORGANISATION_ADD_TO_CONTACT_MANAGEMENT_LIST("TIM.UC_100 61_01");

    override fun toString() = value
}
