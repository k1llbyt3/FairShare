package com.fairshare.android.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.entity.ActivityEventEntity
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
import com.fairshare.android.core.database.entity.UserEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDaoOperationsTest {

    private lateinit var db: FairShareDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = FairShareDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testUserAndGroupWithMembersDao() {
        runBlocking {
            val u1 = UserEntity("u1", "+919876543210", "Alice")
            val u2 = UserEntity("u2", "+919876543211", "Bob")
            db.userDao().insertUsers(listOf(u1, u2))

            val group = GroupEntity("g1", "Goa Trip", "Beach trip", createdById = "u1")
            db.groupDao().insertGroup(group)

            db.groupDao().insertGroupMembers(
                listOf(
                    GroupMemberEntity("g1", "u1", "OWNER"),
                    GroupMemberEntity("g1", "u2", "MEMBER")
                )
            )

            val groupWithMembers = db.groupDao().getGroupWithMembers("g1")
            assertNotNull(groupWithMembers)
            assertEquals("Goa Trip", groupWithMembers!!.group.name)
            assertEquals(2, groupWithMembers.members.size)
            assertTrue(groupWithMembers.members.any { it.id == "u1" && it.displayName == "Alice" })
            assertTrue(groupWithMembers.members.any { it.id == "u2" && it.displayName == "Bob" })
        }
    }

    @Test
    fun testExpenseWithDetailsDao() {
        runBlocking {
            val u1 = UserEntity("u1", "111", "Alice")
            val u2 = UserEntity("u2", "222", "Bob")
            db.userDao().insertUsers(listOf(u1, u2))

            val group = GroupEntity("g1", "Trip", createdById = "u1")
            db.groupDao().insertGroup(group)

            val expense = ExpenseEntity(
                id = "e1",
                groupId = "g1",
                createdById = "u1",
                payerId = "u1",
                amountMinor = 120000L, // ₹1,200.00
                description = "Dinner",
                splitMethod = "ITEMIZED",
                timestamp = System.currentTimeMillis()
            )
            val payers = listOf(ExpensePayerEntity("e1", "u1", 120000L))
            val participants = listOf(
                ExpenseParticipantEntity("e1", "u1", 60000L),
                ExpenseParticipantEntity("e1", "u2", 60000L)
            )
            val items = listOf(
                ExpenseItemEntity("i1", "e1", "Pizza", 80000L, "u1,u2"),
                ExpenseItemEntity("i2", "e1", "Drinks", 40000L, "u1,u2")
            )

            db.expenseDao().insertExpenseWithDetails(expense, payers, participants, items)

            val retrieved = db.expenseDao().getExpenseWithDetails("e1")
            assertNotNull(retrieved)
            assertEquals("Dinner", retrieved!!.expense.description)
            assertEquals(1, retrieved.payers.size)
            assertEquals(2, retrieved.participants.size)
            assertEquals(2, retrieved.items.size)

            // Soft delete test
            db.expenseDao().softDeleteExpense("e1")
            val activeExpenses = db.expenseDao().getExpensesForGroup("g1")
            assertEquals(0, activeExpenses.size)

            val softDeleted = db.expenseDao().getExpenseWithDetails("e1")
            assertTrue(softDeleted!!.expense.isDeleted)
        }
    }

    @Test
    fun testSettlementPaymentDao() {
        runBlocking {
            val group = GroupEntity("g1", "Trip", createdById = "u1")
            db.groupDao().insertGroup(group)

            val payment = SettlementPaymentEntity(
                id = "p1",
                groupId = "g1",
                fromMemberId = "u2",
                toMemberId = "u1",
                amountMinor = 50000L,
                timestamp = System.currentTimeMillis()
            )
            db.settlementDao().insertPayment(payment)

            val retrieved = db.settlementDao().getPaymentById("p1")
            assertNotNull(retrieved)
            assertEquals(50000L, retrieved!!.amountMinor)

            val list = db.settlementDao().getPaymentsForGroup("g1")
            assertEquals(1, list.size)
        }
    }

    @Test
    fun testBudgetWithCategoriesDao() {
        runBlocking {
            val group = GroupEntity("g1", "Trip", createdById = "u1")
            db.groupDao().insertGroup(group)

            val budget = BudgetEntity(
                id = "b1",
                groupId = "g1",
                baseAmountMinor = 5000000L,
                bufferAmountMinor = 500000L,
                startDate = 1000L,
                endDate = 2000L
            )
            val categories = listOf(
                BudgetCategoryEntity("c1", "b1", "FOOD", 2000000L),
                BudgetCategoryEntity("c2", "b1", "TRANSPORT", 1000000L)
            )
            db.budgetDao().insertBudgetWithCategories(budget, categories)

            val retrieved = db.budgetDao().getActiveBudgetForGroup("g1")
            assertNotNull(retrieved)
            assertEquals(5000000L, retrieved!!.budget.baseAmountMinor)
            assertEquals(2, retrieved.categories.size)
        }
    }

    @Test
    fun testActivityAndMessageAndSyncDao() {
        runBlocking {
            val group = GroupEntity("g1", "Trip", createdById = "u1")
            db.groupDao().insertGroup(group)

            db.activityDao().insertEvent(
                ActivityEventEntity("a1", "g1", "u1", "GROUP_CREATED", "Group created")
            )
            val events = db.activityDao().getEventsForGroup("g1")
            assertEquals(1, events.size)

            db.messageDao().insertMessage(
                MessageEntity("m1", "g1", "u1", "Hello team", timestamp = 100L)
            )
            val messages = db.messageDao().getMessagesForGroup("g1")
            assertEquals(1, messages.size)

            db.syncDao().insertOperation(
                SyncOperationEntity("s1", "EXPENSE", "e1", "INSERT", "{}")
            )
            val pending = db.syncDao().getPendingOperations()
            assertEquals(1, pending.size)

            db.syncDao().insertMetadata(
                SyncMetadataEntity("cursor", "12345")
            )
            val meta = db.syncDao().getMetadata("cursor")
            assertEquals("12345", meta?.value)
        }
    }
}
