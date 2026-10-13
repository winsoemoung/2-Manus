package com.example.offlinepos.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
object PinHasher {
    private const val ITERATIONS = 210_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also {
        SecureRandom().nextBytes(it)
    }
    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
    fun verify(pin: String, salt: ByteArray, expected: ByteArray): Boolean =
        java.security.MessageDigest.isEqual(hash(pin, salt), expected)
}