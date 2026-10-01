package com.fairshare.android.core.domain.simulation

import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.model.SplitMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationEngineTest {

    private val alice = Member("1", "Alice")
    private val bob = Member("2", "Bob")
    private val charlie = Member("3", "Charlie")
    private val members = listOf(alice, bob, charlie)

    @Test
    fun testSimulateExpenseDoesNotMutateOriginal() {
        val originalExpenses = listOf(
            Expense(
                id = "e1",
                description = "Taxi",
                totalAmount = Money(30000L),
                payers = listOf(ExpensePayer("1", Money(30000L))),
                participants = listOf(ExpenseParticipant("1"), ExpenseParticipant("2"), ExpenseParticipant("3")),
                splitMethod = SplitMethod.EQUAL
            )
        )
        val originalPayments = emptyList<SettlementPayment>()

        val candidateExpense = Expense(
            id = "e2",
            description = "Dinner",
            totalAmount = Money(60000L),
            payers = listOf(ExpensePayer("2", Money(60000L))),
            participants = listOf(ExpenseParticipant("1"), ExpenseParticipant("2"), ExpenseParticipant("3")),
            splitMethod = SplitMethod.EQUAL
        )

        val result = SimulationEngine.simulateExpense(
            members = members,
            currentExpenses = originalExpenses,
            currentPayments = originalPayments,
            candidateExpense = candidateExpense
        )

        // Verify original lists were untouched
        assertEquals(1, originalExpenses.size)
        assertEquals(0, originalPayments.size)

        // Previous: Alice was owed 200, Bob owed 100, Charlie owed 100
        assertEquals(20000L, result.previousBalances.first { it.memberId == "1" }.netBalance.amountMinor)
        assertEquals(-10000L, result.previousBalances.first { it.memberId == "2" }.netBalance.amountMinor)

        // Simulated: Candidate expense of 600 paid by Bob (200 each)
        // Alice: net 200 - 200 = 0
        // Bob: net -100 + 400 = +300
        // Charlie: net -100 - 200 = -300
        assertEquals(0L, result.simulatedBalances.first { it.memberId == "1" }.netBalance.amountMinor)
        assertEquals(30000L, result.simulatedBalances.first { it.memberId == "2" }.netBalance.amountMinor)
        assertEquals(-30000L, result.simulatedBalances.first { it.memberId == "3" }.netBalance.amountMinor)

        // Verify deltas
        assertEquals(-20000L, result.balanceDeltas["1"]?.amountMinor)
        assertEquals(40000L, result.balanceDeltas["2"]?.amountMinor)
        assertEquals(-20000L, result.balanceDeltas["3"]?.amountMinor)
    }

    @Test
    fun testSimulatePaymentSettlesDebts() {
        val originalExpenses = listOf(
            Expense(
                id = "e1",
                description = "Lunch",
                totalAmount = Money(20000L),
                payers = listOf(ExpensePayer("1", Money(20000L))),
                participants = listOf(ExpenseParticipant("1"), ExpenseParticipant("2")),
                splitMethod = SplitMethod.EQUAL
            )
        )
        // Bob owes Alice ₹100
        val candidatePayment = SettlementPayment(
            id = "p1",
            fromMemberId = "2",
            toMemberId = "1",
            amount = Money(10000L)
        )

        val result = SimulationEngine.simulatePayment(
            members = listOf(alice, bob),
            currentExpenses = originalExpenses,
            currentPayments = emptyList(),
            candidatePayment = candidatePayment
        )

        // Previous: 1 transfer needed
        assertEquals(1, result.previousPlan.transfers.size)
        // Simulated: 0 transfers needed (fully settled)
        assertEquals(0, result.simulatedPlan.transfers.size)
        assertTrue(result.simulatedPlan.isSettled)
        assertEquals(-1, result.transferCountDelta)
    }
}
