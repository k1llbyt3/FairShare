package com.fairshare.android.core.domain.collaboration

data class GroupActivityItem(
    val id: String,
    val groupId: String,
    val actorId: String,
    val actorName: String,
    val eventType: String,
    val summaryText: String,
    val entityType: String? = null,
    val entityId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class GroupMessage(
    val id: String,
    val groupId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val messageType: String = "TEXT", // TEXT, EXPENSE_LINK, SETTLEMENT_LINK, SYSTEM
    val referencedEntityId: String? = null,
    val referencedEntitySummary: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val isFromCurrentUser: Boolean = false,
    val isSynced: Boolean = true
)

data class ExpenseComment(
    val id: String,
    val expenseId: String,
    val groupId: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isDisputeOrClarification: Boolean = false,
    val disputeTag: String? = null
)

object DisputeClarificationTags {
    const val PERSONAL_PAYMENT = "Paid Personally"
    const val EXCLUDE_REQUEST = "Exclude Request"
    const val RECEIPT_ATTACHED = "Receipt Attached"
    const val DUPLICATE_FLAG = "Possible Duplicate"
    const val AMOUNT_DISCREPANCY = "Check Amount"

    val ALL_TAGS = listOf(
        PERSONAL_PAYMENT,
        EXCLUDE_REQUEST,
        RECEIPT_ATTACHED,
        DUPLICATE_FLAG,
        AMOUNT_DISCREPANCY
    )
}
