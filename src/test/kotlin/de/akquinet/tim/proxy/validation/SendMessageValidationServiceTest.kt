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
package de.akquinet.tim.proxy.validation

import de.akquinet.tim.proxy.error.InvalidKeyLength
import de.akquinet.tim.proxy.error.InvalidRelationType
import de.akquinet.tim.proxy.error.JSONDeserializationFailure
import de.akquinet.tim.proxy.error.KeyMustOnlyContainEmoji
import de.akquinet.tim.proxy.error.SendMessageIsValid
import de.akquinet.tim.proxy.error.ThreadingIsNotAllowed
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.types.shouldBeTypeOf

class SendMessageValidationServiceTest : ShouldSpec({

    val validator = SendMessageValidationService()

    context("failures") {
        should("fail with invalid JSON request") {
            val requestBody = """some invalid json""".trimIndent()
            validator.validateSendMessage(
                requestBody,
                "any.send.message.event"
            ).shouldBeLeft().apply {
                shouldBeTypeOf<JSONDeserializationFailure>()
            }
        }

        should("fail when threading is requested, see A_25395-02") {
            val requestBody = """{"m.relates_to":{"event_id":"event123","rel_type":"m.thread"}}""".trimIndent()
            validator.validateSendMessage(requestBody, "any.send.message.event") shouldBeLeft ThreadingIsNotAllowed
        }

        should("fail with invalid emoji count, see A_26228-01") {
            val requestBody = """{"m.relates_to":{"event_id":"event123","rel_type":"m.annotation","key":"👨‍👩‍👧👨‍👩‍"}}"""
            validator.validateSendMessage(requestBody, "m.reaction") shouldBeLeft InvalidKeyLength
        }

        should("fail with dirty emoji key") {
            val requestBody = """{"m.relates_to":{"event_id":"event123","rel_type":"m.annotation","key":"👨‍👩‍👧a"}}"""
            validator.validateSendMessage(requestBody, "m.reaction") shouldBeLeft KeyMustOnlyContainEmoji
        }

        should("fail with invalid relationType") {
            val requestBody = """{"m.relates_to":{"event_id":"event123","rel_type":"m.not.annotation","key":"👨‍👩‍👧"}}"""
            validator.validateSendMessage(requestBody, "m.reaction") shouldBeLeft InvalidRelationType
        }
    }

    context("successes") {
        context("m.reaction") {
            should("succeed with proper reaction event") {
                val requestBody = """{"m.relates_to":{"event_id":"event123","rel_type":"m.annotation","key":"👨‍👩‍👧"}}"""
                validator.validateSendMessage(requestBody, "m.reaction") shouldBeRight SendMessageIsValid
            }

            should("succeed without relatesTo property") {
                val requestBody = """{}"""
                validator.validateSendMessage(requestBody, "m.reaction") shouldBeRight SendMessageIsValid
            }

            should("succeed without relatesTo.key property") {
                val requestBody = """{"m.relates_to":{"event_id":"event123","rel_type":"m.annotation"}}"""
                validator.validateSendMessage(requestBody, "m.reaction") shouldBeRight SendMessageIsValid
            }

            should("succeed with blank relatesTo.key property") {
                val requestBody = """{"m.relates_to":{"event_id":"event123","rel_type":"m.annotation","key":""}}"""
                validator.validateSendMessage(requestBody, "m.reaction") shouldBeRight SendMessageIsValid
            }

            should("succeed with proper event") {
                val requestBody = """
                    {
                      "algorithm": "m.megolm.v1.aes-sha2",
                      "ciphertext": "AwgFEsAGQynnHAUqT1QM+5OEveeaIEqXUgzB6rMwhLtuA1VOYhP6X/Hb7e2atoqmHedtwXECfHle8lhZGjLfgZOGjnEDteKdGytG07iy5m+6nATS8+pkrPoFkCnYZhEM6K3OPrpltLjjYsYPhJvwbR3IqOFccPk8rKqT/768fUYft4e63pek6adMPJaTqMpJV8XuaJStFmeOX8z1FJ0mQlGVzKJUaAw1TFMp38hatoIclmV4vh23Cpwr4Mjov0NA4l5zc8SfLc8l94ydEiERj9opebB5Xaeej3J17KgxEQdd8B0bpNJJR+u3eH5UCGxc3K5jsBRA6tyRxtaGoJDjiMlG+/BuznMiMxMPQd1QLAsHKvNCXJbqvNgMUCbB1NIwmE25dbpc6b8iDyfd3JFegob5XIPcJcwGQM2FqV7GbMpsKKX/4v8fBoUxkbnDLCRSpP8dPCx0P1hMPNDjGVu+qKkd95SkDagJjyXNjarU4qpb0qyjlhCSJxKZkOxgQiwHVeEiUvty9m1UuTut2hAq+F+GJZOKnlMwVsnN/gsQYFRYPBg3lkXUOHcWmaocbxlDHhAdokVpd+9t9gWZeGQDFXbaEKZ0xsUlw92N3GFA3FF3bAIpFjZpiy2iaIcP+gGWKYcUoUcZGHMd8krFT7wtsDuPjZ6t7YPCZSgHGrSrco32JoirCbOBrKy0UN07F25jSZuVqBHcvPTzZQbdcPVaT+Hlzvti0LGR1oJOqroOaOh+xZ/redBolzFybUAdlOGH3Hxv+z3E3LlGwNhnHvPjhOwd7bF2aHTkTJdRH73RuQUKj+oYJtfayp+tXIMe+W/p3hWJa6yo3/yLd3OAJwb1AdNkZad/EzVxxOnnkM6Q/Z8Ya7dtVnSD1mzL4YCmOV7td+EdMlQN5hCKOIs8ptyDgIIVW39Fl+65+IeawoXffsVK7gkTtrj/VDgFmgWQ5H4OfITGJE7U0PTGXKZ0+x7x1qBa+8GydRxb/tg/7AdFCxi6c7L2gMivbSPfBKaLfl38zX1DlQfl3LnayoLbkGou7vrDpJxYJykzoI7SLeRDzRensnnP3Y/eaMEr5Qe1uREaQ+2KJJtsKEoCItBBD5ZvUAQa7aso9Y2a+zLhxLAuAl6RwMuuNtx8b8SNDzhrHhF33HJ5PaA524WMTYWaZcVtpP1eDXJQLnmh6y/8nH8DSdVRadQDOWTl3ZcqPSAdDA",
                      "device_id": "CZZUVFAUEC",
                      "m.relates_to": {
                        "m.in_reply_to": {
                          "event_id": "${'$'}cDsT8PT_QbM2uJP_KuVZbQGtcY0wbqg1mHGcOL8Fv6c"
                        }
                      },
                      "sender_key": "9LC7rMDpTN0toYdzNgN81SeJjJSBgAoca4SMSdXGtlI",
                      "session_id": "izBsv0XNclqcbbxdsJMXzuFmZsukmYFCAENp4RbOL0g"
                    }"""
                validator.validateSendMessage(requestBody, "m.room.encrypted") shouldBeRight SendMessageIsValid
            }
        }
    }
})