package com.mss.thebigcalendar.crypto

expect object CryptoHelper {
    fun encrypt(plainText: String, password: String): String
    fun decrypt(encryptedText: String, password: String): String
    fun isEncrypted(text: String): Boolean
}

class DecryptionRequiredException(message: String) : Exception(message)
class DecryptionFailedException(message: String) : Exception(message)
