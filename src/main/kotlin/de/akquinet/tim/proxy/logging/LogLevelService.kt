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
package de.akquinet.tim.proxy.logging

import ch.qos.logback.classic.Level
import de.akquinet.tim.proxy.ProxyConfiguration
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.slf4j.LoggerFactory
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class LogLevelService(
    private val logLevelResetConfiguration: ProxyConfiguration.LogLevelResetConfiguration
) {

    private val logger = KotlinLogging.logger {}
    private val loggerContext = LoggerFactory.getILoggerFactory()

    fun setLogLevel(newLogLevel: Level, loggerIdentifier: String = org.slf4j.Logger.ROOT_LOGGER_NAME) {
        val resetLogLevel = Level.toLevel(logLevelResetConfiguration.resetLogLevel)
        if (newLogLevel != resetLogLevel) {
            val targetLogger = loggerContext.getLogger(loggerIdentifier) as? ch.qos.logback.classic.Logger
            targetLogger?.let {
                logger.info { "Setting log level to $newLogLevel for target logger $loggerIdentifier" }
                it.level = newLogLevel
            }
        }
    }

    suspend fun scheduleLogLevelReset(loggerIdentifier: String = org.slf4j.Logger.ROOT_LOGGER_NAME) {
        delay(logLevelResetConfiguration.logLevelResetDelayInSeconds.toDuration(DurationUnit.SECONDS))
        val targetLogger = loggerContext.getLogger(loggerIdentifier) as? ch.qos.logback.classic.Logger
        targetLogger?.let {
            it.level = Level.toLevel(logLevelResetConfiguration.resetLogLevel)
            logger.info { "Reset log level back to ${it.level.levelStr} for target logger ${it.name}" }
        }
    }
}
