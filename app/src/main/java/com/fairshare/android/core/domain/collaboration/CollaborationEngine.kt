package com.fairshare.android.core.domain.collaboration

import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment

object CollaborationEngine {

    fun formatActivitySummary(
        eventType: String,
        actorName: String,
        entityDescription: String,
        amountFormatted: String? = null
    ): String {
        return when (eventType) {
            "EXPENSE_CREATED" -> if (amountFormatted != null) "$actorName added \"$entityDescription\" ($amountFormatted)" else "$actorName added \"$entityDescription\""
            "EXPENSE_UPDATED" -> "$actorName updated \"$entityDescription\""
            "EXPENSE_DELETED" -> "$actorName removed \"$entityDescription\""
            "PAYMENT_RECORDED" -> if (amountFormatted != null) "$actorName settled $amountFormatted" else "$actorName recorded a payment"
            "BUDGET_SAVED" -> "$actorName updated the budget"
            "MEMBER_JOINED" -> "$actorName joined the group"
            "ATTACHMENT_ADDED" -> "$actorName added receipt evidence for $entityDescription"
            "COMMENT_ADDED" -> "$actorName commented on $entityDescription"
            else -> "$actorName: $entityDescription"
        }
    }

    fun buildExpenseReferenceSummary(expense: Expense): String {
        val payerNames = expense.payers.joinToString(", ") { it.memberId }
        return "${expense.description} • ${expense.totalAmount.formatted()}"
    }

    fun detectDisputeTag(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("personally") || lower.contains("my personal") -> DisputeClarificationTags.PERSONAL_PAYMENT
            lower.contains("exclude") || lower.contains("wasn't there") || lower.contains("didn't eat") || lower.contains("not for me") -> DisputeClarificationTags.EXCLUDE_REQUEST
            lower.contains("receipt") || lower.contains("bill attached") || lower.contains("proof") -> DisputeClarificationTags.RECEIPT_ATTACHED
            lower.contains("duplicate") || lower.contains("twice") || lower.contains("already added") -> DisputeClarificationTags.DUPLICATE_FLAG
            lower.contains("wrong amount") || lower.contains("mismatch") || lower.contains("check amount") -> DisputeClarificationTags.AMOUNT_DISCREPANCY
            else -> null
        }
    }
}
