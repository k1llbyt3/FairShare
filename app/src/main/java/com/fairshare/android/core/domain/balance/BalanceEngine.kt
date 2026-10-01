package com.fairshare.android.core.domain.balance

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.ledger.GroupLedger
import com.fairshare.android.core.domain.ledger.LedgerEngine
import com.fairshare.android.core.domain.model.Adjustment
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment

/**
 * Balance Engine for calculating individual and group balances.
 */
object BalanceEngine {

    fun calculateMemberBalance(
        memberId: String,
        memberName: String,
        ledger: GroupLedger
    ): MemberBalance {
        val entry = ledger.memberLedgers[memberId]
        return if (entry != null) {
            MemberBalance(
                memberId = memberId,
                memberName = memberName,
                netBalance = entry.netBalance,
                paid = entry.paid,
                consumed = entry.consumed
            )
        } else {
            MemberBalance(
                memberId = memberId,
                memberName = memberName,
                netBalance = Money.zero(ledger.currency),
                paid = Money.zero(ledger.currency),
                consumed = Money.zero(ledger.currency)
            )
        }
    }

    fun calculateGroupBalances(
        members: List<Member>,
        expenses: List<Expense>,
        payments: List<SettlementPayment>,
        adjustments: List<Adjustment> = emptyList(),
        currency: Currency = Currency.INR
    ): List<MemberBalance> {
        if (members.isEmpty()) return emptyList()

        val ledger = LedgerEngine.calculateLedger(members, expenses, payments, adjustments, currency)
        return members.map { member ->
            calculateMemberBalance(member.id, member.name, ledger)
        }
    }
}
