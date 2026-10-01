package com.fairshare.android.core.domain.export

import com.fairshare.android.core.domain.balance.MemberBalance
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.split.ExpenseSplitEngine

object ShareEngine {

    fun formatExpenseShare(expense: Expense, members: List<Member>): String {
        val memberMap = members.associateBy { it.id }
        val payerNames = expense.payers.mapNotNull { memberMap[it.memberId]?.name }.joinToString(", ")
        val splits = ExpenseSplitEngine.calculateSplits(expense)

        val sb = StringBuilder()
        sb.appendLine("FairShare Expense Details:")
        sb.appendLine("• ${expense.description}")
        sb.appendLine("• Amount: ${expense.totalAmount.formatted()}")
        sb.appendLine("• Paid by: $payerNames")
        sb.appendLine("• Category: ${expense.category.displayName}")
        sb.appendLine()
        sb.appendLine("Split breakdown:")
        for ((memberId, share) in splits) {
            val name = memberMap[memberId]?.name ?: memberId
            sb.appendLine("- $name: ${share.formatted()}")
        }
        sb.appendLine()
        sb.appendLine("Shared via FairShare")
        return sb.toString()
    }

    fun formatSettlementShare(payment: SettlementPayment, members: List<Member>): String {
        val memberMap = members.associateBy { it.id }
        val fromName = memberMap[payment.fromMemberId]?.name ?: "Member"
        val toName = memberMap[payment.toMemberId]?.name ?: "Member"

        val sb = StringBuilder()
        sb.appendLine("FairShare Settlement Confirmation:")
        sb.appendLine("• $fromName paid $toName")
        sb.appendLine("• Amount: ${payment.amount.formatted()}")
        sb.appendLine("• Mode: ${payment.mode.name}")
        if (!payment.note.isNullOrBlank()) {
            sb.appendLine("• Note: ${payment.note}")
        }
        sb.appendLine()
        sb.appendLine("Recorded cleanly on FairShare")
        return sb.toString()
    }

    fun formatGroupBalancesShare(groupName: String, balances: List<MemberBalance>, members: List<Member>): String {
        val memberMap = members.associateBy { it.id }
        val sb = StringBuilder()
        sb.appendLine("FairShare Balances for \"$groupName\":")
        sb.appendLine()
        for (b in balances) {
            val name = memberMap[b.memberId]?.name ?: b.memberId
            val status = when {
                b.netBalance.amountMinor > 0 -> "Gets back +${b.netBalance.formatted()}"
                b.netBalance.amountMinor < 0 -> "Owes ${b.netBalance.formatted()}"
                else -> "Settled up (₹0)"
            }
            sb.appendLine("• $name: $status")
        }
        sb.appendLine()
        sb.appendLine("Shared via FairShare")
        return sb.toString()
    }
}
