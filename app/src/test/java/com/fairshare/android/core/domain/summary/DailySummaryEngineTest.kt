package com.fairshare.android.core.domain.summary

import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.ExpensePayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailySummaryEngineTest {

    @Test
    fun testDailySummaryWithoutBudget() {
        val now = 1000000L
        val expenses = listOf(
            Expense(
                id = "e1",
                description = "Lunch",
                totalAmount = Money(142000L), // ₹1,420
                payers = listOf(ExpensePayer("1", Money(142000L))),
                participants = emptyList(),
                category = ExpenseCategory.FOOD,
                timestamp = now + 1000
            ),
            Expense(
                id = "e2",
                description = "Taxi",
                totalAmount = Money(62000L), // ₹620
                payers = listOf(ExpensePayer("1", Money(62000L))),
                participants = emptyList(),
                category = ExpenseCategory.TRANSPORT,
                timestamp = now + 2000
            )
        )

        val facts = DailySummaryEngine.generateDailySummary(
            allExpenses = expenses,
            dayStartEpochMs = now,
            dayEndEpochMs = now + 86400000L
        )

        assertEquals(204000L, facts.totalSpentToday.amountMinor)
        assertEquals(2, facts.expenseCountToday)
        assertEquals(142000L, facts.categoryBreakdown[ExpenseCategory.FOOD]?.amountMinor)
        assertEquals(62000L, facts.categoryBreakdown[ExpenseCategory.TRANSPORT]?.amountMinor)
        assertTrue(facts.headline.contains("₹2,040"))
    }

    @Test
    fun testDailySummaryWithBudget() {
        val now = 1000000L
        val budget = BudgetPlan(
            baseBudget = Money(2000000L), // ₹20,000 for 10 days = ₹2,000/day
            emergencyBuffer = Money(500000L),
            startDateEpochMs = now,
            endDateEpochMs = now + (10 * 86400000L)
        )

        val expenses = listOf(
            Expense(
                id = "e1",
                description = "Team dinner",
                totalAmount = Money(284000L), // ₹2,840 today (above ₹2,000 planned pace)
                payers = listOf(ExpensePayer("1", Money(284000L))),
                participants = emptyList(),
                category = ExpenseCategory.FOOD,
                timestamp = now + 5000
            )
        )

        val facts = DailySummaryEngine.generateDailySummary(
            allExpenses = expenses,
            dayStartEpochMs = now,
            dayEndEpochMs = now + 86400000L,
            budgetPlan = budget
        )

        assertEquals(284000L, facts.totalSpentToday.amountMinor)
        assertTrue(facts.isOverPace)
        assertEquals(200000L, facts.plannedDailySpending?.amountMinor)
        assertEquals(84000L, facts.differenceFromPlanned?.amountMinor) // +₹840
    }
}
