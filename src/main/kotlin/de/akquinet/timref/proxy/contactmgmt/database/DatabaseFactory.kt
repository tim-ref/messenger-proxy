/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.akquinet.timref.proxy.contactmgmt.database

import de.akquinet.timref.proxy.ProxyConfiguration
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction


object DatabaseFactory {
    fun migrate(databaseConfig: ProxyConfiguration.DatabaseConfig) {
        val flyway = Flyway.configure()
            .dataSource(databaseConfig.jdbcUrl, databaseConfig.dbUser, databaseConfig.dbPassword)
            .locations("db/migration/")
            .baselineOnMigrate(true)
            .validateMigrationNaming(false)
            .load()
        flyway.migrate()
    }
    fun init(databaseConfig: ProxyConfiguration.DatabaseConfig) {
        transaction(connectToDB(databaseConfig)) {
        }
    }


    private fun connectToDB(databaseConfig: ProxyConfiguration.DatabaseConfig) = Database.connect(
        url = databaseConfig.jdbcUrl,
        driver = databaseConfig.driver,
        user = databaseConfig.dbUser,
        password = databaseConfig.dbPassword
    )

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
