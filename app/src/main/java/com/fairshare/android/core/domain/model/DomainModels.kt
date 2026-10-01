package com.fairshare.android.core.domain.model

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.network.backend.model.GroupRole

enum class PaymentMode {
    CASH,
    CARD,
    UPI,
    WALLET,
    ONLINE,
    OTHER
}

enum class ExpenseCategory {
    FOOD,
    TRANSPORT,
    ACCOMMODATION,
    ACTIVITIES,
    SHOPPING,
    FUEL,
    TICKETS,
    UTILITIES,
    OTHER;

    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.titlecase() }
}


enum class SplitMethod {
    EQUAL,
    EXACT,
    PERCENTAGE,
    SHARES,
    ITEMIZED
}

enum class AdjustmentType {
    REFUND,
    REVERSAL,
    MANUAL_ADJUSTMENT
}

data class Member(
    val id: String,
    val name: String,
    val isCurrentUser: Boolean = false
)

data class ExpensePayer(
    val memberId: String,
    val amount: Money
)

data class ExpenseParticipant(
    val memberId: String,
    val exactAmount: Money? = null,
    val percentageBasisPoints: Int? = null, // 10000 basis points = 100.00%
    val shares: Int? = null,
    val excluded: Boolean = false
)

data class ExpenseItem(
    val id: String,
    val name: String,
    val amount: Money,
    val participantMemberIds: List<String>
)

data class Expense(
    val id: String,
    val groupId: String = "",
    val description: String = "",
    val totalAmount: Money,
    val payers: List<ExpensePayer>,
    val participants: List<ExpenseParticipant>,
    val splitMethod: SplitMethod = SplitMethod.EQUAL,
    val items: List<ExpenseItem> = emptyList(),
    val mode: PaymentMode = PaymentMode.ONLINE,
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val receiptAttachmentId: String? = null,
    val isDeleted: Boolean = false
) {
    // Secondary constructor for single-payer / simple expense creation (preserves backward compatibility)
    constructor(
        id: String,
        payerId: String,
        amount: Money,
        involvedMemberIds: List<String>,
        mode: PaymentMode = PaymentMode.ONLINE,
        description: String = "",
        timestamp: Long = System.currentTimeMillis(),
        category: ExpenseCategory = ExpenseCategory.OTHER,
        groupId: String = "",
        notes: String? = null,
        isDeleted: Boolean = false
    ) : this(
        id = id,
        groupId = groupId,
        description = description,
        totalAmount = amount,
        payers = listOf(ExpensePayer(memberId = payerId, amount = amount)),
        participants = involvedMemberIds.map { ExpenseParticipant(memberId = it) },
        splitMethod = SplitMethod.EQUAL,
        items = emptyList(),
        mode = mode,
        category = category,
        timestamp = timestamp,
        notes = notes,
        isDeleted = isDeleted
    )

    // Compatibility properties
    val payerId: String
        get() = payers.firstOrNull()?.memberId ?: ""

    val amount: Money
        get() = totalAmount

    val involvedMemberIds: List<String>
        get() = participants.filter { !it.excluded }.map { it.memberId }
}

data class SettlementPayment(
    val id: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amount: Money,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: PaymentMode = PaymentMode.ONLINE,
    val note: String? = null,
    val groupId: String = "",
    val isDeleted: Boolean = false
)

data class Adjustment(
    val id: String,
    val groupId: String = "",
    val memberId: String,
    val amount: Money,
    val type: AdjustmentType,
    val relatedExpenseId: String? = null,
    val relatedPaymentId: String? = null,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

enum class GroupType {
    TRIP,
    HOUSEHOLD,
    EVENT,
    PERSONAL,
    OTHER
}

data class Group(
    val id: String,
    val name: String,
    val description: String = "",
    val groupType: GroupType = GroupType.TRIP,
    val currency: Currency = Currency("INR", "₹"),
    val createdById: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val timezone: String = "Asia/Kolkata",
    val settlementDeadline: Long? = null,
    val isArchived: Boolean = false,
    val revision: Long = 1L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class GroupMember(
    val groupId: String,
    val userId: String,
    val displayName: String = "",
    val phoneNumber: String = "",
    val role: GroupRole = GroupRole.MEMBER,
    val joinedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

data class GroupInvitation(
    val id: String,
    val groupId: String,
    val invitedByUserId: String,
    val inviteCode: String,
    val inviteType: String = "CODE", // CODE, LINK, QR, PHONE
    val targetPhoneNumber: String? = null,
    val maxUses: Int = 0,
    val usedCount: Int = 0,
    val expiresAt: Long? = null,
    val isRevoked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun isValid(): Boolean {
        if (isRevoked) return false
        if (expiresAt != null && System.currentTimeMillis() > expiresAt) return false
        if (maxUses > 0 && usedCount >= maxUses) return false
        return true
    }
}
