package com.fairshare.android.core.database.repository

import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.ActivityEventEntity
import com.fairshare.android.core.database.entity.BudgetCategoryEntity
import com.fairshare.android.core.database.entity.BudgetEntity
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.GroupMemberEntity
import com.fairshare.android.core.database.entity.MessageEntity
import com.fairshare.android.core.database.entity.SyncOperationEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.database.mapper.EntityMappers.toDomain
import com.fairshare.android.core.database.mapper.EntityMappers.toEntity
import com.fairshare.android.core.database.relation.GroupWithMembers
import com.fairshare.android.core.domain.balance.BalanceEngine
import com.fairshare.android.core.domain.balance.MemberBalance
import com.fairshare.android.core.domain.budget.BudgetEngine
import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.budget.BudgetState
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.settlement.SettlementEngine
import com.fairshare.android.core.domain.settlement.SettlementPlan
import com.fairshare.android.core.domain.summary.DailySummaryEngine
import com.fairshare.android.core.domain.summary.DailySummaryFacts
import com.fairshare.android.core.domain.validation.IntegrityValidator
import com.fairshare.android.core.domain.validation.ValidationResult
import com.fairshare.android.core.sync.SyncEngine
import com.fairshare.android.core.sync.SyncEntityType
import com.fairshare.android.core.sync.SyncOperation
import com.fairshare.android.core.sync.SyncOperationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class GroupRepository(private val db: FairShareDatabase) {
    private val groupDao = db.groupDao()
    private val userDao = db.userDao()
    private val activityDao = db.activityDao()

    suspend fun createGroup(group: GroupEntity, members: List<UserEntity>) {
        userDao.insertUsers(members)
        groupDao.insertGroup(group)
        val crossRefs = members.map {
            GroupMemberEntity(
                groupId = group.id,
                userId = it.id,
                role = if (it.id == group.createdById) "OWNER" else "MEMBER"
            )
        }
        groupDao.insertGroupMembers(crossRefs)
        activityDao.insertEvent(
            ActivityEventEntity(
                id = UUID.randomUUID().toString(),
                groupId = group.id,
                actorId = group.createdById,
                eventType = "GROUP_CREATED",
                summaryText = "Created group ${group.name}"
            )
        )
    }

    suspend fun getGroup(groupId: String): GroupEntity? = groupDao.getGroupById(groupId)

    fun getGroupFlow(groupId: String): Flow<GroupEntity?> = groupDao.getGroupFlow(groupId)

    suspend fun getAllActiveGroups(): List<GroupEntity> = groupDao.getAllActiveGroups()

    fun getAllActiveGroupsFlow(): Flow<List<GroupEntity>> = groupDao.getAllActiveGroupsFlow()

    suspend fun getGroupWithMembers(groupId: String): GroupWithMembers? = groupDao.getGroupWithMembers(groupId)

    fun getGroupWithMembersFlow(groupId: String): Flow<GroupWithMembers?> = groupDao.getGroupWithMembersFlow(groupId)

    suspend fun getMembers(groupId: String): List<Member> {
        return groupDao.getMembersForGroup(groupId).map { it.toDomain() }
    }

    fun getMembersFlow(groupId: String): Flow<List<Member>> {
        return groupDao.getMembersForGroupFlow(groupId).map { users -> users.map { it.toDomain() } }
    }

    suspend fun addMember(groupId: String, user: UserEntity, role: String = "MEMBER") {
        userDao.insertUser(user)
        groupDao.insertGroupMember(GroupMemberEntity(groupId = groupId, userId = user.id, role = role))
        activityDao.insertEvent(
            ActivityEventEntity(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                actorId = user.id,
                eventType = "MEMBER_JOINED",
                summaryText = "${user.displayName} joined the group"
            )
        )
    }

    suspend fun removeMember(groupId: String, userId: String) {
        groupDao.removeGroupMember(groupId, userId)
    }
}

