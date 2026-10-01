package com.fairshare.android.core.domain.settlement

import com.fairshare.android.core.domain.balance.BalanceEngine
import com.fairshare.android.core.domain.balance.MemberBalance
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Adjustment
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import kotlin.math.min

/**
 * Deterministic Settlement Engine.
 * Adapts and evolves the legacy StandardSettlementStrategy.java into a pure,
 * integer-minor-unit calculation that never claims global minimality unless verified.
 */
object SettlementEngine {

    private data class MutableBalance(
        val memberId: String,
        val memberName: String,
        var amountMinor: Long
    )

    /**
     * Calculates the suggested settlement plan given a list of member balances.
     */
    fun calculateSettlement(
        balances: List<MemberBalance>,
        currency: Currency = Currency.INR
    ): SettlementPlan {
        if (balances.isEmpty()) {
            return SettlementPlan(
                transfers = emptyList(),
                totalTransferred = Money.zero(currency),
                isSettled = true,
                isExactMinimum = true,
                algorithmLabel = "Settled"
            )
        }

        val debtors = ArrayList<MutableBalance>()
        val creditors = ArrayList<MutableBalance>()

        for (b in balances) {
            val amt = b.netBalance.amountMinor
            if (amt < 0L) {
                debtors.add(MutableBalance(b.memberId, b.memberName, -amt))
            } else if (amt > 0L) {
                creditors.add(MutableBalance(b.memberId, b.memberName, amt))
            }
        }

        if (debtors.isEmpty() && creditors.isEmpty()) {
            return SettlementPlan(
                transfers = emptyList(),
                totalTransferred = Money.zero(currency),
                isSettled = true,
                isExactMinimum = true,
                algorithmLabel = "Everyone settled"
            )
        }

        // Sort descending by magnitude, with memberId ascending as deterministic tie breaker
        debtors.sortWith(
            compareByDescending<MutableBalance> { it.amountMinor }
                .thenBy { it.memberId }
        )
        creditors.sortWith(
            compareByDescending<MutableBalance> { it.amountMinor }
                .thenBy { it.memberId }
        )

        val transfers = ArrayList<SettlementTransfer>()
        var totalTransferredMinor = 0L

        var dIdx = 0
        var cIdx = 0

        while (dIdx < debtors.size && cIdx < creditors.size) {
            val debtor = debtors[dIdx]
            val creditor = creditors[cIdx]

            val transfer = min(debtor.amountMinor, creditor.amountMinor)
            if (transfer > 0L) {
                transfers.add(
                    SettlementTransfer(
                        fromMemberId = debtor.memberId,
                        fromMemberName = debtor.memberName,
                        toMemberId = creditor.memberId,
                        toMemberName = creditor.memberName,
                        amount = Money(transfer, currency)
                    )
                )
                totalTransferredMinor += transfer
                debtor.amountMinor -= transfer
                creditor.amountMinor -= transfer
            }

            if (debtor.amountMinor == 0L) dIdx++
            if (creditor.amountMinor == 0L) cIdx++
        }

        val isExact = debtors.size <= 1 || creditors.size <= 1

        return SettlementPlan(
            transfers = transfers,
            totalTransferred = Money(totalTransferredMinor, currency),
            isSettled = transfers.isEmpty(),
            isExactMinimum = isExact,
            algorithmLabel = if (isExact) "Optimal settlement" else "Suggested settlement"
        )
    }

    /**
     * Convenience method to calculate settlement directly from group members and transactions.
     */
    fun calculateSettlement(
        members: List<Member>,
        expenses: List<Expense>,
        payments: List<SettlementPayment>,
        adjustments: List<Adjustment> = emptyList(),
        currency: Currency = Currency.INR
    ): SettlementPlan {
        val balances = BalanceEngine.calculateGroupBalances(members, expenses, payments, adjustments, currency)
        return calculateSettlement(balances, currency)
    }

    /**
     * Produces formatted text output preserving the legacy Java Swing StandardSettlementStrategy presentation.
     */
    fun formatLegacySummary(
        tripName: String?,
        totalSpend: Money,
        participantCount: Int,
        plan: SettlementPlan
    ): String {
        val name = if (tripName.isNullOrBlank()) "TRIP" else tripName.uppercase()
        val equalShareMinor = if (participantCount > 0) totalSpend.amountMinor / participantCount else 0L
        val equalShare = Money(equalShareMinor, totalSpend.currency)

        val sb = StringBuilder()
        val line = "=====================================\n"

        sb.append(line)
        sb.append(String.format("             %s\n", name))
        sb.append(line)
        sb.append(String.format("Total Spent: %s\n", totalSpend.formatted()))
        sb.append(String.format("People: %d\n", participantCount))
        sb.append(String.format("Per Person: %s\n", equalShare.formatted()))
        sb.append(line)

        if (plan.isSettled) {
            sb.append("\n🎉 Everyone is already settled!\n")
        } else {
            sb.append("\nWho Pays Whom:\n\n")
            for (t in plan.transfers) {
                sb.append(String.format("• %s pays %s %s\n", t.fromMemberName, t.toMemberName, t.amount.formatted()))
            }
        }

        sb.append("\n")
        sb.append(line)
        return sb.toString()
    }
}
