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
package de.akquinet.tim.proxy

import io.netty.buffer.ByteBufAllocator
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SupportedCipherSuiteFilter
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

interface OutboundProxyCertificateManager {
    val rootCaCertificate: X509Certificate
    val privateKey: PrivateKey
    fun impersonatingSslEngine(hostname: String): SSLEngine
}

class OutboundProxyCertificateManagerImpl(
    config: ProxyConfiguration.OutboundProxyConfiguration,
    fileSystem: FileSystem,
) : OutboundProxyCertificateManager {
    private data class CachedCertificate(val cert: X509Certificate, val key: PrivateKey)

    private val certificateCache = MutableStateFlow<Map<String, CachedCertificate>>(mapOf())
    override val rootCaCertificate: X509Certificate =
        fileSystem.read(config.caCertificateFile.toPath()) {
            CertificateFactory.getInstance("X.509").generateCertificate(inputStream())
        }.let {
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

    override fun impersonatingSslEngine(hostname: String): SSLEngine {
        // TODO use one certificate for all known federation domains with use of subjectAlternativeName extension
        val now = Clock.System.now()
        val generator = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }

        val cachedCertificate = getCachedCertificate(hostname, generator, now)

        val sslContext: SslContext = getSslContext(cachedCertificate)

        return sslContext.newEngine(ByteBufAllocator.DEFAULT)
    }

    private fun getSslContext(cachedCertificate: CachedCertificate): SslContext =
        SslContextBuilder.forServer(cachedCertificate.key, cachedCertificate.cert, rootCaCertificate)
            .ciphers(
                SSLContext.getInstance("TLSv1.2").apply {
                    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                        load(null, "".toCharArray())
                    }
                    init(
                        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                            init(keyStore, "".toCharArray())
                        }.keyManagers,
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                            init(keyStore)
                        }.trustManagers,
                        SecureRandom()
                    )
                }.serverSocketFactory.defaultCipherSuites.toMutableList(),
                SupportedCipherSuiteFilter.INSTANCE
            )
            .build()

    private fun getCachedCertificate(
        hostname: String,
        generator: KeyPairGenerator,
        now: Instant
    ) = (certificateCache.value[hostname]
        ?: certificateCache.updateAndGet {
            val serverKeyPair: KeyPair = generator.genKeyPair()
            val impersonatingCertificate =
                JcaX509CertificateConverter().getCertificate(
                    JcaX509v3CertificateBuilder(
                        rootCaCertificate,
                        BigInteger(160, SecureRandom()),
                        Date.from(now.toJavaInstant()),
                        Date.from((now + (365 * 5).days).toJavaInstant()),
                        X500NameBuilder(BCStyle.INSTANCE)
                            .addRDN(BCStyle.CN, hostname)
                            .build(),
                        serverKeyPair.public
                    ).addExtension(
                        Extension.subjectKeyIdentifier,
                        false,
                        BcX509ExtensionUtils().createSubjectKeyIdentifier(
                            SubjectPublicKeyInfo.getInstance(
                                serverKeyPair.public.encoded
                            )
                        )
                    ).addExtension(
                        Extension.subjectAlternativeName,
                        false,
                        GeneralNames(GeneralName(GeneralName.dNSName, hostname))
                    ).addExtension(Extension.basicConstraints, false, BasicConstraints(false))
                        .build(
                            JcaContentSignerBuilder("SHA256withECDSA")
                                .setProvider(BouncyCastleProvider())
                                .build(privateKey)
                        )
                )
            it + (hostname to CachedCertificate(impersonatingCertificate, serverKeyPair.private))
        }[hostname]
        ?: throw RuntimeException("cached certificate mysteriously disappeared"))
}
