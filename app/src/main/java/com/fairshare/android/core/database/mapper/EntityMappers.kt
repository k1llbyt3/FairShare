package com.fairshare.android.core.database.mapper

import com.fairshare.android.core.database.entity.BudgetCategoryEntity
import com.fairshare.android.core.database.entity.BudgetEntity
import com.fairshare.android.core.database.entity.ExpenseEntity
import com.fairshare.android.core.database.entity.ExpenseItemEntity
import com.fairshare.android.core.database.entity.ExpenseParticipantEntity
import com.fairshare.android.core.database.entity.ExpensePayerEntity
import com.fairshare.android.core.database.entity.SettlementPaymentEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.database.relation.BudgetWithCategories
import com.fairshare.android.core.database.relation.ExpenseWithDetails
import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.ExpenseItem
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.network.backend.model.BackendExpense
import com.fairshare.android.core.network.backend.model.BackendExpenseItem
import com.fairshare.android.core.network.backend.model.BackendExpenseParticipant
import com.fairshare.android.core.network.backend.model.BackendExpensePayer
import com.fairshare.android.core.network.backend.model.BackendSettlementPayment

object EntityMappers {

    fun UserEntity.toDomain(isCurrentUser: Boolean = false): Member {
        return Member(
            id = id,
            name = displayName,
            isCurrentUser = isCurrentUser
        )
    }

    fun Member.toEntity(phoneNumber: String = "", defaultCurrency: String = "INR"): UserEntity {
        return UserEntity(
            id = id,
            phoneNumber = phoneNumber.ifBlank { "user_${id}" },
            displayName = name,
            defaultCurrency = defaultCurrency
        )
    }

    fun ExpenseWithDetails.toDomain(): Expense {
        val curr = Currency(expense.currency, if (expense.currency == "INR") "₹" else "$")
        val totalMoney = Money(expense.amountMinor, curr)

        val domainPayers = if (payers.isNotEmpty()) {
            payers.map {
                ExpensePayer(
                    memberId = it.memberId,
                    amount = Money(it.amountMinor, curr)
                )
            }
        } else {
            listOf(ExpensePayer(memberId = expense.payerId, amount = totalMoney))
        }

        val domainParticipants = participants.map {
            ExpenseParticipant(
                memberId = it.memberId,
                exactAmount = it.exactAmountMinor?.let { minor -> Money(minor, curr) },
                percentageBasisPoints = it.percentageBasisPoints,
                shares = it.shares,
                excluded = it.excluded
            )
        }

        val domainItems = items.map {
            ExpenseItem(
                id = it.id,
                name = it.name,
                amount = Money(it.amountMinor, curr),
                participantMemberIds = it.participantIdsCsv.split(",").filter { id -> id.isNotBlank() }
            )
        }

        val splitMode = try {
            SplitMethod.valueOf(expense.splitMethod)
        } catch (_: Exception) {
            SplitMethod.EQUAL
        }

        val paymentMode = try {
            PaymentMode.valueOf(expense.paymentMode)
        } catch (_: Exception) {
            PaymentMode.ONLINE
        }

        val category = try {
            ExpenseCategory.valueOf(expense.category)
        } catch (_: Exception) {
            ExpenseCategory.OTHER
        }

        return Expense(
            id = expense.id,
            groupId = expense.groupId,
            description = expense.description,
            totalAmount = totalMoney,
            payers = domainPayers,
            participants = domainParticipants,
            splitMethod = splitMode,
            items = domainItems,
            mode = paymentMode,
            category = category,
            timestamp = expense.timestamp,
            notes = expense.notes,
            receiptAttachmentId = expense.receiptAttachmentId,
            isDeleted = expense.isDeleted
        )
    }