class ExpenseRepository(
    private val db: FairShareDatabase,
    private val syncEngine: SyncEngine? = null
) {
    private val expenseDao = db.expenseDao()
    private val activityDao = db.activityDao()
    private val syncDao = db.syncDao()

    suspend fun saveExpense(expense: Expense, groupId: String, actorId: String = "") {
        // Enforce Phase 2 domain validation integrity
        val validation = IntegrityValidator.validateExpense(expense)
        if (validation is ValidationResult.Invalid) {
            throw IllegalArgumentException("Cannot persist invalid expense: ${validation.firstError}")
        }

        val (entity, parts) = expense.toEntity(groupId = groupId, createdById = actorId)
        val (payers, participants, items) = parts

        expenseDao.insertExpenseWithDetails(entity, payers, participants, items)

        activityDao.insertEvent(
            ActivityEventEntity(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                actorId = actorId.ifBlank { expense.payers.firstOrNull()?.memberId ?: "" },
                eventType = "EXPENSE_CREATED",
                summaryText = "Added ${expense.description}: ${expense.totalAmount.formatted()}",
                entityType = "EXPENSE",
                entityId = expense.id
            )
        )

        val op = SyncOperation(
            operationId = UUID.randomUUID().toString(),
            entityId = expense.id,
            entityType = SyncEntityType.EXPENSE,
            operationType = SyncOperationType.INSERT,
            groupId = groupId,
            baseRevision = entity.revision,
            payloadJson = "{\"groupId\":\"$groupId\",\"baseRevision\":${entity.revision}}"
        )

        if (syncEngine != null) {
            syncEngine.enqueueOperation(op)
        } else {
            syncDao.insertOperation(
                SyncOperationEntity(
                    id = op.operationId,
                    entityType = "EXPENSE",
                    entityId = expense.id,
                    operationType = "INSERT",
                    payloadJson = op.payloadJson
                )
            )
        }
    }

    suspend fun updateExpense(expense: Expense, groupId: String, baseRevision: Long = 1L, actorId: String = "") {
        val validation = IntegrityValidator.validateExpense(expense)
        if (validation is ValidationResult.Invalid) {
            throw IllegalArgumentException("Cannot persist invalid expense: ${validation.firstError}")
        }

        val (entity, parts) = expense.toEntity(groupId = groupId, createdById = actorId)
        val (payers, participants, items) = parts

        expenseDao.insertExpenseWithDetails(entity, payers, participants, items)

        val op = SyncOperation(
            operationId = UUID.randomUUID().toString(),
            entityId = expense.id,
            entityType = SyncEntityType.EXPENSE,
            operationType = SyncOperationType.UPDATE,
            groupId = groupId,
            baseRevision = baseRevision,
            payloadJson = "{\"groupId\":\"$groupId\",\"baseRevision\":$baseRevision}"
        )

        if (syncEngine != null) {
            syncEngine.enqueueOperation(op)
        } else {
            syncDao.insertOperation(
                SyncOperationEntity(
                    id = op.operationId,
                    entityType = "EXPENSE",
                    entityId = expense.id,
                    operationType = "UPDATE",
                    payloadJson = op.payloadJson
                )
            )
        }
    }

    suspend fun getExpense(id: String): Expense? {
        return expenseDao.getExpenseWithDetails(id)?.toDomain()
    }

    suspend fun getExpenses(groupId: String): List<Expense> {
        return expenseDao.getExpensesForGroup(groupId).map { it.toDomain() }
    }

    fun getExpensesFlow(groupId: String): Flow<List<Expense>> {
        return expenseDao.getExpensesForGroupFlow(groupId).map { list -> list.map { it.toDomain() } }
    }

    suspend fun reverseExpense(id: String, groupId: String, reason: String = "", actorId: String = "") {
        val existing = expenseDao.getExpenseWithDetails(id)
        val description = existing?.expense?.description ?: id
        expenseDao.softDeleteExpense(id)
        val summary = if (reason.isNotBlank()) "Reversed expense '$description': $reason" else "Reversed expense '$description'"
        activityDao.insertEvent(
            ActivityEventEntity(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                actorId = actorId,
                eventType = "EXPENSE_REVERSED",
                summaryText = summary,
                entityType = "EXPENSE",
                entityId = id
            )
        )

        val op = SyncOperation(
            operationId = UUID.randomUUID().toString(),
            entityId = id,
            entityType = SyncEntityType.EXPENSE,
            operationType = SyncOperationType.DELETE,
            groupId = groupId,
            baseRevision = 1L,
            payloadJson = "{\"groupId\":\"$groupId\",\"baseRevision\":1,\"reason\":\"${reason.replace("\"", "\\\"")}\"}"
        )

        if (syncEngine != null) {
            syncEngine.enqueueOperation(op)
        } else {
            syncDao.insertOperation(
                SyncOperationEntity(
                    id = op.operationId,
                    entityType = "EXPENSE",
                    entityId = id,
                    operationType = "DELETE",
                    payloadJson = op.payloadJson
                )
            )
        }
    }

    suspend fun softDeleteExpense(id: String, groupId: String, actorId: String = "") {
        expenseDao.softDeleteExpense(id)
        activityDao.insertEvent(
            ActivityEventEntity(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                actorId = actorId,
                eventType = "EXPENSE_DELETED",
                summaryText = "Deleted expense $id",
                entityType = "EXPENSE",
                entityId = id
            )
        )

        val op = SyncOperation(
            operationId = UUID.randomUUID().toString(),
            entityId = id,
            entityType = SyncEntityType.EXPENSE,
            operationType = SyncOperationType.DELETE,
            groupId = groupId,
            baseRevision = 1L,
            payloadJson = "{\"groupId\":\"$groupId\",\"baseRevision\":1}"
        )

        if (syncEngine != null) {
            syncEngine.enqueueOperation(op)
        } else {
            syncDao.insertOperation(
                SyncOperationEntity(
                    id = op.operationId,
                    entityType = "EXPENSE",
                    entityId = id,
                    operationType = "DELETE",
                    payloadJson = op.payloadJson
                )
            )
        }
    }
}

