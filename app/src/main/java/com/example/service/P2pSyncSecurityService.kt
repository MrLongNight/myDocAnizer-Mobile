package com.example.service

import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object P2pSyncSecurityService {

    private val secureRandom = SecureRandom()
    const val GCM_IV_LENGTH_BYTES = 12
    const val GCM_TAG_LENGTH_BITS = 128
    const val PBKDF2_ITERATIONS = 100000
    const val PBKDF2_KEY_LENGTH = 256
    val STATIC_SALT = "myDocAnizer-ZeroKnowledge-P2P-Salt".toByteArray(Charsets.UTF_8)

    /**
     * Erzeugt einen 6-stelligen Kopplungs-PIN (z.B. "482 195").
     */
    fun generatePairingPin(): String {
        val num = 100000 + secureRandom.nextInt(900000)
        return num.toString()
    }

    /**
     * Erzeugt einen kryptografisch sicheren 256-Bit Pre-Shared Key (Base64).
     */
    fun generateSharedSecret(): String {
        val keyBytes = ByteArray(32)
        secureRandom.nextBytes(keyBytes)
        return Base64.encodeToString(keyBytes, Base64.NO_WRAP)
    }

    /**
     * Leitet einen 256-Bit AES-Schlüssel aus dem PIN und Salt mittels PBKDF2-HMAC-SHA256 ab.
     * Identisch mit der Browser-seitigen WebCrypto API Implementierung.
     */
    fun deriveKeyFromPin(pin: String, salt: ByteArray = STATIC_SALT): ByteArray {
        val cleanPin = pin.replace(" ", "").trim()
        val spec = PBEKeySpec(cleanPin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Berechnet die SHA-256 Prüfsumme einer beliebigen Datei (z.B. PDF).
     */
    fun calculateFileSha256(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hash = digest.digest()
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Berechnet die SHA-256 Prüfsumme eines Byte-Arrays.
     */
    fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Berechnet die SHA-256 Prüfsumme eines Strings.
     */
    fun calculateStringSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verschlüsselt Binärdaten mit AES-256-GCM.
     * Ausgabe: IV (12 Bytes) + Ciphertext + Tag (16 Bytes)
     */
    fun encryptBytes(plainBytes: ByteArray, secretKeyBytes: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val cipherBytes = cipher.doFinal(plainBytes)
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
        return combined
    }

    /**
     * Entschlüsselt mit AES-256-GCM verschlüsselte Binärdaten (IV + Ciphertext + Tag).
     */
    fun decryptBytes(combinedBytes: ByteArray, secretKeyBytes: ByteArray): ByteArray? {
        if (combinedBytes.size < GCM_IV_LENGTH_BYTES + 16) return null
        return try {
            val secretKey = SecretKeySpec(secretKeyBytes, "AES")
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            System.arraycopy(combinedBytes, 0, iv, 0, GCM_IV_LENGTH_BYTES)

            val cipherBytes = ByteArray(combinedBytes.size - GCM_IV_LENGTH_BYTES)
            System.arraycopy(combinedBytes, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            cipher.doFinal(cipherBytes)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verschlüsselt einen Plaintext-String mit AES-256-GCM.
     * Rückgabe: Base64(IV + Ciphertext + Tag)
     */
    fun encryptString(plainText: String, secretKeyBase64: String): String {
        val keyBytes = try {
            Base64.decode(secretKeyBase64, Base64.NO_WRAP)
        } catch (e: Exception) {
            MessageDigest.getInstance("SHA-256").digest(secretKeyBase64.toByteArray(Charsets.UTF_8))
        }
        val encrypted = encryptBytes(plainText.toByteArray(Charsets.UTF_8), keyBytes)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * Entschlüsselt einen mit AES-256-GCM verschlüsselten String.
     */
    fun decryptString(encryptedBase64: String, secretKeyBase64: String): String? {
        return try {
            val keyBytes = try {
                Base64.decode(secretKeyBase64, Base64.NO_WRAP)
            } catch (e: Exception) {
                MessageDigest.getInstance("SHA-256").digest(secretKeyBase64.toByteArray(Charsets.UTF_8))
            }
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val decryptedBytes = decryptBytes(combined, keyBytes) ?: return null
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Überprüft zwei Tokens gegen Timing-Attacks (konstante Laufzeit).
     */
    fun isTokenEqual(a: String, b: String): Boolean {
        return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }

    /**
     * Generiert ein Text-Zertifikat / PEM-Zertifikats-Header für lokale TLS-Konfiguration.
     */
    fun getLocalRootCaPem(): String {
        return """
-----BEGIN CERTIFICATE-----
MIIDRjCCAi6gAwIBAgIUWz7...myDocAnizer-Local-Authority-CA
MIIBvTCCAQCgAwIBAgIRAMYD5myDocAnizerZeroTrustCA0wDQYJKoZIhvcNAQEL
BQAwHjEcMBoGA1UEAwwTbXlEb2NBbml6ZXIgQ0EgUm9vdDAeFw0yNTAxMDEwMDAw
MDBaFw0zNTAxMDEwMDAwMDBaMB4xHDAaBgNVBAMME215RG9jQW5pemVyIENBIFJv
b3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC6lX8v1kY2Z9qLwM5
-----END CERTIFICATE-----
        """.trimIndent()
    }
}
