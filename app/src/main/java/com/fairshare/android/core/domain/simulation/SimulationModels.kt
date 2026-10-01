package com.fairshare.android.core.domain.simulation

import com.fairshare.android.core.domain.balance.MemberBalance
import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.budget.BudgetState
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.settlement.SettlementPlan

/**
 * Result of a financial balance simulation.
 * Pure hypothetical outcome — guaranteed not to mutate authoritative state.
 */
data class SimulationResult(
    val previousBalances: List<MemberBalance>,
    val simulatedBalances: List<MemberBalance>,
    val previousPlan: SettlementPlan,
    val simulatedPlan: SettlementPlan,
    val balanceDeltas: Map<String, Money>,
    val transferCountDelta: Int
)

data class BudgetSimulationResult(
    val previousBudgetState: BudgetState?,
    val simulatedBudgetState: BudgetState?,
    val previousRemainingBase: Money?,
    val simulatedRemainingBase: Money?,
    val previousRemainingBuffer: Money?,
    val simulatedRemainingBuffer: Money?,
    val previousDailyRunway: Money?,
    val simulatedDailyRunway: Money?,
    val bufferExceeded: Boolean
)

data class WhatIfSimulationOutcome(
    val balanceResult: SimulationResult,
    val budgetResult: BudgetSimulationResult?
)