    fun Expense.toEntity(groupId: String = "", createdById: String = ""): Pair<ExpenseEntity, Triple<List<ExpensePayerEntity>, List<ExpenseParticipantEntity>, List<ExpenseItemEntity>>> {
        val targetGroupId = groupId.ifBlank { this.groupId }
        val targetCreatedBy = createdById.ifBlank { this.payers.firstOrNull()?.memberId ?: "" }

        val expenseEntity = ExpenseEntity(
            id = id,
            groupId = targetGroupId,
            createdById = targetCreatedBy,
            payerId = payers.firstOrNull()?.memberId ?: "",
            amountMinor = totalAmount.amountMinor,
            currency = totalAmount.currency.code,
            description = description,
            category = category.name,
            paymentMode = mode.name,
            splitMethod = splitMethod.name,
            notes = notes,
            receiptAttachmentId = receiptAttachmentId,
            timestamp = timestamp,
            isDeleted = isDeleted
        )

        val payerEntities = payers.map {
            ExpensePayerEntity(
                expenseId = id,
                memberId = it.memberId,
                amountMinor = it.amount.amountMinor,
                currency = it.amount.currency.code
            )
        }

        val computedSplits = try {
            com.fairshare.android.core.domain.split.ExpenseSplitEngine.calculateSplits(this)
        } catch (_: Exception) {
            emptyMap()
        }

        val participantEntities = participants.map {
            val share = if (it.excluded) 0L else (computedSplits[it.memberId]?.amountMinor ?: it.exactAmount?.amountMinor ?: 0L)
            ExpenseParticipantEntity(
                expenseId = id,
                memberId = it.memberId,
                shareMinor = share,
                exactAmountMinor = it.exactAmount?.amountMinor,
                percentageBasisPoints = it.percentageBasisPoints,
                shares = it.shares,
                excluded = it.excluded
            )
        }

        val itemEntities = items.map {
            ExpenseItemEntity(
                id = it.id,
                expenseId = id,
                name = it.name,
                amountMinor = it.amount.amountMinor,
                participantIdsCsv = it.participantMemberIds.joinToString(",")
            )
        }

        return Pair(expenseEntity, Triple(payerEntities, participantEntities, itemEntities))
    }

    fun SettlementPaymentEntity.toDomain(): SettlementPayment {
        val curr = Currency(currency, if (currency == "INR") "₹" else "$")
        val paymentMode = try {
            PaymentMode.valueOf(this.paymentMode)
        } catch (_: Exception) {
            PaymentMode.ONLINE
        }

        return SettlementPayment(
            id = id,
            fromMemberId = fromMemberId,
            toMemberId = toMemberId,
            amount = Money(amountMinor, curr),
            timestamp = timestamp,
            mode = paymentMode,
            note = note,
            groupId = groupId,
            isDeleted = isDeleted
        )
    }

    fun SettlementPayment.toEntity(groupId: String = ""): SettlementPaymentEntity {
        return SettlementPaymentEntity(
            id = id,
            groupId = groupId.ifBlank { this.groupId },
            fromMemberId = fromMemberId,
            toMemberId = toMemberId,
            amountMinor = amount.amountMinor,
            currency = amount.currency.code,
            paymentMode = mode.name,
            timestamp = timestamp,
            note = note,
            isDeleted = isDeleted
        )
    }

    fun BudgetWithCategories.toDomain(): BudgetPlan {
        val curr = Currency(budget.currency, if (budget.currency == "INR") "₹" else "$")
        val catMap = categories.associate {
            val cat = try {
                ExpenseCategory.valueOf(it.category)
            } catch (_: Exception) {
                ExpenseCategory.OTHER
            }
            cat to Money(it.allocatedAmountMinor, curr)
        }

        return BudgetPlan(
            id = budget.id,
            baseBudget = Money(budget.baseAmountMinor, curr),
            emergencyBuffer = Money(budget.bufferAmountMinor, curr),
            startDateEpochMs = budget.startDate,
            endDateEpochMs = budget.endDate,
            timezone = budget.timezone,
            categoryAllocations = catMap
        )
    }

    // -------------------------------------------------------------
    // Backend <-> Room Entity Mappings for Synchronization
    // -------------------------------------------------------------

