package com.jupiter.filemanager.data.transfer

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * One explicitly approved Jupiscan Relay session.
 *
 * The token is generated in memory, is never persisted, and is compared in constant time. It
 * authorizes only the short-lived local server instance that created it; stopping the server or
 * expiring the session invalidates every previously copied pairing link.
 */
data class RelaySession(
    val token: String,
    val expiresAtMillis: Long,
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis

    fun isAuthorized(candidate: String?, nowMillis: Long): Boolean {
        if (isExpired(nowMillis) || candidate.isNullOrBlank()) return false
        return MessageDigest.isEqual(
            token.toByteArray(StandardCharsets.UTF_8),
            candidate.toByteArray(StandardCharsets.UTF_8),
        )
    }

    fun remainingMillis(nowMillis: Long): Long = (expiresAtMillis - nowMillis).coerceAtLeast(0L)

    companion object {
        const val DEFAULT_DURATION_MILLIS: Long = 10L * 60L * 1000L

        fun create(
            nowMillis: Long = System.currentTimeMillis(),
            durationMillis: Long = DEFAULT_DURATION_MILLIS,
            random: SecureRandom = SecureRandom(),
        ): RelaySession {
            require(durationMillis > 0L) { "Relay session duration must be positive" }
            val bytes = ByteArray(TOKEN_BYTES)
            random.nextBytes(bytes)
            return RelaySession(
                token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
                expiresAtMillis = nowMillis + durationMillis,
            )
        }

        private const val TOKEN_BYTES = 32
    }
}
