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
package de.akquinet.tim.proxy

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.ktor.network.tls.certificates.*
import java.io.OutputStreamWriter
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter

class OutboundProxyCertificateManagerTest :
  ShouldSpec({
    val fakeFileSystem = FakeFileSystem()
    val config =
      ProxyConfiguration.OutboundProxyConfiguration(
        enforceDomainList = true,
        port = 8093,
        baseDirectory = "certificates",
        caCertificateFile = "certificates/ca.crt",
        caPrivateKeyFile = "certificates/key.pem",
        domainWhiteList = "",
        ssoDomain = "",
        certCacheDuration = 1000.milliseconds,
      )

    val keyStore =
      generateCertificate(
        file = java.io.File("target/test-keystore.jks"),
        keyAlias = "ca",
        keyPassword = "password",
        algorithm = "SHA256withECDSA",
        keySizeInBits = 256,
        keyType = KeyType.CA,
      )

    fun getCacheSize(manager: OutboundProxyCertificateManagerImpl): Int {
      val field =
        OutboundProxyCertificateManagerImpl::class.java.getDeclaredField("certificateCache")
      field.isAccessible = true
      @Suppress("UNCHECKED_CAST") val cache = field.get(manager) as MutableStateFlow<Map<String, *>>
      return cache.value.size
    }

    beforeTest {
      fakeFileSystem.createDirectories(config.baseDirectory.toPath())
      fakeFileSystem.write(config.caCertificateFile.toPath()) {
        PemWriter(OutputStreamWriter(outputStream())).apply {
          writeObject(PemObject("X509 CERTIFICATE", keyStore.getCertificate("cacert").encoded))
          flush()
        }
      }
      fakeFileSystem.write(config.caPrivateKeyFile.toPath()) {
        PemWriter(OutputStreamWriter(outputStream())).apply {
          writeObject(
            PemObject("PRIVATE KEY", keyStore.getKey("ca", "password".toCharArray()).encoded)
          )
          flush()
        }
      }
    }

    afterTest { fakeFileSystem.deleteRecursively(config.baseDirectory.toPath()) }

    should("evict expired entries from cert cache") {
      val manager = OutboundProxyCertificateManagerImpl(config, fakeFileSystem)

      manager.impersonatingSslEngine("host1.example.com")
      manager.impersonatingSslEngine("host2.example.com")
      getCacheSize(manager) shouldBe 2

      delay(1500.milliseconds)

      // After TTL expired, requesting a new host should evict expired entries
      manager.impersonatingSslEngine("host3.example.com")
      getCacheSize(manager) shouldBeLessThanOrEqual 1 // only host3 should remain
    }
  })
