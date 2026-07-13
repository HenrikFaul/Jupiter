package com.jupiter.filemanager.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelaySessionTest {

    @Test
    fun onlyTheMatchingTokenIsAcceptedBeforeTheDeadline() {
        val session = RelaySession(token = "pair-token", expiresAtMillis = 1_000L)

        assertTrue(session.isAuthorized("pair-token", nowMillis = 999L))
        assertFalse(session.isAuthorized("wrong-token", nowMillis = 999L))
        assertFalse(session.isAuthorized(null, nowMillis = 999L))
    }

    @Test
    fun deadlineInvalidatesEveryPairingLink() {
        val session = RelaySession(token = "pair-token", expiresAtMillis = 1_000L)

        assertTrue(session.isExpired(1_000L))
        assertFalse(session.isAuthorized("pair-token", nowMillis = 1_000L))
        assertEquals(0L, session.remainingMillis(nowMillis = 1_000L))
    }

    @Test
    fun generatedTokenIsUrlSafeAndSessionUsesRequestedLifetime() {
        val session = RelaySession.create(nowMillis = 10L, durationMillis = 500L)

        assertEquals(510L, session.expiresAtMillis)
        assertTrue(session.token.matches(Regex("[A-Za-z0-9_-]{43}")))
    }
}
