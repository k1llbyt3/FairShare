package com.fairshare.android.core.domain.settlement

import com.fairshare.android.core.domain.currency.Money

/**
 * A directed financial transfer to settle debts.
 */
data class SettlementTransfer(
    val fromMemberId: String,
    val fromMemberName: String,
    val toMemberId: String,
    val toMemberName: String,
    val amount: Money
)

/**
 * Outcome of the settlement calculation.
 */
data class SettlementPlan(
    val transfers: List<SettlementTransfer>,
    val totalTransferred: Money,
    val isSettled: Boolean,
    val isExactMinimum: Boolean = false,
    val algorithmLabel: String = "Suggested settlement"
)