class SettlementRepository(
    private val db: FairShareDatabase,
    private val syncEngine: SyncEngine? = null
) {
    private val settlementDao = db.settlementDao()
    private val activityDao = db.activityDao()
    private val syncDao = db.syncDao()
    private val groupDao = db.groupDao()
    private val expenseDao = db.expenseDao()

    suspend fun recordPayment(payment: SettlementPayment, groupId: String, actorId: String = "") {
        val validation = IntegrityValidator.validatePayment(payment)
        if (validation is ValidationResult.Invalid) {
            throw IllegalArgumentException("Cannot persist invalid payment: ${validation.firstError}")
        }

        val entity = payment.toEntity(groupId = groupId)
        settlementDao.insertPayment(entity)

        activityDao.insertEvent(
            ActivityEventEntity(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                actorId = actorId.ifBlank { payment.fromMemberId },
                eventType = "PAYMENT_RECORDED",
                summaryText = "Recorded transfer: ${payment.amount.formatted()}",
                entityType = "PAYMENT",
                entityId = payment.id
            )
        )

        val op = SyncOperation(
            operationId = UUID.randomUUID().toString(),
            entityId = payment.id,
            entityType = SyncEntityType.PAYMENT,
            operationType = SyncOperationType.INSERT,
            groupId = groupId,
            baseRevision = entity.revision,
            payloadJson = "{\"groupId\":\"$groupId\",\"baseRevision\":${entity.revision}}"
        )

        if (syncEngine != null) {
            syncEngine.enqueueOperation(op)
        } else {
            syncDao.insertOperation(
                SyncOperationEntity(
                    id = op.operationId,
                    entityType = "PAYMENT",
                    entityId = payment.id,
                    operationType = "INSERT",
                    payloadJson = op.payloadJson
                )
            )
        }
    }

    suspend fun deletePayment(id: String, groupId: String) {
        settlementDao.softDeletePayment(id)
        val op = SyncOperation(
            operationId = UUID.randomUUID().toString(),
            entityId = id,
            entityType = SyncEntityType.PAYMENT,
            operationType = SyncOperationType.DELETE,
            groupId = groupId,
            baseRevision = 1L,
            payloadJson = "{\"groupId\":\"$groupId\",\"baseRevision\":1}"
        )
        if (syncEngine != null) {
            syncEngine.enqueueOperation(op)
        } else {
            syncDao.insertOperation(
                SyncOperationEntity(
                    id = op.operationId,
                    entityType = "PAYMENT",
                    entityId = id,
                    operationType = "DELETE",
                    payloadJson = op.payloadJson
                )
            )
        }
    }

    suspend fun getPayments(groupId: String): List<SettlementPayment> {
        return settlementDao.getPaymentsForGroup(groupId).map { it.toDomain() }
    }

    fun getPaymentsFlow(groupId: String): Flow<List<SettlementPayment>> {
        return settlementDao.getPaymentsForGroupFlow(groupId).map { list -> list.map { it.toDomain() } }
    }

    suspend fun calculateGroupBalances(groupId: String): List<MemberBalance> {
        val members = groupDao.getMembersForGroup(groupId).map { it.toDomain() }
        val expenses = expenseDao.getExpensesForGroup(groupId).map { it.toDomain() }
        val payments = settlementDao.getPaymentsForGroup(groupId).map { it.toDomain() }

        return BalanceEngine.calculateGroupBalances(members, expenses, payments)
    }

    suspend fun calculateSettlementPlan(groupId: String): SettlementPlan {
        val balances = calculateGroupBalances(groupId)
        return SettlementEngine.calculateSettlement(balances)
    }
}

