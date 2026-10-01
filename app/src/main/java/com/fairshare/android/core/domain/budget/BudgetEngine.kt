package com.fairshare.android.core.domain.budget

import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import kotlin.math.max

/**
 * Deterministic Budget Engine.
 * Authoritative calculations for Base Budget, Emergency Buffer, Daily Limits,
 * Spending Velocity, and Forecast Projections.
 */
object BudgetEngine {

    private const val MS_PER_DAY = 86_400_000L

    fun calculateBudgetState(
        plan: BudgetPlan,
        expenses: List<Expense>,
        currentEpochMs: Long = System.currentTimeMillis()
    ): BudgetState {
        val currency = plan.baseBudget.currency
        val activeExpenses = expenses.filter { !it.isDeleted }

        // Filter expenses within budget range if valid dates given
        val inScopeExpenses = if (plan.startDateEpochMs > 0L && plan.endDateEpochMs >= plan.startDateEpochMs) {
            activeExpenses.filter { it.timestamp in plan.startDateEpochMs..plan.endDateEpochMs }
        } else {
            activeExpenses
        }

        val totalSpentMinor = inScopeExpenses.sumOf { it.totalAmount.amountMinor }
        val totalSpent = Money(totalSpentMinor, currency)

        // Category breakdown
        val categorySpending = HashMap<ExpenseCategory, Long>()
        for (e in inScopeExpenses) {
            val cur = categorySpending[e.category] ?: 0L
            categorySpending[e.category] = cur + e.totalAmount.amountMinor
        }
        val categoryMap = categorySpending.mapValues { Money(it.value, currency) }

        val baseBudgetMinor = plan.baseBudget.amountMinor
        val bufferMinor = plan.emergencyBuffer.amountMinor
        val envelopeMinor = baseBudgetMinor + bufferMinor

        val remainingBaseMinor = max(0L, baseBudgetMinor - totalSpentMinor)
        val baseOverrunMinor = max(0L, totalSpentMinor - baseBudgetMinor)

        val remainingBufferMinor = max(0L, bufferMinor - baseOverrunMinor)
        val bufferConsumedMinor = bufferMinor - remainingBufferMinor

        val totalEnvelopeRemainingMinor = remainingBaseMinor + remainingBufferMinor

        val isBaseExceeded = totalSpentMinor > baseBudgetMinor
        val isBufferExhausted = remainingBufferMinor == 0L && isBaseExceeded && totalSpentMinor >= envelopeMinor

        val percentageUsed = if (envelopeMinor > 0L) {
            (totalSpentMinor.toDouble() / envelopeMinor.toDouble()) * 100.0
        } else {
            0.0
        }

        val bufferState = when {
            bufferMinor == 0L -> if (isBaseExceeded) BufferState.EXHAUSTED else BufferState.UNUSED
            !isBaseExceeded -> BufferState.UNUSED
            remainingBufferMinor == 0L -> BufferState.EXHAUSTED
            bufferConsumedMinor >= (bufferMinor * 0.8).toLong() -> BufferState.NEAR_LIMIT
            else -> BufferState.ACTIVE
        }

        val categoryStates = plan.categoryAllocations.map { (cat, allocated) ->
            val spentMinor = categorySpending[cat] ?: 0L
            val spent = Money(spentMinor, currency)
            val remMinor = max(0L, allocated.amountMinor - spentMinor)
            val isExceeded = spentMinor > allocated.amountMinor
            val pct = if (allocated.amountMinor > 0L) {
                (spentMinor.toDouble() / allocated.amountMinor.toDouble()) * 100.0
            } else {
                0.0
            }
            CategoryBudgetState(
                category = cat,
                allocated = allocated,
                spent = spent,
                remaining = Money(remMinor, currency),
                isExceeded = isExceeded,
                percentageUsed = pct
            )
        }

        return BudgetState(
            plan = plan,
            totalSpent = totalSpent,
            remainingBaseBudget = Money(remainingBaseMinor, currency),
            baseBudgetOverrun = Money(baseOverrunMinor, currency),
            remainingBuffer = Money(remainingBufferMinor, currency),
            bufferConsumed = Money(bufferConsumedMinor, currency),
            totalEnvelopeRemaining = Money(totalEnvelopeRemainingMinor, currency),
            isBaseBudgetExceeded = isBaseExceeded,
            isBufferExhausted = isBufferExhausted,
            bufferState = bufferState,
            percentageUsed = percentageUsed,
            categorySpending = categoryMap,
            categoryStates = categoryStates
        )
    }

