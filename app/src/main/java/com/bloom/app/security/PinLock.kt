package com.bloom.app.security

import com.bloom.app.domain.Settings

/** PIN hashing (PBKDF2-HMAC-SHA256, per-user random salt) and verification. */
object PinLock {
    private const val ITERATIONS = 210_000

    fun isValidFormat(pin: String) = pin.length in 4..8 && pin.all { it.isDigit() }

    /** Returns (saltB64, hashB64). */
    fun hash(pin: String): Pair<String, String> {
        val salt = Crypto.randomBytes(16)
        val h = Crypto.pbkdf2(pin.toCharArray(), salt, ITERATIONS)
        return Crypto.b64(salt) to Crypto.b64(h)
    }

    fun verify(pin: String, s: Settings): Boolean {
        val salt = s.pinSalt ?: return false
        val expected = s.pinHash ?: return false
        val h = Crypto.pbkdf2(pin.toCharArray(), Crypto.unb64(salt), ITERATIONS)
        return Crypto.constantTimeEquals(h, Crypto.unb64(expected))
    }

    /** Escalating delay (ms) after consecutive failures: none for 4, then 30 s, 1 m, 5 m, 15 m. */
    fun lockoutMs(failures: Int): Long = when {
        failures < 5 -> 0
        failures == 5 -> 30_000
        failures == 6 -> 60_000
        failures == 7 -> 300_000
        else -> 900_000
    }
}