class BudgetRepository(
    private val db: FairShareDatabase,
    private val syncEngine: SyncEngine? = null
) {
    private val budgetDao = db.budgetDao()
    private val expenseDao = db.expenseDao()
    private val activityDao = db.activityDao()

    suspend fun saveBudget(plan: BudgetPlan, groupId: String, actorId: String = "") {
        val existing = budgetDao.getActiveBudgetForGroup(groupId)
        val budgetId = plan.id.ifBlank { existing?.budget?.id ?: UUID.randomUUID().toString() }
        val budgetEntity = BudgetEntity(
            id = budgetId,
            groupId = groupId,
            baseAmountMinor = plan.baseBudget.amountMinor,
            bufferAmountMinor = plan.emergencyBuffer.amountMinor,
            currency = plan.baseBudget.currency.code,
            startDate = plan.startDateEpochMs,
            endDate = plan.endDateEpochMs,
            timezone = plan.timezone,
            enabled = true,
            updatedAt = System.currentTimeMillis()
        )
        val categoryEntities = plan.categoryAllocations.map { (cat, money) ->
            BudgetCategoryEntity(
                id = UUID.randomUUID().toString(),
                budgetId = budgetId,
                category = cat.name,
                allocatedAmountMinor = money.amountMinor
            )
        }
        budgetDao.insertBudgetWithCategories(budgetEntity, categoryEntities)

        activityDao.insertEvent(
            ActivityEventEntity(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                actorId = actorId,
                eventType = "BUDGET_SAVED",
                summaryText = "Budget: ${plan.baseBudget.formatted()} (Buffer: ${plan.emergencyBuffer.formatted()})",
                entityType = "BUDGET",
                entityId = budgetId
            )
        )
    }

    suspend fun setBudget(groupId: String, plan: BudgetPlan, actorId: String = "") {
        saveBudget(plan = plan, groupId = groupId, actorId = actorId)
    }

    suspend fun getActiveBudget(groupId: String): BudgetPlan? {
        return budgetDao.getActiveBudgetForGroup(groupId)?.toDomain()
    }

    fun getActiveBudgetFlow(groupId: String): Flow<BudgetPlan?> {
        return budgetDao.getActiveBudgetForGroupFlow(groupId).map { it?.toDomain() }
    }

    suspend fun calculateBudgetState(groupId: String): BudgetState? {
        val plan = getActiveBudget(groupId) ?: return null
        val expenses = expenseDao.getExpensesForGroup(groupId).map { it.toDomain() }
        return BudgetEngine.calculateBudgetState(plan, expenses)
    }

    suspend fun getDailySummary(groupId: String, dateEpochMs: Long = System.currentTimeMillis()): DailySummaryFacts {
        val plan = getActiveBudget(groupId)
        val expenses = expenseDao.getExpensesForGroup(groupId).map { it.toDomain() }
        val currency = plan?.baseBudget?.currency ?: expenses.firstOrNull()?.amount?.currency ?: Currency.INR

        val tz = try {
            java.util.TimeZone.getTimeZone(plan?.timezone ?: "UTC")
        } catch (_: Exception) {
            java.util.TimeZone.getTimeZone("UTC")
        }
        val cal = java.util.Calendar.getInstance(tz).apply {
            timeInMillis = dateEpochMs
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val dayStart = cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val dayEnd = cal.timeInMillis - 1

        return DailySummaryEngine.generateDailySummary(
            allExpenses = expenses,
            dayStartEpochMs = dayStart,
            dayEndEpochMs = dayEnd,
            budgetPlan = plan,
            currency = currency
        )
    }
}

class ActivityRepository(private val db: FairShareDatabase) {
    private val activityDao = db.activityDao()

    suspend fun recordEvent(event: ActivityEventEntity) = activityDao.insertEvent(event)

    suspend fun getEvents(groupId: String): List<ActivityEventEntity> = activityDao.getEventsForGroup(groupId)

    fun getEventsFlow(groupId: String): Flow<List<ActivityEventEntity>> = activityDao.getEventsForGroupFlow(groupId)
}

class MessageRepository(private val db: FairShareDatabase) {
    private val messageDao = db.messageDao()

    suspend fun sendMessage(message: MessageEntity) = messageDao.insertMessage(message)

    suspend fun getMessages(groupId: String): List<MessageEntity> = messageDao.getMessagesForGroup(groupId)

    fun getMessagesFlow(groupId: String): Flow<List<MessageEntity>> = messageDao.getMessagesForGroupFlow(groupId)
}
