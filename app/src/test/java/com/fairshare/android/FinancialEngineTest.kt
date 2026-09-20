package com.fairshare.android

import com.fairshare.android.core.common.FinancialEngine
import com.fairshare.android.core.model.Expense
import com.fairshare.android.core.model.Member
import com.fairshare.android.core.model.Money
import com.fairshare.android.core.model.Payment
import com.fairshare.android.core.model.PaymentMode
import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialEngineTest {

    @Test
    fun testEqualSplitCalculation() {
        val alice = Member("1", "Alice")
        val bob = Member("2", "Bob")
        val charlie = Member("3", "Charlie")
        val members = listOf(alice, bob, charlie)

        // Alice pays ₹300 for all 3 members (100 each)
        val expense = Expense(
            id = "e1",
            payerId = "1",
            amount = Money.fromRupees(300L),
            involvedMemberIds = listOf("1", "2", "3"),
            mode = PaymentMode.ONLINE,
            description = "Dinner"
        )

        val summary = FinancialEngine.calculateSummary(members, listOf(expense), emptyList())

        assertEquals(30000L, summary.totalSpend.paise)
        assertEquals(10000L, summary.perPersonShare.paise)

        // Alice paid 300, owes 100 -> net +200
        val aliceBalance = summary.balances.first { it.memberId == "1" }
        assertEquals(20000L, aliceBalance.netBalance.paise)

        // Bob owes 100 -> net -100
        val bobBalance = summary.balances.first { it.memberId == "2" }
        assertEquals(-10000L, bobBalance.netBalance.paise)

        // Charlie owes 100 -> net -100
        val charlieBalance = summary.balances.first { it.memberId == "3" }
        assertEquals(-10000L, charlieBalance.netBalance.paise)

        // Settlements should total 200 to Alice
        val totalSettled = summary.settlements.sumOf { it.amount.paise }
        assertEquals(20000L, totalSettled)
    }

    @Test
    fun testPaymentSettlement() {
        val alice = Member("1", "Alice")
        val bob = Member("2", "Bob")
        val members = listOf(alice, bob)

        // Alice pays ₹200 for both (100 each)
        val expense = Expense(
            id = "e1",
            payerId = "1",
            amount = Money.fromRupees(200L),
            involvedMemberIds = listOf("1", "2")
        )

        // Bob pays Alice ₹100 directly
        val payment = Payment(
            id = "p1",
            fromMemberId = "2",
            toMemberId = "1",
            amount = Money.fromRupees(100L)
        )

        val summary = FinancialEngine.calculateSummary(members, listOf(expense), listOf(payment))

        // Both should now be settled (net 0)
        assertEquals(0L, summary.balances.first { it.memberId == "1" }.netBalance.paise)
        assertEquals(0L, summary.balances.first { it.memberId == "2" }.netBalance.paise)
        assertEquals(0, summary.settlements.size)
    }
}
