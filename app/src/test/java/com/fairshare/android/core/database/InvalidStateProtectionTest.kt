package com.fairshare.android.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.database.repository.ExpenseRepository
import com.fairshare.android.core.database.repository.GroupRepository
import com.fairshare.android.core.database.repository.SettlementRepository
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.model.SplitMethod
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvalidStateProtectionTest {

    private lateinit var db: FairShareDatabase
    private lateinit var groupRepo: GroupRepository
    private lateinit var expenseRepo: ExpenseRepository
    private lateinit var settlementRepo: SettlementRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = FairShareDatabase.createInMemory(context)
        groupRepo = GroupRepository(db)
        expenseRepo = ExpenseRepository(db)
        settlementRepo = SettlementRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidExpenseRejectedAndNotPersisted() {
        runBlocking {
            val alice = UserEntity("1", "1001", "Alice")
            val group = GroupEntity("g1", "Trip", createdById = "1")
            groupRepo.createGroup(group, listOf(alice))

            // Invalid: Expense of 100, but payer only paid 80
            val invalidExpense = Expense(
                id = "bad_e1",
                groupId = "g1",
                description = "Mismatch",
                totalAmount = Money(10000L),
                payers = listOf(ExpensePayer("1", Money(8000L))), // mismatch!
                participants = listOf(ExpenseParticipant("1")),
                splitMethod = SplitMethod.EQUAL
            )

            try {
                expenseRepo.saveExpense(invalidExpense, "g1", "1")
            } finally {
                // Verify nothing was saved
                val expenses = expenseRepo.getExpenses("g1")
                assertEquals(0, expenses.size)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPaymentToSelfRejectedAndNotPersisted() {
        runBlocking {
            val alice = UserEntity("1", "1001", "Alice")
            val group = GroupEntity("g1", "Trip", createdById = "1")
            groupRepo.createGroup(group, listOf(alice))

            // Invalid: Alice pays Alice
            val invalidPayment = SettlementPayment(
                id = "bad_p1",
                groupId = "g1",
                fromMemberId = "1",
                toMemberId = "1", // Self payment
                amount = Money(5000L)
            )

            try {
                settlementRepo.recordPayment(invalidPayment, "g1", "1")
            } finally {
                val payments = settlementRepo.getPayments("g1")
                assertEquals(0, payments.size)
            }
        }
    }
}
