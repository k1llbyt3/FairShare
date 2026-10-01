package com.fairshare.android.core.network.backend.model

import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SplitMethod

/**
 * Canonical Cloud/Backend Models for FairShare Multi-Device Architecture.
 * Required by specs/Plan.md Phase 4 and specs/PRD.md Section 71.
 *
 * All authoritative monetary values strictly use integer minor units (e.g. paise).
 */

data class BackendUser(
    val id: String,
    val phoneNumber: String, // E.164 normalized
    val displayName: String,
    val profileImageUrl: String? = null,
    val defaultCurrency: String = "INR",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class GroupRole {
    OWNER,
    ADMIN,
    MEMBER;

    fun canManageGroup(): Boolean = this == OWNER || this == ADMIN
    fun canManageInvitations(): Boolean = this == OWNER || this == ADMIN
    fun canRemoveMember(targetRole: GroupRole, isSelf: Boolean): Boolean {
        if (isSelf) return true
        return when (this) {
            OWNER -> true
            ADMIN -> targetRole == MEMBER
            MEMBER -> false
        }
    }
}

data class BackendGroup(
    val id: String,
    val name: String,
    val description: String = "",
    val groupType: String = "TRIP", // TRIP, HOUSEHOLD, EVENT, OTHER
    val currency: String = "INR",
    val createdById: String,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val timezone: String = "Asia/Kolkata",
    val settlementDeadline: Long? = null,
    val isArchived: Boolean = false,
    val revision: Long = 1L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class BackendGroupMember(
    val groupId: String,
    val userId: String,
    val role: GroupRole = GroupRole.MEMBER,
    val joinedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

data class BackendGroupMemberWithUser(
    val member: BackendGroupMember,
    val user: BackendUser
)

data class BackendInvitation(
    val id: String,
    val groupId: String,
    val invitedByUserId: String,
    val inviteCode: String,
    val inviteType: String = "CODE", // CODE, LINK, QR, PHONE
    val targetPhoneNumber: String? = null,
    val maxUses: Int = 0, // 0 = unlimited
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

data class BackendExpensePayer(
    val memberId: String,
    val amountMinor: Long,
    val currency: String = "INR"
)

data class BackendExpenseParticipant(
    val memberId: String,
    val shareMinor: Long,
    val exactAmountMinor: Long? = null,
    val percentageBasisPoints: Int? = null,
    val shares: Int? = null,
    val excluded: Boolean = false
)

data class BackendExpenseItem(
    val id: String,
    val expenseId: String,
    val name: String,
    val amountMinor: Long,
    val participantIds: List<String> = emptyList()
)

data class BackendExpense(
    val id: String,
    val groupId: String,
    val createdById: String,
    val payerId: String,
    val amountMinor: Long, // Integer minor units (e.g. paise)
    val currency: String = "INR",
    val description: String,
    val merchant: String? = null,
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val paymentMode: PaymentMode = PaymentMode.ONLINE,
    val splitMethod: SplitMethod = SplitMethod.EQUAL,
    val notes: String? = null,
    val receiptAttachmentId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val revision: Long = 1L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val payers: List<BackendExpensePayer> = emptyList(),
    val participants: List<BackendExpenseParticipant> = emptyList(),
    val items: List<BackendExpenseItem> = emptyList()
)

data class BackendSettlementPayment(
    val id: String,
    val groupId: String,
    val fromUserId: String,
    val toUserId: String,
    val amountMinor: Long, // Integer minor units
    val currency: String = "INR",
    val paymentMode: PaymentMode = PaymentMode.ONLINE,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,
    val proofAttachmentId: String? = null,
    val createdByUserId: String,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val revision: Long = 1L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class BackendBudgetCategory(
    val id: String,
    val budgetId: String,
    val category: String,
    val allocatedAmountMinor: Long
)

data class BackendBudget(
    val id: String,
    val groupId: String,
    val baseAmountMinor: Long,
    val bufferAmountMinor: Long,
    val currency: String = "INR",
    val startDate: Long,
    val endDate: Long,
    val timezone: String = "UTC",
    val enabled: Boolean = true,
    val categories: List<BackendBudgetCategory> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class BackendActivityEvent(
    val id: String,
    val groupId: String,
    val actorId: String,
    val eventType: String,
    val summaryText: String,
    val entityType: String? = null,
    val entityId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class BackendAuditEvent(
    val id: String,
    val recordId: String,
    val actorId: String,
    val operation: String,
    val previousRevision: Long,
    val newRevision: Long,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Incremental change payload for synchronization (Phase 5).
 * Contains delta updates since a specified cursor timestamp.
 */
data class BackendGroupDelta(
    val groupId: String,
    val cursorTimestamp: Long,
    val expenses: List<BackendExpense> = emptyList(),
    val payments: List<BackendSettlementPayment> = emptyList(),
    val group: BackendGroup? = null,
    val members: List<BackendGroupMemberWithUser> = emptyList()
)

data class BackendMessage(
    val id: String,
    val groupId: String,
    val senderId: String,
    val content: String,
    val messageType: String = "TEXT", // TEXT, EXPENSE_LINK, SETTLEMENT_LINK, SYSTEM
    val referencedEntityId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

data class BackendAttachment(
    val id: String,
    val groupId: String,
    val expenseId: String? = null,
    val localUri: String,
    val remoteUrl: String? = null,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis()
)


