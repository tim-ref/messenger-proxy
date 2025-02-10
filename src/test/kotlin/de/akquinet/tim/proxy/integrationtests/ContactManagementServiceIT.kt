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
package de.akquinet.tim.proxy.integrationtests

import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.Contact
import de.akquinet.tim.fachdienst.messengerproxy.gematik.model.contactmanagement.ContactInviteSettings
import de.akquinet.tim.proxy.contactmgmt.database.ContactManagementServiceImpl
import de.akquinet.tim.proxy.contactmgmt.model.ContactEntities
import de.akquinet.tim.proxy.extensions.toEntity
import io.kotest.common.runBlocking
import io.kotest.matchers.shouldBe
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Instant

class ContactManagementServiceIT {

    companion object {

        private val embeddedPostgres: EmbeddedPostgres = EmbeddedPostgres.start()
        private lateinit var sut: ContactManagementServiceImpl

        @JvmStatic
        @BeforeAll
        fun setUp() {
            Database.connect({ embeddedPostgres.postgresDatabase.connection })
            transaction { SchemaUtils.create(ContactEntities) }

            sut = ContactManagementServiceImpl()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            embeddedPostgres.close()
        }
    }

    @Test
    fun `can handle inviteSettings with end set`() {
        val start: Instant = Instant.parse("2024-01-01T00:00:00.00Z")
        val end: Instant = Instant.parse("2024-02-01T00:00:00.00Z")

        val invitedUser = "@invited:example2.com"
        val invitingUser = "@inviting:example2.org"

        val invitingUserContact = Contact(
            displayName = "inviting user 2",
            mxid = invitingUser,
            inviteSettings = ContactInviteSettings(
                start = start.epochSecond,
                end = end.epochSecond
            )
        )

        runBlocking {
            sut.addContactTo(invitedUser, invitingUserContact.toEntity(invitedUser, null))
            sut.findContactsOf(invitedUser).isNotEmpty()

            sut.hasContactValidInviteSettings(
                ownerMxid = invitedUser,
                approvedMxid = invitingUser,
                timeToCompare = Instant.parse("2024-01-10T00:00:00.00Z") // between
            ) shouldBe true

            sut.hasContactValidInviteSettings(
                ownerMxid = invitedUser,
                approvedMxid = invitingUser,
                timeToCompare = Instant.parse("2023-01-01T00:00:00.00Z") // before
            ) shouldBe false

            sut.hasContactValidInviteSettings(
                ownerMxid = invitedUser,
                approvedMxid = invitingUser,
                timeToCompare = Instant.parse("2025-01-01T00:00:00.00Z") // after
            ) shouldBe false

            sut.hasContactValidInviteSettings(
                ownerMxid = invitedUser,
                approvedMxid = invitingUser,
                timeToCompare = start
            ) shouldBe true // inclusive start

            sut.hasContactValidInviteSettings(
                ownerMxid = invitedUser,
                approvedMxid = invitingUser,
                timeToCompare = end
            ) shouldBe false // exclusive end
        }
    }

    @Test
    fun `can handle inviteSettings without end timestamp`() {
        val start: Instant = Instant.parse("2024-01-01T00:00:00.00Z")

        val invitedUser = "@invited:example.com"
        val invitingUser = "@inviting:example.org"

        val invitingUserContact = Contact(
            displayName = "inviting user",
            mxid = invitingUser,
            inviteSettings = ContactInviteSettings(
                start = start.epochSecond
            )
        )

        runBlocking {
            sut.addContactTo(invitedUser, invitingUserContact.toEntity(invitedUser, null))
            sut.findContactsOf(invitedUser).isNotEmpty()

            sut.hasContactValidInviteSettings(
                ownerMxid = invitedUser,
                approvedMxid = invitingUser,
                timeToCompare = Instant.parse("2024-01-10T00:00:00.00Z") // between
            ) shouldBe true

            sut.hasContactValidInviteSettings(
                ownerMxid = invitedUser,
                approvedMxid = invitingUser,
                timeToCompare = Instant.parse("2023-01-01T00:00:00.00Z") // before
            ) shouldBe false

            sut.hasContactValidInviteSettings(
                ownerMxid = invitedUser,
                approvedMxid = invitingUser,
                timeToCompare = Instant.parse("2025-01-01T00:00:00.00Z") // after
            ) shouldBe true // invite has no end, i.e. end is open, so we expect it to valid at any time gte start

            sut.hasContactValidInviteSettings(
                ownerMxid = invitedUser,
                approvedMxid = invitingUser,
                timeToCompare = start
            ) shouldBe true // inclusive start

            // end is not set, so no test is necessary/possible
        }
    }
}