    fun ExpenseWithDetails.toBackend(): BackendExpense {
        val splitMode = try {
            SplitMethod.valueOf(expense.splitMethod)
        } catch (_: Exception) {
            SplitMethod.EQUAL
        }
        val paymentMode = try {
            PaymentMode.valueOf(expense.paymentMode)
        } catch (_: Exception) {
            PaymentMode.ONLINE
        }
        val category = try {
            ExpenseCategory.valueOf(expense.category)
        } catch (_: Exception) {
            ExpenseCategory.OTHER
        }

        var backendPayers = payers.map {
            BackendExpensePayer(
                memberId = it.memberId,
                amountMinor = it.amountMinor,
                currency = it.currency
            )
        }
        if (backendPayers.isEmpty() || backendPayers.sumOf { it.amountMinor } != expense.amountMinor) {
            val primaryPayer = expense.payerId.ifBlank { expense.createdById }
            if (primaryPayer.isNotBlank()) {
                backendPayers = listOf(
                    BackendExpensePayer(
                        memberId = primaryPayer,
                        amountMinor = expense.amountMinor,
                        currency = expense.currency
                    )
                )
            }
        }

        var backendParticipants = participants.map {
            BackendExpenseParticipant(
                memberId = it.memberId,
                shareMinor = it.shareMinor,
                exactAmountMinor = it.exactAmountMinor,
                percentageBasisPoints = it.percentageBasisPoints,
                shares = it.shares,
                excluded = it.excluded
            )
        }
        val activeParts = backendParticipants.filter { !it.excluded }
        if (activeParts.isNotEmpty() && activeParts.sumOf { it.shareMinor } != expense.amountMinor) {
            val splits = com.fairshare.android.core.domain.rounding.RoundingEngine.distributeEqual(
                expense.amountMinor,
                activeParts.map { it.memberId }
            )
            backendParticipants = backendParticipants.map {
                if (it.excluded) it else it.copy(shareMinor = splits[it.memberId] ?: it.shareMinor)
            }
        } else if (backendParticipants.isEmpty()) {
            val defaultMember = expense.payerId.ifBlank { expense.createdById }
            if (defaultMember.isNotBlank()) {
                backendParticipants = listOf(
                    BackendExpenseParticipant(
                        memberId = defaultMember,
                        shareMinor = expense.amountMinor
                    )
                )
            }
        }

        val backendItems = items.map {
            BackendExpenseItem(
                id = it.id,
                expenseId = it.expenseId,
                name = it.name,
                amountMinor = it.amountMinor,
                participantIds = it.participantIdsCsv.split(",").filter { id -> id.isNotBlank() }
            )
        }

        return BackendExpense(
            id = expense.id,
            groupId = expense.groupId,
            createdById = expense.createdById,
            payerId = expense.payerId,
            amountMinor = expense.amountMinor,
            currency = expense.currency,
            description = expense.description,
            merchant = expense.merchant,
            category = category,
            paymentMode = paymentMode,
            splitMethod = splitMode,
            notes = expense.notes,
            receiptAttachmentId = expense.receiptAttachmentId,
            timestamp = expense.timestamp,
            isDeleted = expense.isDeleted,
            deletedAt = expense.deletedAt,
            revision = expense.revision,
            createdAt = expense.createdAt,
            updatedAt = expense.updatedAt,
            payers = backendPayers,
            participants = backendParticipants,
            items = backendItems
        )
    }

    fun BackendExpense.toRoomEntity(): Pair<ExpenseEntity, Triple<List<ExpensePayerEntity>, List<ExpenseParticipantEntity>, List<ExpenseItemEntity>>> {
        val expenseEntity = ExpenseEntity(
            id = id,
            groupId = groupId,
            createdById = createdById,
            payerId = payerId.ifBlank { payers.firstOrNull()?.memberId ?: "" },
            amountMinor = amountMinor,
            currency = currency,
            description = description,
            merchant = merchant,
            category = category.name,
            paymentMode = paymentMode.name,
            splitMethod = splitMethod.name,
            notes = notes,
            receiptAttachmentId = receiptAttachmentId,
            timestamp = timestamp,
            isDeleted = isDeleted,
            deletedAt = deletedAt,
            revision = revision,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

        val payerEntities = payers.map {
            ExpensePayerEntity(
                expenseId = id,
                memberId = it.memberId,
                amountMinor = it.amountMinor,
                currency = it.currency
            )
        }

        val participantEntities = participants.map {
            ExpenseParticipantEntity(
                expenseId = id,
                memberId = it.memberId,
                shareMinor = it.shareMinor,
                exactAmountMinor = it.exactAmountMinor,
                percentageBasisPoints = it.percentageBasisPoints,
                shares = it.shares,
                excluded = it.excluded
            )
        }

        val itemEntities = items.map {
            ExpenseItemEntity(
                id = it.id,
                expenseId = id,
                name = it.name,
                amountMinor = it.amountMinor,
                participantIdsCsv = it.participantIds.joinToString(",")
            )
        }

        return Pair(expenseEntity, Triple(payerEntities, participantEntities, itemEntities))
    }

    fun SettlementPaymentEntity.toBackend(): BackendSettlementPayment {
        val paymentMode = try {
            PaymentMode.valueOf(this.paymentMode)
        } catch (_: Exception) {
            PaymentMode.ONLINE
        }

        return BackendSettlementPayment(
            id = id,
            groupId = groupId,
            fromUserId = fromMemberId,
            toUserId = toMemberId,
            amountMinor = amountMinor,
            currency = currency,
            paymentMode = paymentMode,
            timestamp = timestamp,
            note = note,
            proofAttachmentId = proofAttachmentId,
            createdByUserId = fromMemberId,
            isDeleted = isDeleted,
            deletedAt = deletedAt,
            revision = revision,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun BackendSettlementPayment.toRoomEntity(): SettlementPaymentEntity {
        return SettlementPaymentEntity(
            id = id,
            groupId = groupId,
            fromMemberId = fromUserId,
            toMemberId = toUserId,
            amountMinor = amountMinor,
            currency = currency,
            paymentMode = paymentMode.name,
            timestamp = timestamp,
            note = note,
            proofAttachmentId = proofAttachmentId,
            isDeleted = isDeleted,
            deletedAt = deletedAt,
            revision = revision,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
