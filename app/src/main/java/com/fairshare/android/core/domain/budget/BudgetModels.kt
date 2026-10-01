package com.fairshare.android.core.domain.budget

import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.ExpenseCategory

enum class BufferState {
    UNUSED,
    ACTIVE,
    NEAR_LIMIT,
    EXHAUSTED
}

data class CategoryBudgetState(
    val category: ExpenseCategory,
    val allocated: Money,
    val spent: Money,
    val remaining: Money,
    val isExceeded: Boolean,
    val percentageUsed: Double
)

data class BudgetPlan(
    val id: String = "",
    val baseBudget: Money,
    val emergencyBuffer: Money,
    val startDateEpochMs: Long,
    val endDateEpochMs: Long,
    val timezone: String = "UTC",
    val categoryAllocations: Map<ExpenseCategory, Money> = emptyMap(),
    val alertThresholdPercentage: Int = 80
) {
    val totalEnvelope: Money
        get() = baseBudget + emergencyBuffer
}

data class BudgetState(
    val plan: BudgetPlan,
    val totalSpent: Money,
    val remainingBaseBudget: Money,
    val baseBudgetOverrun: Money,
    val remainingBuffer: Money,
    val bufferConsumed: Money,
    val totalEnvelopeRemaining: Money,
    val isBaseBudgetExceeded: Boolean,
    val isBufferExhausted: Boolean,
    val bufferState: BufferState = BufferState.UNUSED,
    val percentageUsed: Double,
    val categorySpending: Map<ExpenseCategory, Money>,
    val categoryStates: List<CategoryBudgetState> = emptyList()
)

data class SpendingVelocity(
    val totalDays: Int,
    val elapsedDays: Int,
    val remainingDays: Int,
    val plannedDailyRate: Money,
    val actualDailyRate: Money,
    val velocityRatio: Double,
    val isPacingAhead: Boolean
)

data class BudgetProjection(
    val projectedTotalSpending: Money,
    val projectedBaseOverrun: Money,
    val willExceedBaseBudget: Boolean,
    val willExceedEnvelope: Boolean
)
