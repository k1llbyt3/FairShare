package com.fairshare.android.core.common

import com.fairshare.android.core.domain.balance.BalanceEngine
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.settlement.SettlementEngine
import com.fairshare.android.core.model.FinancialSummary

/**
 * Common FinancialEngine facade adapter.
 * Preserves existing Phase 0/1 UI entry points while routing calculations
 * to the authoritative deterministic domain engine.
 */
object FinancialEngine {

    fun calculateSummary(
        members: List<Member>,
        expenses: List<Expense>,
        payments: List<SettlementPayment>
    ): FinancialSummary {
        if (members.isEmpty()) {
            return FinancialSummary(Money.ZERO, Money.ZERO, emptyList(), emptyList())
        }

        val balances = BalanceEngine.calculateGroupBalances(members, expenses, payments)
        val settlementPlan = SettlementEngine.calculateSettlement(balances)

        val totalSpendPaise = expenses.filter { !it.isDeleted }.sumOf { it.totalAmount.amountMinor }
        val perPersonPaise = if (members.isNotEmpty()) totalSpendPaise / members.size else 0L

        return FinancialSummary(
            totalSpend = Money(totalSpendPaise),
            perPersonShare = Money(perPersonPaise),
            balances = balances,
            settlements = settlementPlan.transfers
        )
    }
}
