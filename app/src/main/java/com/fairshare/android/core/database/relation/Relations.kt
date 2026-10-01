package com.fairshare.android.core.database.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.fairshare.android.core.database.entity.BudgetCategoryEntity
import com.fairshare.android.core.database.entity.BudgetEntity
import com.fairshare.android.core.database.entity.ExpenseEntity
import com.fairshare.android.core.database.entity.ExpenseItemEntity
import com.fairshare.android.core.database.entity.ExpenseParticipantEntity
import com.fairshare.android.core.database.entity.ExpensePayerEntity
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.GroupMemberEntity
import com.fairshare.android.core.database.entity.SettlementPaymentEntity
import com.fairshare.android.core.database.entity.UserEntity

data class GroupWithMembers(
    @Embedded
    val group: GroupEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = GroupMemberEntity::class,
            parentColumn = "groupId",
            entityColumn = "userId"
        )
    )
    val members: List<UserEntity>
)

data class ExpenseWithDetails(
    @Embedded
    val expense: ExpenseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "expenseId"
    )
    val payers: List<ExpensePayerEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "expenseId"
    )
    val participants: List<ExpenseParticipantEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "expenseId"
    )
    val items: List<ExpenseItemEntity>
)

data class BudgetWithCategories(
    @Embedded
    val budget: BudgetEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "budgetId"
    )
    val categories: List<BudgetCategoryEntity>
)

data class GroupWithDetails(
    @Embedded
    val group: GroupEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "groupId"
    )
    val memberCrossReferences: List<GroupMemberEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "groupId"
    )
    val expenses: List<ExpenseEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "groupId"
    )
    val payments: List<SettlementPaymentEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "groupId"
    )
    val budgets: List<BudgetEntity>
)
