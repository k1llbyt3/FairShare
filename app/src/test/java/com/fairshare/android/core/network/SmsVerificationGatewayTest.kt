package com.fairshare.android.core.network

import com.fairshare.android.core.network.backend.auth.RealSmsVerificationGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsVerificationGatewayTest {

    @Test
    fun sendVerificationCode_generatesValid6DigitCodeAndTicket() {
        val gateway = RealSmsVerificationGateway()
        val phone = "+919876543210"

        val ticket = gateway.sendVerificationCode(phone)
        assertNotNull(ticket.ticketId)
        assertEquals(phone, ticket.phoneNumber)
        assertTrue(ticket.expiresAt > System.currentTimeMillis())
        assertEquals(5, ticket.attemptsRemaining)

        val code = gateway.getCodeForTesting(phone)
        assertNotNull(code)
        assertEquals(6, code!!.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun verifyCode_withCorrectCode_returnsTrueAndClearsTicket() {
        val gateway = RealSmsVerificationGateway()
        val phone = "+919876543210"

        gateway.sendVerificationCode(phone)
        val code = gateway.getCodeForTesting(phone)!!

        val result = gateway.verifyCode(phone, code)
        assertTrue(result)

        // Verifying again should return false because ticket was consumed
        val retry = gateway.verifyCode(phone, code)
        assertFalse(retry)
    }

    @Test
    fun verifyCode_withIncorrectCode_decrementsAttemptsAndFails() {
        val gateway = RealSmsVerificationGateway()
        val phone = "+919876543210"

        gateway.sendVerificationCode(phone)

        val result = gateway.verifyCode(phone, "000000")
        assertFalse(result)

        val ticket = gateway.getActiveTicket(phone)
        assertNotNull(ticket)
        assertEquals(4, ticket!!.attemptsRemaining)
    }

    @Test(expected = IllegalStateException::class)
    fun verifyCode_exceedingMaxAttempts_invalidatesTicketAndThrows() {
        val gateway = RealSmsVerificationGateway(maxAttempts = 3)
        val phone = "+919876543210"

        gateway.sendVerificationCode(phone)

        gateway.verifyCode(phone, "000001")
        gateway.verifyCode(phone, "000002")
        gateway.verifyCode(phone, "000003") // 3rd attempt exceeds max
    }

    @Test(expected = IllegalStateException::class)
    fun sendVerificationCode_violatingCooldown_throwsException() {
        val gateway = RealSmsVerificationGateway(resendCooldownMs = 60000)
        val phone = "+919876543210"

        gateway.sendVerificationCode(phone)
        // Immediate second call should violate cooldown
        gateway.sendVerificationCode(phone)
    }

    @Test(expected = IllegalStateException::class)
    fun sendVerificationCode_violatingRateLimitWindow_throwsException() {
        val gateway = RealSmsVerificationGateway(
            resendCooldownMs = 0, // no cooldown for this test
            maxRequestsPerWindow = 2,
            windowDurationMs = 600000
        )
        val phone = "+919876543210"

        gateway.sendVerificationCode(phone)
        gateway.sendVerificationCode(phone)
        // 3rd call exceeds maxRequestsPerWindow
        gateway.sendVerificationCode(phone)
    }

    @Test
    fun expiredCode_failsVerification() {
        val gateway = RealSmsVerificationGateway(codeExpiryMs = -1000) // already expired
        val phone = "+919876543210"

        gateway.sendVerificationCode(phone)
        val code = gateway.getCodeForTesting(phone)!!

        val result = gateway.verifyCode(phone, code)
        assertFalse(result)
    }
}
