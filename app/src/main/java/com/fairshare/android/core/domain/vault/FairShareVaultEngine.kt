package com.fairshare.android.core.domain.vault

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.split.ExpenseSplitEngine

object FairShareVaultEngine {

    fun calculateVaultState(
        groupId: String,
        fundName: String = "Trip Fund",
        members: List<Member>,
        contributions: List<VaultContribution>,
        poolExpenses: List<Expense>,
        currency: Currency = Currency.INR
    ): VaultLedgerState {
        val memberMap = members.associateBy { it.id }

        // Total contributions
        val contributionByMember = mutableMapOf<String, Long>()
        members.forEach { contributionByMember[it.id] = 0L }
        for (c in contributions) {
            contributionByMember[c.memberId] = (contributionByMember[c.memberId] ?: 0L) + c.amount.amountMinor
        }
        val totalContributedMinor = contributionByMember.values.sum()

        // Total pool expenses and spent per member
        val spentByMember = mutableMapOf<String, Long>()
        members.forEach { spentByMember[it.id] = 0L }
        var totalSpentMinor = 0L

        for (exp in poolExpenses.filter { !it.isDeleted }) {
            totalSpentMinor += exp.totalAmount.amountMinor
            val splitMap = ExpenseSplitEngine.calculateSplits(exp)
            for ((memberId, money) in splitMap) {
                spentByMember[memberId] = (spentByMember[memberId] ?: 0L) + money.amountMinor
            }
        }

        val remainingPoolMinor = kotlin.math.max(0L, totalContributedMinor - totalSpentMinor)

        val memberShares = members.map { m ->
            val contrib = contributionByMember[m.id] ?: 0L
            val spent = spentByMember[m.id] ?: 0L
            val remaining = contrib - spent
            MemberVaultShare(
                memberId = m.id,
                memberName = m.name,
                totalContributed = Money(contrib, currency),
                allocatedSpent = Money(spent, currency),
                remainingShare = Money(remaining, currency)
            )
        }

        return VaultLedgerState(
            groupId = groupId,
            fundName = fundName,
            totalContributed = Money(totalContributedMinor, currency),
            totalSpentFromPool = Money(totalSpentMinor, currency),
            remainingPoolBalance = Money(remainingPoolMinor, currency),
            memberShares = memberShares,
            contributions = contributions,
            currency = currency
        )
    }
}
