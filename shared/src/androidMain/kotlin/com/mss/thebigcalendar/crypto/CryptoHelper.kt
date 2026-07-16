package com.mss.thebigcalendar.crypto

import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

actual object CryptoHelper {
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12

    actual fun encrypt(plainText: String, password: String): String {
        try {
            val random = SecureRandom()
            val salt = ByteArray(SALT_LENGTH)
            random.nextBytes(salt)

            val iv = ByteArray(IV_LENGTH)
            random.nextBytes(iv)

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
            val tmp = factory.generateSecret(spec)
            val secretKey = SecretKeySpec(tmp.encoded, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmParameterSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)

            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val encoder = Base64.getEncoder()
            val saltBase64 = encoder.encodeToString(salt)
            val ivBase64 = encoder.encodeToString(iv)
            val cipherTextBase64 = encoder.encodeToString(cipherText)

            return """{"encrypted":true,"salt":"$saltBase64","iv":"$ivBase64","ciphertext":"$cipherTextBase64"}"""
        } catch (e: Exception) {
            throw RuntimeException("Erro na criptografia", e)
        }
    }

    actual fun decrypt(encryptedText: String, password: String): String {
        try {
            val salt = extractJsonValue(encryptedText, "salt") ?: throw IllegalArgumentException("Sal não encontrado")
            val iv = extractJsonValue(encryptedText, "iv") ?: throw IllegalArgumentException("IV não encontrado")
            val ciphertext = extractJsonValue(encryptedText, "ciphertext") ?: throw IllegalArgumentException("Texto cifrado não encontrado")

            val decoder = Base64.getDecoder()
            val saltBytes = decoder.decode(salt)
            val ivBytes = decoder.decode(iv)
            val cipherTextBytes = decoder.decode(ciphertext)

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec: KeySpec = PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH)
            val tmp = factory.generateSecret(spec)
            val secretKey = SecretKeySpec(tmp.encoded, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmParameterSpec = GCMParameterSpec(128, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

            val decryptedBytes = cipher.doFinal(cipherTextBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw RuntimeException("Erro na descriptografia (verifique a senha)", e)
        }
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val matchResult = pattern.find(json)
        return matchResult?.groupValues?.get(1)
    }

    actual fun isEncrypted(text: String): Boolean {
        return text.contains("\"encrypted\"") && text.contains("true")
    }
}
