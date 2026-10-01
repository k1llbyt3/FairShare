package com.fairshare.android.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["phoneNumber"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey
    val id: String,
    val phoneNumber: String,
    val displayName: String,
    val profileImageUrl: String? = null,
    val defaultCurrency: String = "INR",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "groups",
    indices = [
        Index(value = ["createdById"])
    ]
)
data class GroupEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val groupType: String = "TRIP", // TRIP, HOUSEHOLD, EVENT, OTHER
    val currency: String = "INR",
    val createdById: String,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val settlementDeadline: Long? = null,
    val isArchived: Boolean = false,
    val revision: Long = 1L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["userId"])
    ]
)
data class GroupMemberEntity(
    val groupId: String,
    val userId: String,
    val role: String = "MEMBER", // OWNER, ADMIN, MEMBER
    val joinedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["payerId"]),
        Index(value = ["timestamp"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val createdById: String,
    val payerId: String,
    val amountMinor: Long,
    val currency: String = "INR",
    val description: String,
    val merchant: String? = null,
    val category: String = "OTHER",
    val paymentMode: String = "ONLINE",
    val splitMethod: String = "EQUAL",
    val notes: String? = null,
    val receiptAttachmentId: String? = null,
    val timestamp: Long,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val revision: Long = 1L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "expense_payers",
    primaryKeys = ["expenseId", "memberId"],
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["expenseId"]),
        Index(value = ["memberId"])
    ]
)
data class ExpensePayerEntity(
    val expenseId: String,
    val memberId: String,
    val amountMinor: Long,
    val currency: String = "INR"
)

@Entity(
    tableName = "expense_participants",
    primaryKeys = ["expenseId", "memberId"],
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["expenseId"]),
        Index(value = ["memberId"])
    ]
)
data class ExpenseParticipantEntity(
    val expenseId: String,
    val memberId: String,
    val shareMinor: Long,
    val exactAmountMinor: Long? = null,
    val percentageBasisPoints: Int? = null,
    val shares: Int? = null,
    val excluded: Boolean = false
)

@Entity(
    tableName = "expense_items",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["expenseId"])
    ]
)
data class ExpenseItemEntity(
    @PrimaryKey
    val id: String,
    val expenseId: String,
    val name: String,
    val amountMinor: Long,
    val participantIdsCsv: String = "" // comma-separated participant IDs
)

@Entity(
    tableName = "settlement_payments",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["fromMemberId"]),
        Index(value = ["toMemberId"]),
        Index(value = ["timestamp"])
    ]
)
data class SettlementPaymentEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinor: Long,
    val currency: String = "INR",
    val paymentMode: String = "ONLINE",
    val timestamp: Long,
    val note: String? = null,
    val proofAttachmentId: String? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val revision: Long = 1L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["groupId"])
    ]
)
data class BudgetEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val baseAmountMinor: Long,
    val bufferAmountMinor: Long,
    val currency: String = "INR",
    val startDate: Long,
    val endDate: Long,
    val timezone: String = "UTC",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "budget_categories",
    foreignKeys = [
        ForeignKey(
            entity = BudgetEntity::class,
            parentColumns = ["id"],
            childColumns = ["budgetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["budgetId"])
    ]
)
data class BudgetCategoryEntity(
    @PrimaryKey
    val id: String,
    val budgetId: String,
    val category: String,
    val allocatedAmountMinor: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["senderId"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val senderId: String,
    val content: String,
    val messageType: String = "TEXT", // TEXT, EXPENSE_LINK, SETTLEMENT_LINK, SYSTEM
    val referencedEntityId: String? = null,
    val timestamp: Long,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "activity_events",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["timestamp"])
    ]
)
data class ActivityEventEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val actorId: String,
    val eventType: String, // GROUP_CREATED, EXPENSE_CREATED, PAYMENT_CREATED, etc.
    val summaryText: String,
    val entityType: String? = null,
    val entityId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transaction_candidates",
    indices = [
        Index(value = ["status"]),
        Index(value = ["detectedTimestamp"])
    ]
)
data class TransactionCandidateEntity(
    @PrimaryKey
    val id: String,
    val groupId: String? = null,
    val rawSource: String, // SMS, NOTIFICATION, OCR, BANK_STATEMENT
    val rawText: String,
    val detectedAmountMinor: Long,
    val currency: String = "INR",
    val detectedMerchant: String? = null,
    val detectedTimestamp: Long,
    val status: String = "PENDING", // PENDING, CONFIRMED, DISCARDED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attachments",
    indices = [
        Index(value = ["groupId"])
    ]
)
data class AttachmentEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val localUri: String,
    val remoteUrl: String? = null,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_operations",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class SyncOperationEntity(
    @PrimaryKey
    val id: String,
    val entityType: String, // EXPENSE, PAYMENT, GROUP, etc.
    val entityId: String,
    val operationType: String, // INSERT, UPDATE, DELETE
    val payloadJson: String,
    val status: String = "PENDING", // PENDING, IN_FLIGHT, SUCCESS, FAILED
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_metadata"
)
data class SyncMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "meta_key")
    val key: String,
    @ColumnInfo(name = "meta_value")
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)