    fun calculateDailyTarget(
        budgetState: BudgetState,
        remainingDays: Int
    ): Money {
        val currency = budgetState.plan.baseBudget.currency
        if (remainingDays <= 0) {
            return Money.zero(currency)
        }

        val availableMinor = if (budgetState.remainingBaseBudget.isPositive) {
            budgetState.remainingBaseBudget.amountMinor
        } else {
            budgetState.remainingBuffer.amountMinor
        }

        val dailyMinor = availableMinor / remainingDays
        return Money(dailyMinor, currency)
    }

    fun calculateRemainingBuffer(
        plan: BudgetPlan,
        totalSpent: Money
    ): Money {
        val currency = plan.baseBudget.currency
        val baseOverrunMinor = max(0L, totalSpent.amountMinor - plan.baseBudget.amountMinor)
        val remainingBufferMinor = max(0L, plan.emergencyBuffer.amountMinor - baseOverrunMinor)
        return Money(remainingBufferMinor, currency)
    }

    fun calculateSpendingVelocity(
        plan: BudgetPlan,
        totalSpent: Money,
        currentEpochMs: Long = System.currentTimeMillis()
    ): SpendingVelocity {
        val currency = plan.baseBudget.currency
        val diffMs = max(0L, plan.endDateEpochMs - plan.startDateEpochMs)
        val rawDays = (diffMs / MS_PER_DAY).toInt()
        val totalDays = max(1, rawDays)

        val isExpired = currentEpochMs > plan.endDateEpochMs
        val isBeforeStart = currentEpochMs < plan.startDateEpochMs

        val elapsedDays = when {
            isBeforeStart -> 1
            isExpired -> totalDays
            else -> {
                val elapsedMs = max(0L, currentEpochMs - plan.startDateEpochMs)
                max(1, minOf(totalDays, (elapsedMs / MS_PER_DAY).toInt()))
            }
        }

        val remainingDays = when {
            isExpired -> 0
            isBeforeStart -> totalDays
            else -> max(0, totalDays - elapsedDays)
        }

        val plannedDailyMinor = plan.baseBudget.amountMinor / totalDays
        val actualDailyMinor = totalSpent.amountMinor / elapsedDays

        val ratio = if (plannedDailyMinor > 0L) {
            actualDailyMinor.toDouble() / plannedDailyMinor.toDouble()
        } else {
            if (actualDailyMinor > 0L) 2.0 else 1.0
        }

        return SpendingVelocity(
            totalDays = totalDays,
            elapsedDays = elapsedDays,
            remainingDays = remainingDays,
            plannedDailyRate = Money(plannedDailyMinor, currency),
            actualDailyRate = Money(actualDailyMinor, currency),
            velocityRatio = ratio,
            isPacingAhead = actualDailyMinor > plannedDailyMinor
        )
    }

    fun calculateProjection(
        plan: BudgetPlan,
        totalSpent: Money,
        currentEpochMs: Long = System.currentTimeMillis()
    ): BudgetProjection {
        val velocity = calculateSpendingVelocity(plan, totalSpent, currentEpochMs)
        val currency = plan.baseBudget.currency

        val projectedRemainingMinor = velocity.actualDailyRate.amountMinor * velocity.remainingDays
        val projectedTotalMinor = totalSpent.amountMinor + projectedRemainingMinor
        val projectedBaseOverrunMinor = max(0L, projectedTotalMinor - plan.baseBudget.amountMinor)

        val willExceedBase = projectedTotalMinor > plan.baseBudget.amountMinor
        val willExceedEnvelope = projectedTotalMinor > plan.totalEnvelope.amountMinor

        return BudgetProjection(
            projectedTotalSpending = Money(projectedTotalMinor, currency),
            projectedBaseOverrun = Money(projectedBaseOverrunMinor, currency),
            willExceedBaseBudget = willExceedBase,
            willExceedEnvelope = willExceedEnvelope
        )
    }
}
