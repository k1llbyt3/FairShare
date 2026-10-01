package com.fairshare.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fairshare.android.core.database.entity.ActivityEventEntity
import com.fairshare.android.core.database.entity.AttachmentEntity
import com.fairshare.android.core.database.entity.BudgetCategoryEntity
import com.fairshare.android.core.database.entity.BudgetEntity
import com.fairshare.android.core.database.entity.ExpenseEntity
import com.fairshare.android.core.database.entity.ExpenseItemEntity
import com.fairshare.android.core.database.entity.ExpenseParticipantEntity
import com.fairshare.android.core.database.entity.ExpensePayerEntity
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.GroupMemberEntity
import com.fairshare.android.core.database.entity.MessageEntity
import com.fairshare.android.core.database.entity.SettlementPaymentEntity
import com.fairshare.android.core.database.entity.SyncMetadataEntity
import com.fairshare.android.core.database.entity.SyncOperationEntity
import com.fairshare.android.core.database.entity.TransactionCandidateEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.database.relation.BudgetWithCategories
import com.fairshare.android.core.database.relation.ExpenseWithDetails
import com.fairshare.android.core.database.relation.GroupWithDetails
import com.fairshare.android.core.database.relation.GroupWithMembers
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    fun getUserFlow(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>
}

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getGroupById(id: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE id = :id")
    fun getGroupFlow(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE isArchived = 0 ORDER BY updatedAt DESC")
    suspend fun getAllActiveGroups(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getAllActiveGroupsFlow(): Flow<List<GroupEntity>>

    @Query("SELECT DISTINCT g.* FROM groups g LEFT JOIN group_members gm ON g.id = gm.groupId WHERE (gm.userId = :userId OR g.createdById = :userId) AND g.isArchived = 0 ORDER BY g.updatedAt DESC")
    suspend fun getUserActiveGroups(userId: String): List<GroupEntity>

    @Query("SELECT DISTINCT g.* FROM groups g LEFT JOIN group_members gm ON g.id = gm.groupId WHERE (gm.userId = :userId OR g.createdById = :userId) AND g.isArchived = 0 ORDER BY g.updatedAt DESC")
    fun getUserActiveGroupsFlow(userId: String): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE isArchived = 1 ORDER BY updatedAt DESC")
    suspend fun getAllArchivedGroups(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun getAllArchivedGroupsFlow(): Flow<List<GroupEntity>>

    @Query("SELECT DISTINCT g.* FROM groups g LEFT JOIN group_members gm ON g.id = gm.groupId WHERE (gm.userId = :userId OR g.createdById = :userId) AND g.isArchived = 1 ORDER BY g.updatedAt DESC")
    suspend fun getUserArchivedGroups(userId: String): List<GroupEntity>

    @Query("SELECT DISTINCT g.* FROM groups g LEFT JOIN group_members gm ON g.id = gm.groupId WHERE (gm.userId = :userId OR g.createdById = :userId) AND g.isArchived = 1 ORDER BY g.updatedAt DESC")
    fun getUserArchivedGroupsFlow(userId: String): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMember(crossRef: GroupMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMembers(crossRefs: List<GroupMemberEntity>)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun removeGroupMember(groupId: String, userId: String)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun getMemberRole(groupId: String, userId: String): GroupMemberEntity?

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getGroupMemberCrossRefs(groupId: String): List<GroupMemberEntity>

    @Transaction
    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun getGroupWithMembers(groupId: String): GroupWithMembers?

    @Transaction
    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun getGroupWithMembersFlow(groupId: String): Flow<GroupWithMembers?>

    @Transaction
    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun getGroupWithDetails(groupId: String): GroupWithDetails?

    @Query("SELECT u.* FROM users u INNER JOIN group_members gm ON u.id = gm.userId WHERE gm.groupId = :groupId")
    suspend fun getMembersForGroup(groupId: String): List<UserEntity>

    @Query("SELECT u.* FROM users u INNER JOIN group_members gm ON u.id = gm.userId WHERE gm.groupId = :groupId")
    fun getMembersForGroupFlow(groupId: String): Flow<List<UserEntity>>
}

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayers(payers: List<ExpensePayerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipants(participants: List<ExpenseParticipantEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ExpenseItemEntity>)

    @Query("DELETE FROM expense_payers WHERE expenseId = :expenseId")
    suspend fun deletePayers(expenseId: String)

    @Query("DELETE FROM expense_participants WHERE expenseId = :expenseId")
    suspend fun deleteParticipants(expenseId: String)

    @Query("DELETE FROM expense_items WHERE expenseId = :expenseId")
    suspend fun deleteItems(expenseId: String)

    @Transaction
    suspend fun insertExpenseWithDetails(
        expense: ExpenseEntity,
        payers: List<ExpensePayerEntity>,
        participants: List<ExpenseParticipantEntity>,
        items: List<ExpenseItemEntity>
    ) {
        insertExpense(expense)
        deletePayers(expense.id)
        insertPayers(payers)
        deleteParticipants(expense.id)
        insertParticipants(participants)
        deleteItems(expense.id)
        if (items.isNotEmpty()) {
            insertItems(items)
        }
    }

    @Transaction
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseWithDetails(id: String): ExpenseWithDetails?

    @Transaction
    @Query("SELECT * FROM expenses WHERE groupId = :groupId AND isDeleted = 0 ORDER BY timestamp DESC")
    suspend fun getExpensesForGroup(groupId: String): List<ExpenseWithDetails>

    @Transaction
    @Query("SELECT * FROM expenses WHERE groupId = :groupId AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getExpensesForGroupFlow(groupId: String): Flow<List<ExpenseWithDetails>>

    @Query("UPDATE expenses SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteExpense(id: String, deletedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun hardDeleteExpense(id: String)
}

@Dao
interface SettlementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: SettlementPaymentEntity)

    @Query("SELECT * FROM settlement_payments WHERE id = :id")
    suspend fun getPaymentById(id: String): SettlementPaymentEntity?

    @Query("SELECT * FROM settlement_payments WHERE groupId = :groupId AND isDeleted = 0 ORDER BY timestamp DESC")
    suspend fun getPaymentsForGroup(groupId: String): List<SettlementPaymentEntity>

    @Query("SELECT * FROM settlement_payments WHERE groupId = :groupId AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getPaymentsForGroupFlow(groupId: String): Flow<List<SettlementPaymentEntity>>

    @Query("UPDATE settlement_payments SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeletePayment(id: String, deletedAt: Long = System.currentTimeMillis())
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<BudgetCategoryEntity>)

    @Query("DELETE FROM budget_categories WHERE budgetId = :budgetId")
    suspend fun deleteCategories(budgetId: String)

    @Transaction
    suspend fun insertBudgetWithCategories(
        budget: BudgetEntity,
        categories: List<BudgetCategoryEntity>
    ) {
        insertBudget(budget)
        deleteCategories(budget.id)
        if (categories.isNotEmpty()) {
            insertCategories(categories)
        }
    }

    @Transaction
    @Query("SELECT * FROM budgets WHERE groupId = :groupId AND enabled = 1 LIMIT 1")
    suspend fun getActiveBudgetForGroup(groupId: String): BudgetWithCategories?

    @Transaction
    @Query("SELECT * FROM budgets WHERE groupId = :groupId AND enabled = 1 LIMIT 1")
    fun getActiveBudgetForGroupFlow(groupId: String): Flow<BudgetWithCategories?>
}

@Dao
interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ActivityEventEntity)

    @Query("SELECT * FROM activity_events WHERE groupId = :groupId ORDER BY timestamp DESC")
    suspend fun getEventsForGroup(groupId: String): List<ActivityEventEntity>

    @Query("SELECT * FROM activity_events WHERE groupId = :groupId ORDER BY timestamp DESC")
    fun getEventsForGroupFlow(groupId: String): Flow<List<ActivityEventEntity>>
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE groupId = :groupId AND isDeleted = 0 ORDER BY timestamp ASC")
    suspend fun getMessagesForGroup(groupId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE groupId = :groupId AND isDeleted = 0 ORDER BY timestamp ASC")
    fun getMessagesForGroupFlow(groupId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE referencedEntityId = :entityId AND isDeleted = 0 ORDER BY timestamp ASC")
    suspend fun getMessagesForReferencedEntity(entityId: String): List<MessageEntity>
}

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperation(operation: SyncOperationEntity)

    @Query("SELECT * FROM sync_operations WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingOperations(): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_operations WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun getPendingOperationsFlow(): Flow<List<SyncOperationEntity>>

    @Query("SELECT COUNT(*) FROM sync_operations WHERE status = 'PENDING'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM sync_operations WHERE entityId = :entityId")
    suspend fun getOperationsForEntity(entityId: String): List<SyncOperationEntity>

    @Query("DELETE FROM sync_operations WHERE entityId = :entityId")
    suspend fun deleteOperationsForEntity(entityId: String)

    @Query("SELECT * FROM sync_operations WHERE status = 'FAILED' ORDER BY createdAt ASC")
    suspend fun getFailedOperations(): List<SyncOperationEntity>

    @Update
    suspend fun updateOperation(operation: SyncOperationEntity)

    @Query("DELETE FROM sync_operations WHERE id = :id")
    suspend fun deleteOperation(id: String)

    @Query("DELETE FROM sync_operations")
    suspend fun clearAllOperations()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata WHERE meta_key = :key")
    suspend fun getMetadata(key: String): SyncMetadataEntity?

    @Query("DELETE FROM sync_metadata WHERE meta_key = :key")
    suspend fun deleteMetadata(key: String)

    @Query("SELECT * FROM sync_metadata")
    suspend fun getAllMetadata(): List<SyncMetadataEntity>
}

@Dao
interface CandidateAndAttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidate(candidate: TransactionCandidateEntity)

    @Query("SELECT * FROM transaction_candidates WHERE status = :status ORDER BY detectedTimestamp DESC")
    suspend fun getCandidatesByStatus(status: String): List<TransactionCandidateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE groupId = :groupId")
    suspend fun getAttachmentsForGroup(groupId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getAttachmentById(id: String): AttachmentEntity?

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachment(id: String)
}
