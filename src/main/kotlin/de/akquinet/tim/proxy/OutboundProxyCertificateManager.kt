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

import io.netty.buffer.ByteBufAllocator
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SupportedCipherSuiteFilter
import io.netty.util.ReferenceCounted
import java.io.InputStreamReader
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import okio.FileSystem
import okio.Path.Companion.toPath
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.bc.BcX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

interface OutboundProxyCertificateManager {
  val rootCaCertificate: X509Certificate
  val privateKey: PrivateKey

  fun impersonatingSslEngine(hostname: String): SSLEngine
}

class OutboundProxyCertificateManagerImpl(
  private val config: ProxyConfiguration.OutboundProxyConfiguration,
  fileSystem: FileSystem,
) : OutboundProxyCertificateManager {
  private data class CachedCertificate(
    val cert: X509Certificate,
    val key: PrivateKey,
    val sslContext: SslContext,
    val cachedAt: Instant,
  )

  private val certificateCache = MutableStateFlow<Map<String, CachedCertificate>>(mapOf())
  override val rootCaCertificate: X509Certificate =
    fileSystem
      .read(config.caCertificateFile.toPath()) {
        CertificateFactory.getInstance("X.509").generateCertificate(inputStream())
      }
      .let {
        require(it is X509Certificate) { "expected an X.509 certificate" }
        it
      }

  override val privateKey: PrivateKey =
    fileSystem.read(config.caPrivateKeyFile.toPath()) {
      val privateKeyInfo =
        when (val keyPair = PEMParser(InputStreamReader(inputStream())).readObject()) {
          is PEMKeyPair -> keyPair.privateKeyInfo
          is PrivateKeyInfo -> keyPair
          else -> throw IllegalArgumentException("private key type not supported")
        }
      JcaPEMKeyConverter().getPrivateKey(privateKeyInfo)
    }

  /**
   * The cipher suites of the platform's default SSL context. Computing them allocates a full
   * [SSLContext] plus key and trust managers, so it is done once and reused.
   */
  private val defaultCipherSuites: List<String> by lazy {
    SSLContext.getInstance("TLSv1.2")
      .apply {
        val keyStore =
          KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, "".toCharArray()) }
        init(
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore, "".toCharArray()) }
            .keyManagers,
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore) }
            .trustManagers,
          SecureRandom(),
        )
      }
      .serverSocketFactory
      .defaultCipherSuites
      .toList()
  }

  override fun impersonatingSslEngine(hostname: String): SSLEngine {
    // TODO use one certificate for all known federation domains with use of subjectAlternativeName
    // extension
    val now = Clock.System.now()
    return getCachedCertificate(hostname, now).sslContext.newEngine(ByteBufAllocator.DEFAULT)
  }

  private fun buildSslContext(cert: X509Certificate, key: PrivateKey): SslContext =
    SslContextBuilder.forServer(key, cert, rootCaCertificate)
      .ciphers(defaultCipherSuites, SupportedCipherSuiteFilter.INSTANCE)
      .build()

  private fun getCachedCertificate(hostname: String, now: Instant): CachedCertificate {
    val cached = certificateCache.value[hostname]
    if (cached != null && now < (cached.cachedAt + config.certCacheDuration)) {
      return cached
    }

    val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
    val serverKeyPair: KeyPair = generator.genKeyPair()
    val impersonatingCertificate =
      JcaX509CertificateConverter()
        .getCertificate(
          JcaX509v3CertificateBuilder(
              rootCaCertificate,
              BigInteger(160, SecureRandom()),
              Date.from(now.toJavaInstant()),
              Date.from((now + (365 * 5).days).toJavaInstant()),
              X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.CN, hostname).build(),
              serverKeyPair.public,
            )
            .addExtension(
              Extension.subjectKeyIdentifier,
              false,
              BcX509ExtensionUtils()
                .createSubjectKeyIdentifier(
                  SubjectPublicKeyInfo.getInstance(serverKeyPair.public.encoded)
                ),
            )
            .addExtension(
              Extension.subjectAlternativeName,
              false,
              GeneralNames(GeneralName(GeneralName.dNSName, hostname)),
            )
            .addExtension(Extension.basicConstraints, false, BasicConstraints(false))
            .build(
              JcaContentSignerBuilder("SHA256withECDSA")
                .setProvider(BouncyCastleProvider())
                .build(privateKey)
            )
        )
    val newEntry =
      CachedCertificate(
        cert = impersonatingCertificate,
        key = serverKeyPair.private,
        sslContext = buildSslContext(impersonatingCertificate, serverKeyPair.private),
        cachedAt = now,
      )

    val evicted = mutableListOf<SslContext>()
    val updatedCache =
      certificateCache.updateAndGet { currentCache ->
        evicted.clear()
        val kept = mutableMapOf<String, CachedCertificate>()
        for ((host, entry) in currentCache) {
          if (host == hostname || now >= (entry.cachedAt + config.certCacheDuration)) {
            evicted.add(entry.sslContext)
          } else {
            kept[host] = entry
          }
        }
        kept[hostname] = newEntry
        kept
      }
    evicted.forEach { releaseSilently(it) }

    return updatedCache[hostname]
      ?: throw RuntimeException("cached certificate mysteriously disappeared")
  }

  private fun releaseSilently(sslContext: SslContext) {
    if (sslContext is ReferenceCounted) {
      try {
        sslContext.release()
      } catch (_: IllegalStateException) {
        // already released
      }
    }
  }
}
