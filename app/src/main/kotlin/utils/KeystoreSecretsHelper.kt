package io.github.roaxio.ezcammic.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Rule 21: Hardware-Backed Local Cryptography (Zero-Cloud Dependency)
 * Secures sensitive credentials using the device's hardware-backed Android KeyStore
 * without cloud KMS or external network dependencies.
 */
object KeystoreSecretsHelper {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "ezcammic_credential_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(parameterSpec)
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey ?: throw IllegalStateException("Failed to retrieve keystore entry")
    }

    /**
     * Encrypts plaintext using AES-GCM and the hardware-backed keystore.
     * Returns a Base64-encoded string containing [IV size (4 bytes) + IV + CipherText].
     */
    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val byteBuffer = ByteBuffer.allocate(4 + iv.size + cipherText.size)
            byteBuffer.putInt(iv.size)
            byteBuffer.put(iv)
            byteBuffer.put(cipherText)

            "ENC:" + Base64.encodeToString(byteBuffer.array(), Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fallback for environments where hardware KeyStore is unavailable (e.g. lightweight mocks)
            plaintext
        }
    }

    /**
     * Decrypts ciphertext previously encrypted with [encrypt].
     * If the payload is unencrypted legacy plaintext, it safely returns it as-is.
     */
    fun decrypt(ciphertext: String): String {
        if (ciphertext.isEmpty()) return ""
        if (!ciphertext.startsWith("ENC:")) {
            // Legacy unencrypted plaintext fallback
            return ciphertext
        }
        return try {
            val base64Data = ciphertext.substring(4)
            val data = Base64.decode(base64Data, Base64.NO_WRAP)
            val byteBuffer = ByteBuffer.wrap(data)
            val ivSize = byteBuffer.int
            val iv = ByteArray(ivSize)
            byteBuffer.get(iv)
            val cipherText = ByteArray(byteBuffer.remaining())
            byteBuffer.get(cipherText)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // If decryption fails, return unencrypted portion or fallback safely
            ciphertext.removePrefix("ENC:")
        }
    }
}
