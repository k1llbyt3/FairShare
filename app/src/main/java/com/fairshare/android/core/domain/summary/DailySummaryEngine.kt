package com.fairshare.android.core.domain.summary

import com.fairshare.android.core.domain.balance.BalanceEngine
import com.fairshare.android.core.domain.budget.BudgetEngine
import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.settlement.SettlementEngine
import kotlin.math.max

/**
 * Deterministic Daily Summary Engine.
 * Generates explainable, verifiable financial summaries for a specific day.
 */
object DailySummaryEngine {

    fun calculateDailyFacts(
        plan: BudgetPlan?,
        expenses: List<Expense>,
        targetDateEpochMs: Long,
        payments: List<SettlementPayment> = emptyList(),
        members: List<Member> = emptyList(),
        currency: Currency = plan?.baseBudget?.currency ?: expenses.firstOrNull()?.amount?.currency ?: Currency.INR
    ): DailySummaryFacts {
        val dayStart = targetDateEpochMs - (targetDateEpochMs % 86_400_000L)
        val dayEnd = dayStart + 86_400_000L - 1
        return generateDailySummary(
            allExpenses = expenses,
            dayStartEpochMs = dayStart,
            dayEndEpochMs = dayEnd,
            budgetPlan = plan,
            allPayments = payments,
            members = members,
            currency = currency
        )
    }

