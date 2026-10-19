package com.zaneschepke.wireguardautotunnel.data.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Whole-file AES used by the old library. Existing `.sqlite3.aes` backups decrypt with this layout.
 */
internal object BackupCipher {
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 128
    private const val ITERATIONS = 65_536
    private const val CIPHER = "AES/GCM/NoPadding"

    private val random = SecureRandom()

    fun encrypt(data: ByteArray, password: String): ByteArray {
        val iv = ByteArray(IV_LENGTH)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key(password, iv), GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(data)
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + iv.size + encrypted.size)
        buffer.putInt(iv.size)
        buffer.put(iv)
        buffer.put(encrypted)
        return buffer.array()
    }

    fun decrypt(data: ByteArray, password: String): ByteArray {
        require(data.size >= Int.SIZE_BYTES + IV_LENGTH + TAG_BITS / 8) { "short backup" }
        val buffer = ByteBuffer.wrap(data)
        val ivLength = buffer.int
        require(ivLength == IV_LENGTH) { "unexpected iv length" }
        require(buffer.remaining() >= ivLength + TAG_BITS / 8) { "truncated backup" }
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, key(password, iv), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    private fun key(password: String, iv: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), iv, ITERATIONS, KEY_BITS)
        val encoded =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return SecretKeySpec(encoded, "AES")
    }
}
