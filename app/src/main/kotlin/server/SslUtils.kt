package io.github.roaxio.ezcammic.server

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory

object SslUtils {

    @Volatile
    private var cachedSSLContext: SSLContext? = null

    @Synchronized
    fun getOrCreateSSLContext(): SSLContext {
        cachedSSLContext?.let { return it }

        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048, SecureRandom())
        val keyPair: KeyPair = keyPairGen.generateKeyPair()

        val notBefore = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
        val notAfter = Date(System.currentTimeMillis() + 5 * 365 * 24 * 60 * 60 * 1000L)
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val subject = X500Name("CN=EZCamMic, O=LocalShare, C=US")

        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )

        // Add standard X.509 extensions required by modern browsers (RFC 5280)
        certBuilder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        certBuilder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )
        certBuilder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(arrayOf(KeyPurposeId.id_kp_serverAuth))
        )

        // Subject Alternative Names (SAN) - Chrome and modern browsers reject certificates without SAN
        val sanNames = arrayOf(
            GeneralName(GeneralName.dNSName, "localhost"),
            GeneralName(GeneralName.dNSName, "ezcammic.local"),
            GeneralName(GeneralName.iPAddress, "127.0.0.1"),
            GeneralName(GeneralName.iPAddress, "0.0.0.0")
        )
        certBuilder.addExtension(Extension.subjectAlternativeName, false, GeneralNames(sanNames))

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        val cert = JcaX509CertificateConverter().getCertificate(certHolder)

        val keyStorePassword = "ezcammic_pass".toCharArray()
        // PKCS12 is universally supported across Android API 24-36+ without external providers
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry("ezcammic_key", keyPair.private, keyStorePassword, arrayOf(cert))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, keyStorePassword)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, SecureRandom())

        cachedSSLContext = sslContext
        return sslContext
    }

    fun createSSLServerSocket(port: Int): SSLServerSocket {
        val sslContext = getOrCreateSSLContext()
        val factory: SSLServerSocketFactory = sslContext.serverSocketFactory
        val serverSocket = factory.createServerSocket() as SSLServerSocket
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress("0.0.0.0", port))
        return serverSocket
    }
}
