package com.fairshare.android.core.domain.budget

import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpensePayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetEngineTest {

    private val plan = BudgetPlan(
        baseBudget = Money(5000000L),      // ₹50,000 base
        emergencyBuffer = Money(500000L),  // ₹5,000 buffer
        startDateEpochMs = 1000000L,
        endDateEpochMs = 1000000L + (10 * 86400000L) // 10 days
    )

    @Test
    fun testNoExpensesRecorded() {
        val state = BudgetEngine.calculateBudgetState(plan, emptyList())
        assertEquals(0L, state.totalSpent.amountMinor)
        assertEquals(5000000L, state.remainingBaseBudget.amountMinor)
        assertEquals(500000L, state.remainingBuffer.amountMinor)
        assertEquals(5500000L, state.totalEnvelopeRemaining.amountMinor)
        assertFalse(state.isBaseBudgetExceeded)
        assertFalse(state.isBufferExhausted)
        assertEquals(0.0, state.percentageUsed, 0.001)

        val dailyTarget = BudgetEngine.calculateDailyTarget(state, 10)
        assertEquals(500000L, dailyTarget.amountMinor) // ₹5,000 / day
    }

    @Test
    fun testUnderBudget() {
        // Spent ₹20,000
        val expenses = listOf(
            Expense(
                id = "e1",
                description = "Flights",
                totalAmount = Money(2000000L),
                payers = listOf(ExpensePayer("1", Money(2000000L))),
                participants = emptyList(),
                timestamp = 1000000L + 86400000L
            )
        )

        val state = BudgetEngine.calculateBudgetState(plan, expenses)
        assertEquals(2000000L, state.totalSpent.amountMinor)
        assertEquals(3000000L, state.remainingBaseBudget.amountMinor)
        assertEquals(500000L, state.remainingBuffer.amountMinor)
        assertFalse(state.isBaseBudgetExceeded)
        assertFalse(state.isBufferExhausted)

        val daily = BudgetEngine.calculateDailyTarget(state, 6)
        assertEquals(500000L, daily.amountMinor) // 30,000 / 6 = 5,000
    }

    @Test
    fun testOverBudgetDipsIntoBuffer() {
        // Spent ₹51,000 (exceeds ₹50,000 base by ₹1,000, leaving ₹4,000 in buffer)
        val expenses = listOf(
            Expense(
                id = "e1",
                description = "Big hotel",
                totalAmount = Money(5100000L),
                payers = listOf(ExpensePayer("1", Money(5100000L))),
                participants = emptyList(),
                timestamp = 1000000L + 86400000L
            )
        )

        val state = BudgetEngine.calculateBudgetState(plan, expenses)
        assertEquals(5100000L, state.totalSpent.amountMinor)
        assertEquals(0L, state.remainingBaseBudget.amountMinor)
        assertEquals(100000L, state.baseBudgetOverrun.amountMinor)
        assertEquals(400000L, state.remainingBuffer.amountMinor)
        assertEquals(100000L, state.bufferConsumed.amountMinor)
        assertTrue(state.isBaseBudgetExceeded)
        assertFalse(state.isBufferExhausted)

        // Daily target now draws from buffer
        val daily = BudgetEngine.calculateDailyTarget(state, 2)
        assertEquals(200000L, daily.amountMinor) // 4,000 / 2 = 2,000
    }

    @Test
    fun testBufferExhausted() {
        // Spent ₹56,000 (exceeds base ₹50,000 + buffer ₹5,000)
        val expenses = listOf(
            Expense(
                id = "e1",
                description = "Massive bill",
                totalAmount = Money(5600000L),
                payers = listOf(ExpensePayer("1", Money(5600000L))),
                participants = emptyList(),
                timestamp = 1000000L + 86400000L
            )
        )

        val state = BudgetEngine.calculateBudgetState(plan, expenses)
        assertEquals(0L, state.remainingBaseBudget.amountMinor)
        assertEquals(0L, state.remainingBuffer.amountMinor)
        assertTrue(state.isBaseBudgetExceeded)
        assertTrue(state.isBufferExhausted)

        val daily = BudgetEngine.calculateDailyTarget(state, 5)
        assertEquals(0L, daily.amountMinor)
    }

    @Test
    fun testSpendingVelocityAndProjection() {
        val currentEpoch = 1000000L + (5 * 86400000L) // Day 5 of 10
        val totalSpent = Money(3000000L) // ₹30,000 spent in 5 days (6,000/day vs planned 5,000/day)

        val velocity = BudgetEngine.calculateSpendingVelocity(plan, totalSpent, currentEpoch)
        assertEquals(10, velocity.totalDays)
        assertEquals(5, velocity.elapsedDays)
        assertEquals(5, velocity.remainingDays)
        assertEquals(500000L, velocity.plannedDailyRate.amountMinor)
        assertEquals(600000L, velocity.actualDailyRate.amountMinor)
        assertTrue(velocity.isPacingAhead)

        val projection = BudgetEngine.calculateProjection(plan, totalSpent, currentEpoch)
        // Projected: 30,000 + (6,000 * 5) = 60,000
        assertEquals(6000000L, projection.projectedTotalSpending.amountMinor)
        assertTrue(projection.willExceedBaseBudget) // 60,000 > 50,000
        assertTrue(projection.willExceedEnvelope)   // 60,000 > 55,000
    }
}
