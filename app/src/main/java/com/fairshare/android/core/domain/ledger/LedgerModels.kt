package com.fairshare.android.core.domain.ledger

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money

/**
 * Authoritative financial position of a single member within a group/trip.
 */
data class MemberLedger(
    val memberId: String,
    val paid: Money,
    val consumed: Money,
    val adjustmentNet: Money,
    val netBalance: Money
) {
    val isSettled: Boolean
        get() = netBalance.isZero

    val isCreditor: Boolean
        get() = netBalance.isPositive

    val isDebtor: Boolean
        get() = netBalance.isNegative
}

/**
 * Authoritative ledger for an entire group.
 */
data class GroupLedger(
    val currency: Currency,
    val memberLedgers: Map<String, MemberLedger>,
    val totalExpenseSpend: Money,
    val totalDirectTransfers: Money,
    val totalAdjustments: Money
) {
    /**
     * Ledger invariant check: Sum of all member net balances must be strictly zero.
     */
    val isBalanced: Boolean
        get() = memberLedgers.values.sumOf { it.netBalance.amountMinor } == 0L
}
