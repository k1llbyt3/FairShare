package com.fairshare.android.core.domain.ledger

import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Adjustment
import com.fairshare.android.core.domain.model.AdjustmentType
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.model.SplitMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerEngineTest {

    private val alice = Member("1", "Alice")
    private val bob = Member("2", "Bob")
    private val charlie = Member("3", "Charlie")
    private val members = listOf(alice, bob, charlie)

    @Test
    fun testSingleExpenseEqualSplitLedger() {
        // Alice pays ₹300 for Alice, Bob, Charlie (100 each)
        val expense = Expense(
            id = "e1",
            description = "Dinner",
            totalAmount = Money(30000L),
            payers = listOf(ExpensePayer("1", Money(30000L))),
            participants = listOf(
                ExpenseParticipant("1"),
                ExpenseParticipant("2"),
                ExpenseParticipant("3")
            ),
            splitMethod = SplitMethod.EQUAL
        )

        val ledger = LedgerEngine.calculateLedger(members, listOf(expense), emptyList())

        assertTrue(ledger.isBalanced)
        assertEquals(30000L, ledger.totalExpenseSpend.amountMinor)

        // Alice: paid 300, consumed 100 -> net +200
        val aLedger = ledger.memberLedgers["1"]!!
        assertEquals(30000L, aLedger.paid.amountMinor)
        assertEquals(10000L, aLedger.consumed.amountMinor)
        assertEquals(20000L, aLedger.netBalance.amountMinor)

        // Bob: paid 0, consumed 100 -> net -100
        val bLedger = ledger.memberLedgers["2"]!!
        assertEquals(0L, bLedger.paid.amountMinor)
        assertEquals(10000L, bLedger.consumed.amountMinor)
        assertEquals(-10000L, bLedger.netBalance.amountMinor)

        // Charlie: paid 0, consumed 100 -> net -100
        val cLedger = ledger.memberLedgers["3"]!!
        assertEquals(0L, cLedger.paid.amountMinor)
        assertEquals(10000L, cLedger.consumed.amountMinor)
        assertEquals(-10000L, cLedger.netBalance.amountMinor)
    }

    @Test
    fun testMultiplePayersExpense() {
        // Expense ₹2,000 total. Alice pays ₹1,200, Bob pays ₹800.
        // Shared equally by Alice, Bob, Charlie (3 people -> 667, 667, 666)
        val expense = Expense(
            id = "e2",
            description = "Resort",
            totalAmount = Money(200000L),
            payers = listOf(
                ExpensePayer("1", Money(120000L)),
                ExpensePayer("2", Money(80000L))
            ),
            participants = listOf(
                ExpenseParticipant("1"),
                ExpenseParticipant("2"),
                ExpenseParticipant("3")
            ),
            splitMethod = SplitMethod.EQUAL
        )

        val ledger = LedgerEngine.calculateLedger(members, listOf(expense), emptyList())

        assertTrue(ledger.isBalanced)
        assertEquals(120000L, ledger.memberLedgers["1"]?.paid?.amountMinor)
        assertEquals(80000L, ledger.memberLedgers["2"]?.paid?.amountMinor)
        assertEquals(0L, ledger.memberLedgers["3"]?.paid?.amountMinor)
    }

    @Test
    fun testDirectPaymentSettlesBalance() {
        // Alice pays ₹200 for Alice and Bob
        val expense = Expense(
            id = "e1",
            description = "Lunch",
            totalAmount = Money(20000L),
            payers = listOf(ExpensePayer("1", Money(20000L))),
            participants = listOf(
                ExpenseParticipant("1"),
                ExpenseParticipant("2")
            ),
            splitMethod = SplitMethod.EQUAL
        )

        // Bob pays Alice ₹100 directly
        val payment = SettlementPayment(
            id = "p1",
            fromMemberId = "2",
            toMemberId = "1",
            amount = Money(10000L)
        )

        val ledger = LedgerEngine.calculateLedger(listOf(alice, bob), listOf(expense), listOf(payment))

        assertTrue(ledger.isBalanced)
        // Both net balances should now be 0
        assertEquals(0L, ledger.memberLedgers["1"]?.netBalance?.amountMinor)
        assertEquals(0L, ledger.memberLedgers["2"]?.netBalance?.amountMinor)
    }

    @Test
    fun testPartialPayment() {
        // Alice pays ₹200 for Bob (Bob owes ₹200)
        val expense = Expense(
            id = "e1",
            description = "Gift",
            totalAmount = Money(20000L),
            payers = listOf(ExpensePayer("1", Money(20000L))),
            participants = listOf(ExpenseParticipant("2")),
            splitMethod = SplitMethod.EQUAL
        )

        // Bob partially pays Alice ₹80
        val payment = SettlementPayment(
            id = "p1",
            fromMemberId = "2",
            toMemberId = "1",
            amount = Money(8000L)
        )

        val ledger = LedgerEngine.calculateLedger(listOf(alice, bob), listOf(expense), listOf(payment))

        assertTrue(ledger.isBalanced)
        assertEquals(12000L, ledger.memberLedgers["1"]?.netBalance?.amountMinor)  // Alice is owed ₹120
        assertEquals(-12000L, ledger.memberLedgers["2"]?.netBalance?.amountMinor) // Bob owes ₹120
    }

    @Test
    fun testAdjustmentRefundAndReversal() {
        // Alice pays ₹100 for Bob (Bob owes ₹100)
        val expense = Expense(
            id = "e1",
            description = "Ticket",
            totalAmount = Money(10000L),
            payers = listOf(ExpensePayer("1", Money(10000L))),
            participants = listOf(ExpenseParticipant("2")),
            splitMethod = SplitMethod.EQUAL
        )

        // Merchant refunds ₹30 to Bob
        val refund = Adjustment(
            id = "a1",
            memberId = "2",
            amount = Money(3000L),
            type = AdjustmentType.REFUND,
            description = "Partial refund on ticket"
        )
        // Matching credit to group/Alice
        val aliceAdj = Adjustment(
            id = "a2",
            memberId = "1",
            amount = Money(3000L),
            type = AdjustmentType.REVERSAL,
            description = "Reduction in receivable"
        )

        val ledger = LedgerEngine.calculateLedger(listOf(alice, bob), listOf(expense), emptyList(), listOf(refund, aliceAdj))

        assertTrue(ledger.isBalanced)
        assertEquals(7000L, ledger.memberLedgers["1"]?.netBalance?.amountMinor)
        assertEquals(-7000L, ledger.memberLedgers["2"]?.netBalance?.amountMinor)
    }
}
