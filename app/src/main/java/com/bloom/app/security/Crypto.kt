package com.bloom.app.security

import java.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM helpers shared by on-device storage and password-protected backups. */
object Crypto {
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private val rng = SecureRandom()

    fun randomBytes(n: Int) = ByteArray(n).also { rng.nextBytes(it) }

    /** Output layout: IV (12) || ciphertext+tag. [aad] is authenticated but not stored. */
    fun seal(key: SecretKey, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        // Keystore-backed keys must generate their own IV (randomized encryption is enforced);
        // software keys (password-derived backups, tests) get a fresh random IV from us.
        if (key is SecretKeySpec) c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, randomBytes(IV_BYTES)))
        else c.init(Cipher.ENCRYPT_MODE, key)
        if (aad != null) c.updateAAD(aad)
        return c.iv + c.doFinal(plain)
    }

    fun open(key: SecretKey, sealed: ByteArray, aad: ByteArray? = null): ByteArray {
        require(sealed.size > IV_BYTES + 16) { "Ciphertext too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, sealed, 0, IV_BYTES))
        if (aad != null) c.updateAAD(aad)
        return c.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    fun pbkdf2(secret: CharArray, salt: ByteArray, iterations: Int, bytes: Int = 32): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, bytes * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }

    fun aesKey(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "AES")

    fun constantTimeEquals(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)

    fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)
}
