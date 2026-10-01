package com.fairshare.android.core.domain.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun testMinorUnitArithmetic() {
        val m1 = Money(10050L, Currency.INR) // ₹100.50
        val m2 = Money(5025L, Currency.INR)  // ₹50.25

        val sum = m1 + m2
        assertEquals(15075L, sum.amountMinor)
        assertEquals(15075L, sum.paise)

        val diff = m1 - m2
        assertEquals(5025L, diff.amountMinor)

        val mult = m2 * 2
        assertEquals(10050L, mult.amountMinor)

        val neg = -m1
        assertEquals(-10050L, neg.amountMinor)
        assertTrue(neg.isNegative)
        assertFalse(neg.isPositive)
        assertEquals(10050L, neg.abs().amountMinor)
    }

    @Test
    fun testDeterministicFormatting() {
        val m1 = Money(10000L, Currency.INR)
        assertEquals("₹100", m1.formatted())

        val m2 = Money(10050L, Currency.INR)
        assertEquals("₹100.50", m2.formatted())

        val m3 = Money(5L, Currency.INR)
        assertEquals("₹0.05", m3.formatted())

        val m4 = Money(-250000L, Currency.INR)
        assertEquals("-₹2,500", m4.formatted())

        val usd = Money(123456L, Currency.USD)
        assertEquals("$1,234.56", usd.formatted())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCurrencyMismatchThrows() {
        val inr = Money(100L, Currency.INR)
        val usd = Money(100L, Currency.USD)
        val unused = inr + usd
    }
}
