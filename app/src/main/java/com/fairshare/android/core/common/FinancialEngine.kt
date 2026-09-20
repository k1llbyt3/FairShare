package com.fairshare.android.core.common

import com.fairshare.android.core.model.Expense
import com.fairshare.android.core.model.FinancialSummary
import com.fairshare.android.core.model.Member
import com.fairshare.android.core.model.MemberBalance
import com.fairshare.android.core.model.Money
import com.fairshare.android.core.model.Payment
import com.fairshare.android.core.model.SettlementTransfer
import kotlin.math.min

/**
 * Deterministic Financial Engine.
 * Adapted directly from legacy StandardSettlementStrategy.java while eliminating
 * floating-point inaccuracies through integer minor-unit (paise) arithmetic.
 */
object FinancialEngine {

    fun calculateSummary(
        members: List<Member>,
        expenses: List<Expense>,
        payments: List<Payment>
    ): FinancialSummary {
        if (members.isEmpty()) {
            return FinancialSummary(Money.ZERO, Money.ZERO, emptyList(), emptyList())
        }

        val memberMap = members.associateBy { it.id }
        val balancePaise = members.associate { it.id to 0L }.toMutableMap()

        var totalSpendPaise = 0L

        // 1. Process expenses
        for (expense in expenses) {
            val amount = expense.amount.paise
            totalSpendPaise += amount

            // Payer gets credited with the full amount they paid
            balancePaise[expense.payerId] = (balancePaise[expense.payerId] ?: 0L) + amount

            val involved = expense.involvedMemberIds.ifEmpty { members.map { it.id } }
            if (involved.isNotEmpty()) {
                val baseShare = amount / involved.size
                var remainder = amount % involved.size

                for (id in involved) {
                    val share = baseShare + (if (remainder > 0) 1 else 0)
                    if (remainder > 0) remainder--
                    balancePaise[id] = (balancePaise[id] ?: 0L) - share
                }
            }
        }

        // 2. Process payments (From pays To -> From balance increases / credited, To gets debited)
        for (payment in payments) {
            val amount = payment.amount.paise
            balancePaise[payment.fromMemberId] = (balancePaise[payment.fromMemberId] ?: 0L) + amount
            balancePaise[payment.toMemberId] = (balancePaise[payment.toMemberId] ?: 0L) - amount
        }

        // 3. Build Balances list
        val balances = members.map { member ->
            MemberBalance(
                memberId = member.id,
                memberName = member.name,
                netBalance = Money(balancePaise[member.id] ?: 0L)
            )
        }

        // 4. Calculate Settlements (Who Pays Whom) using legacy greedy min/max settlement algorithm
        data class TempBalance(val id: String, val name: String, var amount: Long)

        val debtors = mutableListOf<TempBalance>()
        val creditors = mutableListOf<TempBalance>()

        for (b in balances) {
            val amt = b.netBalance.paise
            if (amt < 0) {
                debtors.add(TempBalance(b.memberId, b.memberName, -amt))
            } else if (amt > 0) {
                creditors.add(TempBalance(b.memberId, b.memberName, amt))
            }
        }

        debtors.sortByDescending { it.amount }
        creditors.sortByDescending { it.amount }

        val settlements = mutableListOf<SettlementTransfer>()
        var dIdx = 0
        var cIdx = 0

        while (dIdx < debtors.size && cIdx < creditors.size) {
            val debtor = debtors[dIdx]
            val creditor = creditors[cIdx]

            val transfer = min(debtor.amount, creditor.amount)
            if (transfer > 0) {
                settlements.add(
                    SettlementTransfer(
                        fromMemberId = debtor.id,
                        fromMemberName = debtor.name,
                        toMemberId = creditor.id,
                        toMemberName = creditor.name,
                        amount = Money(transfer)
                    )
                )
                debtor.amount -= transfer
                creditor.amount -= transfer
            }

            if (debtor.amount == 0L) dIdx++
            if (creditor.amount == 0L) cIdx++
        }

        val perPersonSharePaise = if (members.isEmpty()) 0L else totalSpendPaise / members.size

        return FinancialSummary(
            totalSpend = Money(totalSpendPaise),
            perPersonShare = Money(perPersonSharePaise),
            balances = balances,
            settlements = settlements
        )
    }
}
