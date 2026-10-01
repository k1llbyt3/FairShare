package com.fairshare.android.core.domain.simulation

import com.fairshare.android.core.domain.balance.BalanceEngine
import com.fairshare.android.core.domain.budget.BudgetEngine
import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Adjustment
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.settlement.SettlementEngine

/**
 * Deterministic Simulation Engine.
 * Simulates hypothetical financial transactions without mutating persistent or authoritative group state.
 */
object SimulationEngine {

    fun simulateExpense(
        members: List<Member>,
        currentExpenses: List<Expense>,
        currentPayments: List<SettlementPayment>,
        currentAdjustments: List<Adjustment> = emptyList(),
        candidateExpense: Expense,
        currency: Currency = Currency.INR
    ): SimulationResult {
        // Base state
        val prevBalances = BalanceEngine.calculateGroupBalances(
            members, currentExpenses, currentPayments, currentAdjustments, currency
        )
        val prevPlan = SettlementEngine.calculateSettlement(prevBalances, currency)

        // Simulated state (creates a new list, leaving currentExpenses untouched)
        val simulatedExpenses = currentExpenses + candidateExpense
        val simBalances = BalanceEngine.calculateGroupBalances(
            members, simulatedExpenses, currentPayments, currentAdjustments, currency
        )
        val simPlan = SettlementEngine.calculateSettlement(simBalances, currency)

        val prevMap = prevBalances.associate { it.memberId to it.netBalance }
        val deltas = HashMap<String, Money>()
        for (sb in simBalances) {
            val oldNet = prevMap[sb.memberId] ?: Money.zero(currency)
            deltas[sb.memberId] = sb.netBalance - oldNet
        }

        return SimulationResult(
            previousBalances = prevBalances,
            simulatedBalances = simBalances,
            previousPlan = prevPlan,
            simulatedPlan = simPlan,
            balanceDeltas = deltas,
            transferCountDelta = simPlan.transfers.size - prevPlan.transfers.size
        )
    }

    fun simulateWhatIfExpense(
        members: List<Member>,
        currentExpenses: List<Expense>,
        currentPayments: List<SettlementPayment>,
        currentAdjustments: List<Adjustment> = emptyList(),
        candidateExpense: Expense,
        budgetPlan: BudgetPlan? = null,
        currency: Currency = Currency.INR
    ): WhatIfSimulationOutcome {
        val balanceResult = simulateExpense(
            members = members,
            currentExpenses = currentExpenses,
            currentPayments = currentPayments,
            currentAdjustments = currentAdjustments,
            candidateExpense = candidateExpense,
            currency = currency
        )

        val budgetResult = if (budgetPlan != null) {
            val prevBudgetState = BudgetEngine.calculateBudgetState(budgetPlan, currentExpenses)
            val simBudgetState = BudgetEngine.calculateBudgetState(budgetPlan, currentExpenses + candidateExpense)

            val daysRemaining = kotlin.math.max(1, ((budgetPlan.endDateEpochMs - System.currentTimeMillis()) / 86400000L).toInt())
            val prevRunway = BudgetEngine.calculateDailyTarget(prevBudgetState, daysRemaining)
            val simRunway = BudgetEngine.calculateDailyTarget(simBudgetState, daysRemaining)

            BudgetSimulationResult(
                previousBudgetState = prevBudgetState,
                simulatedBudgetState = simBudgetState,
                previousRemainingBase = prevBudgetState.remainingBaseBudget,
                simulatedRemainingBase = simBudgetState.remainingBaseBudget,
                previousRemainingBuffer = prevBudgetState.remainingBuffer,
                simulatedRemainingBuffer = simBudgetState.remainingBuffer,
                previousDailyRunway = prevRunway,
                simulatedDailyRunway = simRunway,
                bufferExceeded = simBudgetState.isBufferExhausted
            )
        } else null

        return WhatIfSimulationOutcome(
            balanceResult = balanceResult,
            budgetResult = budgetResult
        )
    }

    fun simulatePayment(
        members: List<Member>,
        currentExpenses: List<Expense>,
        currentPayments: List<SettlementPayment>,
        currentAdjustments: List<Adjustment> = emptyList(),
        candidatePayment: SettlementPayment,
        currency: Currency = Currency.INR
    ): SimulationResult {
        // Base state
        val prevBalances = BalanceEngine.calculateGroupBalances(
            members, currentExpenses, currentPayments, currentAdjustments, currency
        )
        val prevPlan = SettlementEngine.calculateSettlement(prevBalances, currency)

        // Simulated state
        val simulatedPayments = currentPayments + candidatePayment
        val simBalances = BalanceEngine.calculateGroupBalances(
            members, currentExpenses, simulatedPayments, currentAdjustments, currency
        )
        val simPlan = SettlementEngine.calculateSettlement(simBalances, currency)

        val prevMap = prevBalances.associate { it.memberId to it.netBalance }
        val deltas = HashMap<String, Money>()
        for (sb in simBalances) {
            val oldNet = prevMap[sb.memberId] ?: Money.zero(currency)
            deltas[sb.memberId] = sb.netBalance - oldNet
        }

        return SimulationResult(
            previousBalances = prevBalances,
            simulatedBalances = simBalances,
            previousPlan = prevPlan,
            simulatedPlan = simPlan,
            balanceDeltas = deltas,
            transferCountDelta = simPlan.transfers.size - prevPlan.transfers.size
        )
    }
}