    fun generateDailySummary(
        allExpenses: List<Expense>,
        dayStartEpochMs: Long,
        dayEndEpochMs: Long,
        budgetPlan: BudgetPlan? = null,
        allPayments: List<SettlementPayment> = emptyList(),
        members: List<Member> = emptyList(),
        currency: Currency = Currency.INR
    ): DailySummaryFacts {
        val activeExpenses = allExpenses.filter { !it.isDeleted }
        val todayExpenses = activeExpenses.filter { it.timestamp in dayStartEpochMs..dayEndEpochMs }

        val todaySpentMinor = todayExpenses.sumOf { it.totalAmount.amountMinor }
        val totalSpentToday = Money(todaySpentMinor, currency)
        val expenseCountToday = todayExpenses.size

        // Category breakdown
        val categorySpending = HashMap<ExpenseCategory, Long>()
        for (e in todayExpenses) {
            val cur = categorySpending[e.category] ?: 0L
            categorySpending[e.category] = cur + e.totalAmount.amountMinor
        }
        val categoryBreakdown = categorySpending.mapValues { Money(it.value, currency) }

        // Payer breakdown
        val memberMap = members.associateBy { it.id }
        val payerSpending = HashMap<String, Long>()
        val payerNameSpending = HashMap<String, Long>()
        for (e in todayExpenses) {
            val cur = payerSpending[e.payerId] ?: 0L
            payerSpending[e.payerId] = cur + e.totalAmount.amountMinor

            val payerName = memberMap[e.payerId]?.name ?: "Member ${e.payerId.take(4)}"
            val curName = payerNameSpending[payerName] ?: 0L
            payerNameSpending[payerName] = curName + e.totalAmount.amountMinor
        }
        val payerBreakdown = payerSpending.mapValues { Money(it.value, currency) }
        val payerNameBreakdown = payerNameSpending.mapValues { Money(it.value, currency) }

        // Payments on this date
        val todayPayments = allPayments.filter { it.timestamp in dayStartEpochMs..dayEndEpochMs }
        val totalSettledTodayMinor = todayPayments.sumOf { it.amount.amountMinor }
        val totalSettledToday = if (todayPayments.isNotEmpty()) Money(totalSettledTodayMinor, currency) else null

        // Group settlement status
        val isGroupSettled = if (members.isNotEmpty()) {
            val balances = BalanceEngine.calculateGroupBalances(members, activeExpenses, allPayments, emptyList(), currency)
            val plan = SettlementEngine.calculateSettlement(balances, currency)
            plan.isSettled
        } else null

        var plannedDailySpending: Money? = null
        var differenceFromPlanned: Money? = null
        var baseBudgetRemaining: Money? = null
        var bufferRemaining: Money? = null
        var suggestedTomorrowLimit: Money? = null
        var isOverPace = false

        val notes = ArrayList<String>()

        if (budgetPlan != null) {
            val budgetState = BudgetEngine.calculateBudgetState(budgetPlan, activeExpenses, dayEndEpochMs)
            val velocity = BudgetEngine.calculateSpendingVelocity(budgetPlan, budgetState.totalSpent, dayEndEpochMs)

            plannedDailySpending = velocity.plannedDailyRate
            differenceFromPlanned = totalSpentToday - plannedDailySpending
            baseBudgetRemaining = budgetState.remainingBaseBudget
            bufferRemaining = budgetState.remainingBuffer
            isOverPace = totalSpentToday > plannedDailySpending

            val projection = BudgetEngine.calculateProjection(budgetPlan, budgetState.totalSpent, dayEndEpochMs)
            val tomorrowRemainingDays = max(1, velocity.remainingDays - 1)
            suggestedTomorrowLimit = BudgetEngine.calculateDailyTarget(budgetState, tomorrowRemainingDays)

            if (budgetState.isBaseBudgetExceeded) {
                notes.add("Base budget has been exceeded. Remaining emergency buffer: ${bufferRemaining.formatted()}.")
            } else if (isOverPace) {
                notes.add("Today's spending is ${differenceFromPlanned.formatted()} above the planned daily pace.")
            } else {
                notes.add("Today's spending is on track within the planned daily budget.")
            }

            if (suggestedTomorrowLimit.isPositive) {
                notes.add("Recommended daily limit for tomorrow: ${suggestedTomorrowLimit.formatted()}.")
            }

            val headline = if (budgetState.isBaseBudgetExceeded) {
                "Buffer Active: ${totalSpentToday.formatted()} spent today"
            } else if (isOverPace) {
                "Pacing Alert: ${differenceFromPlanned.formatted()} over daily pace"
            } else {
                "On Track: ${totalSpentToday.formatted()} spent today"
            }

            if (todayPayments.isNotEmpty()) {
                notes.add("${todayPayments.size} direct payments recorded today (${totalSettledToday?.formatted() ?: "₹0"} settled).")
            }

            if (isGroupSettled == true) {
                notes.add("Group settlement status: All debts are currently balanced.")
            }

            return DailySummaryFacts(
                dateEpochMs = dayStartEpochMs,
                totalSpentToday = totalSpentToday,
                expenseCountToday = expenseCountToday,
                categoryBreakdown = categoryBreakdown,
                plannedDailySpending = plannedDailySpending,
                differenceFromPlanned = differenceFromPlanned,
                baseBudgetRemaining = baseBudgetRemaining,
                bufferRemaining = bufferRemaining,
                suggestedTomorrowLimit = suggestedTomorrowLimit,
                isOverPace = isOverPace,
                headline = headline,
                summaryNotes = notes,
                todayExpenses = todayExpenses,
                projectedTotalSpending = projection.projectedTotalSpending,
                payerBreakdown = payerBreakdown,
                payerNameBreakdown = payerNameBreakdown,
                paymentsToday = todayPayments,
                totalSettledToday = totalSettledToday,
                isGroupSettled = isGroupSettled
            )
        } else {
            if (expenseCountToday > 0) {
                notes.add("Recorded $expenseCountToday expenses totaling ${totalSpentToday.formatted()}.")
            } else {
                notes.add("No expenses recorded on this date.")
            }

            if (payerNameBreakdown.isNotEmpty()) {
                val payerSummary = payerNameBreakdown.entries.joinToString(", ") { "${it.key}: ${it.value.formatted()}" }
                notes.add("Source payers: $payerSummary")
            }

            if (todayPayments.isNotEmpty()) {
                notes.add("${todayPayments.size} direct payments recorded (${totalSettledToday?.formatted() ?: "₹0"} settled).")
            }

            if (isGroupSettled == true) {
                notes.add("Group settlement status: All member debts are fully settled.")
            }

            val headline = if (expenseCountToday > 0) {
                "Today: ${totalSpentToday.formatted()} across $expenseCountToday expenses"
            } else {
                "No expenses recorded today"
            }

            return DailySummaryFacts(
                dateEpochMs = dayStartEpochMs,
                totalSpentToday = totalSpentToday,
                expenseCountToday = expenseCountToday,
                categoryBreakdown = categoryBreakdown,
                plannedDailySpending = null,
                differenceFromPlanned = null,
                baseBudgetRemaining = null,
                bufferRemaining = null,
                suggestedTomorrowLimit = null,
                isOverPace = false,
                headline = headline,
                summaryNotes = notes,
                todayExpenses = todayExpenses,
                projectedTotalSpending = null,
                payerBreakdown = payerBreakdown,
                payerNameBreakdown = payerNameBreakdown,
                paymentsToday = todayPayments,
                totalSettledToday = totalSettledToday,
                isGroupSettled = isGroupSettled
            )
        }
    }
}
