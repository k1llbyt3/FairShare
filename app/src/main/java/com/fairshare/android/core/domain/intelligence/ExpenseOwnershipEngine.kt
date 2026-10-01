package com.fairshare.android.core.domain.intelligence

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.split.ExpenseSplitEngine

data class MemberOwnership(
    val memberId: String,
    val memberName: String,
    val paidAmount: Money,
    val consumedAmount: Money,
    val netPosition: Money // paid - consumed
)

data class ExpenseOwnershipBreakdown(
    val expenseId: String,
    val description: String,
    val totalAmount: Money,
    val payers: List<Pair<String, Money>>, // MemberName to AmountPaid
    val consumers: List<Pair<String, Money>>, // MemberName to AmountConsumed
    val memberOwnerships: List<MemberOwnership>
)

object ExpenseOwnershipEngine {

    fun computeExpenseOwnership(
        expense: Expense,
        members: List<Member>,
        currency: Currency = Currency.INR
    ): ExpenseOwnershipBreakdown {
        val memberMap = members.associateBy { it.id }

        // 1. Who paid
        val paidMap = mutableMapOf<String, Long>()
        for (payer in expense.payers) {
            paidMap[payer.memberId] = (paidMap[payer.memberId] ?: 0L) + payer.amount.amountMinor
        }

        // 2. Who consumed
        val consumedMap = mutableMapOf<String, Long>()
        val splitMap = ExpenseSplitEngine.calculateSplits(expense)
        for ((memberId, money) in splitMap) {
            consumedMap[memberId] = money.amountMinor
        }

        // All involved member IDs
        val allMemberIds = (paidMap.keys + consumedMap.keys + members.map { it.id }).toSet()
        val ownerships = allMemberIds.map { memberId ->
            val paid = paidMap[memberId] ?: 0L
            val consumed = consumedMap[memberId] ?: 0L
            val net = paid - consumed
            val name = memberMap[memberId]?.name ?: "Member $memberId"
            MemberOwnership(
                memberId = memberId,
                memberName = name,
                paidAmount = Money(paid, currency),
                consumedAmount = Money(consumed, currency),
                netPosition = Money(net, currency)
            )
        }

        val payersList = paidMap.map { (id, minor) ->
            (memberMap[id]?.name ?: id) to Money(minor, currency)
        }

        val consumersList = consumedMap.map { (id, minor) ->
            (memberMap[id]?.name ?: id) to Money(minor, currency)
        }

        return ExpenseOwnershipBreakdown(
            expenseId = expense.id,
            description = expense.description,
            totalAmount = expense.totalAmount,
            payers = payersList,
            consumers = consumersList,
            memberOwnerships = ownerships
        )
    }

    fun computeGroupOwnership(
        expenses: List<Expense>,
        members: List<Member>,
        currency: Currency = Currency.INR
    ): List<MemberOwnership> {
        val memberMap = members.associateBy { it.id }
        val paidTotals = mutableMapOf<String, Long>()
        val consumedTotals = mutableMapOf<String, Long>()

        members.forEach {
            paidTotals[it.id] = 0L
            consumedTotals[it.id] = 0L
        }

        for (exp in expenses.filter { !it.isDeleted }) {
            for (p in exp.payers) {
                paidTotals[p.memberId] = (paidTotals[p.memberId] ?: 0L) + p.amount.amountMinor
            }
            val splitMap = ExpenseSplitEngine.calculateSplits(exp)
            for ((memberId, money) in splitMap) {
                consumedTotals[memberId] = (consumedTotals[memberId] ?: 0L) + money.amountMinor
            }
        }

        return members.map { m ->
            val paid = paidTotals[m.id] ?: 0L
            val consumed = consumedTotals[m.id] ?: 0L
            val net = paid - consumed
            MemberOwnership(
                memberId = m.id,
                memberName = m.name,
                paidAmount = Money(paid, currency),
                consumedAmount = Money(consumed, currency),
                netPosition = Money(net, currency)
            )
        }
    }
}
