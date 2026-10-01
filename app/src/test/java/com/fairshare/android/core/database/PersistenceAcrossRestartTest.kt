package com.fairshare.android.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.entity.ExpenseEntity
import com.fairshare.android.core.database.entity.ExpenseParticipantEntity
import com.fairshare.android.core.database.entity.ExpensePayerEntity
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.GroupMemberEntity
import com.fairshare.android.core.database.entity.UserEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistenceAcrossRestartTest {

    @Test
    fun testDataPersistenceAcrossDatabaseCloseAndReopen() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbFile = File(context.filesDir, "test_restart.db")
            if (dbFile.exists()) dbFile.delete()

            // 1. Initial process: Open DB, insert data, and close
            var db = Room.databaseBuilder(context, FairShareDatabase::class.java, dbFile.absolutePath)
                .allowMainThreadQueries()
                .build()

            val u1 = UserEntity("u1", "+919999900001", "Alice")
            val u2 = UserEntity("u2", "+919999900002", "Bob")
            db.userDao().insertUsers(listOf(u1, u2))

            val group = GroupEntity("g1", "Road Trip", createdById = "u1")
            db.groupDao().insertGroup(group)
            db.groupDao().insertGroupMembers(
                listOf(
                    GroupMemberEntity("g1", "u1", "OWNER"),
                    GroupMemberEntity("g1", "u2", "MEMBER")
                )
            )

            val expense = ExpenseEntity(
                id = "e1",
                groupId = "g1",
                createdById = "u1",
                payerId = "u1",
                amountMinor = 50000L, // ₹500.00
                description = "Fuel",
                timestamp = 1000L
            )
            val payers = listOf(ExpensePayerEntity("e1", "u1", 50000L))
            val participants = listOf(
                ExpenseParticipantEntity("e1", "u1", 25000L),
                ExpenseParticipantEntity("e1", "u2", 25000L)
            )
            db.expenseDao().insertExpenseWithDetails(expense, payers, participants, emptyList())

            // Simulate app kill / process termination
            db.close()

            // 2. Restart process: Reopen the same database file
            db = Room.databaseBuilder(context, FairShareDatabase::class.java, dbFile.absolutePath)
                .allowMainThreadQueries()
                .build()

            // Verify group and members survived
            val groupWithMembers = db.groupDao().getGroupWithMembers("g1")
            assertNotNull(groupWithMembers)
            assertEquals("Road Trip", groupWithMembers!!.group.name)
            assertEquals(2, groupWithMembers.members.size)

            // Verify expense and split details survived exactly
            val retrievedExpense = db.expenseDao().getExpenseWithDetails("e1")
            assertNotNull(retrievedExpense)
            assertEquals(50000L, retrievedExpense!!.expense.amountMinor)
            assertEquals("Fuel", retrievedExpense.expense.description)
            assertEquals(1, retrievedExpense.payers.size)
            assertEquals(50000L, retrievedExpense.payers[0].amountMinor)
            assertEquals(2, retrievedExpense.participants.size)
            assertEquals(25000L, retrievedExpense.participants.first { it.memberId == "u1" }.shareMinor)
            assertEquals(25000L, retrievedExpense.participants.first { it.memberId == "u2" }.shareMinor)

            db.close()
            dbFile.delete()
        }
    }
}
