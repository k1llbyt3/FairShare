package com.fairshare.android.core.domain.settlement

import com.fairshare.android.core.domain.balance.MemberBalance
import com.fairshare.android.core.domain.currency.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementEngineTest {

    @Test
    fun testEveryoneSettled() {
        val balances = listOf(
            MemberBalance("1", "Alice", Money.ZERO),
            MemberBalance("2", "Bob", Money.ZERO)
        )

        val plan = SettlementEngine.calculateSettlement(balances)
        assertTrue(plan.isSettled)
        assertEquals(0, plan.transfers.size)
        assertEquals(0L, plan.totalTransferred.amountMinor)
    }

    @Test
    fun testTwoMemberSettlement() {
        // Alice is owed ₹100, Bob owes ₹100
        val balances = listOf(
            MemberBalance("1", "Alice", Money(10000L)),
            MemberBalance("2", "Bob", Money(-10000L))
        )

        val plan = SettlementEngine.calculateSettlement(balances)
        assertFalse(plan.isSettled)
        assertEquals(1, plan.transfers.size)

        val transfer = plan.transfers.first()
        assertEquals("2", transfer.fromMemberId)
        assertEquals("1", transfer.toMemberId)
        assertEquals(10000L, transfer.amount.amountMinor)
        assertEquals(10000L, plan.totalTransferred.amountMinor)
    }

    @Test
    fun testThreeMemberSettlementGreedyMatching() {
        // Alice is owed ₹200. Bob owes ₹100, Charlie owes ₹100.
        val balances = listOf(
            MemberBalance("1", "Alice", Money(20000L)),
            MemberBalance("2", "Bob", Money(-10000L)),
            MemberBalance("3", "Charlie", Money(-10000L))
        )

        val plan = SettlementEngine.calculateSettlement(balances)
        assertEquals(2, plan.transfers.size)
        assertEquals(20000L, plan.totalTransferred.amountMinor)

        val recipients = plan.transfers.map { it.toMemberId }.toSet()
        assertEquals(setOf("1"), recipients) // Alice receives all
    }

    @Test
    fun testLegacyFormattedOutput() {
        val balances = listOf(
            MemberBalance("1", "Alice", Money(10000L)),
            MemberBalance("2", "Bob", Money(-10000L))
        )
        val plan = SettlementEngine.calculateSettlement(balances)
        val summaryText = SettlementEngine.formatLegacySummary("GOA", Money(10000L), 2, plan)

        assertTrue(summaryText.contains("GOA"))
        assertTrue(summaryText.contains("Total Spent: ₹100"))
        assertTrue(summaryText.contains("Bob pays Alice ₹100"))
    }
}
