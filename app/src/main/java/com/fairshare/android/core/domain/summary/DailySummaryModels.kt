package com.fairshare.android.core.domain.summary

import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.SettlementPayment

/**
 * Deterministic evidence-based Daily Summary Facts.
 * 100% derived from domain data with zero AI hallucination or guesswork.
 */
data class DailySummaryFacts(
    val dateEpochMs: Long,
    val totalSpentToday: Money,
    val expenseCountToday: Int,
    val categoryBreakdown: Map<ExpenseCategory, Money>,
    val plannedDailySpending: Money?,
    val differenceFromPlanned: Money?,
    val baseBudgetRemaining: Money?,
    val bufferRemaining: Money?,
    val suggestedTomorrowLimit: Money?,
    val isOverPace: Boolean,
    val headline: String,
    val summaryNotes: List<String>,
    val todayExpenses: List<Expense> = emptyList(),
    val projectedTotalSpending: Money? = null,
    val payerBreakdown: Map<String, Money> = emptyMap(),
    val payerNameBreakdown: Map<String, Money> = emptyMap(),
    val paymentsToday: List<SettlementPayment> = emptyList(),
    val totalSettledToday: Money? = null,
    val isGroupSettled: Boolean? = null
)
