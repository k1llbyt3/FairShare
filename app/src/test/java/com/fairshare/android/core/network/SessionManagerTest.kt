package com.fairshare.android.core.network

import com.fairshare.android.core.network.backend.auth.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {

    @Test
    fun createSession_generatesUnique256BitHexToken() {
        val manager = SessionManager()
        val userId = "user-123"

        val session = manager.createSession(userId)
        assertNotNull(session.token)
        assertEquals(64, session.token.length) // 32 bytes hex encoded = 64 characters
        assertEquals(userId, session.userId)
        assertFalse(session.isExpired)
    }

    @Test
    fun getSession_retrievesActiveSession() {
        val manager = SessionManager()
        val session = manager.createSession("user-456")

        val retrieved = manager.getSession(session.token)
        assertNotNull(retrieved)
        assertEquals(session.token, retrieved!!.token)
        assertEquals("user-456", retrieved.userId)
    }

    @Test
    fun revokeSession_invalidatesSpecificToken() {
        val manager = SessionManager()
        val session1 = manager.createSession("user-789")
        val session2 = manager.createSession("user-789")

        manager.revokeSession(session1.token)

        assertNull(manager.getSession(session1.token))
        assertNotNull(manager.getSession(session2.token))
    }

    @Test
    fun revokeAllUserSessions_invalidatesAllTokensForUser() {
        val manager = SessionManager()
        val userId = "user-multi"
        val session1 = manager.createSession(userId)
        val session2 = manager.createSession(userId)

        manager.revokeAllUserSessions(userId)

        assertNull(manager.getSession(session1.token))
        assertNull(manager.getSession(session2.token))
    }
}
