package com.fairshare.android.core.domain.balance

import com.fairshare.android.core.domain.currency.Money

/**
 * Domain representation of a member's net and gross balance position.
 */
data class MemberBalance(
    val memberId: String,
    val memberName: String,
    val netBalance: Money,
    val paid: Money = Money.ZERO,
    val consumed: Money = Money.ZERO
) {
    val isOwed: Boolean
        get() = netBalance.isPositive

    val owes: Boolean
        get() = netBalance.isNegative

    val isSettled: Boolean
        get() = netBalance.isZero
}
