package com.fairshare.android.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.database.repository.BudgetRepository
import com.fairshare.android.core.database.repository.ExpenseRepository
import com.fairshare.android.core.database.repository.GroupRepository
import com.fairshare.android.core.database.repository.SettlementRepository
import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseItem
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.model.SplitMethod
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinancialReconstructionFromRoomTest {

    private lateinit var db: FairShareDatabase
    private lateinit var groupRepo: GroupRepository
    private lateinit var expenseRepo: ExpenseRepository
    private lateinit var settlementRepo: SettlementRepository
    private lateinit var budgetRepo: BudgetRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = FairShareDatabase.createInMemory(context)
        groupRepo = GroupRepository(db)
        expenseRepo = ExpenseRepository(db)
        settlementRepo = SettlementRepository(db)
        budgetRepo = BudgetRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testReconstructedFinancialCalculationsMatchPhase2() {
        runBlocking {
            val alice = UserEntity("1", "1001", "Alice")
            val bob = UserEntity("2", "1002", "Bob")
            val charlie = UserEntity("3", "1003", "Charlie")
            val group = GroupEntity("g1", "Goa Trip", createdById = "1")

            groupRepo.createGroup(group, listOf(alice, bob, charlie))

            // 1. Equal Split Expense: Alice pays ₹300 for Alice, Bob, Charlie (₹100 each)
            val e1 = Expense(
                id = "e1",
                groupId = "g1",
                description = "Dinner",
                totalAmount = Money(30000L),
                payers = listOf(ExpensePayer("1", Money(30000L))),
                participants = listOf(
                    ExpenseParticipant("1"),
                    ExpenseParticipant("2"),
                    ExpenseParticipant("3")
                ),
                splitMethod = SplitMethod.EQUAL
            )
            expenseRepo.saveExpense(e1, "g1", "1")

            // 2. Itemized Split Expense: Bob pays ₹200 for Pizza (Alice + Bob: ₹100 each)
            val e2 = Expense(
                id = "e2",
                groupId = "g1",
                description = "Pizza",
                totalAmount = Money(20000L),
                payers = listOf(ExpensePayer("2", Money(20000L))),
                participants = listOf(
                    ExpenseParticipant("1"),
                    ExpenseParticipant("2")
                ),
                splitMethod = SplitMethod.ITEMIZED,
                items = listOf(
                    ExpenseItem("i1", "Pizza", Money(20000L), listOf("1", "2"))
                )
            )
            expenseRepo.saveExpense(e2, "g1", "2")

            // 3. Direct payment: Charlie pays Alice ₹50
            val p1 = SettlementPayment(
                id = "p1",
                groupId = "g1",
                fromMemberId = "3",
                toMemberId = "1",
                amount = Money(5000L)
            )
            settlementRepo.recordPayment(p1, "g1", "3")

            // 4. Budget
            val budgetPlan = BudgetPlan(
                baseBudget = Money(100000L), // ₹1,000
                emergencyBuffer = Money(20000L), // ₹200
                startDateEpochMs = 0L,
                endDateEpochMs = 100000L
            )
            budgetRepo.saveBudget(budgetPlan, "g1")

            // Now compute group balances directly from persisted Room data via SettlementRepository
            val balances = settlementRepo.calculateGroupBalances("g1")
            assertEquals(3, balances.size)

            val aBal = balances.first { it.memberId == "1" }
            assertEquals(5000L, aBal.netBalance.amountMinor)

            val bBal = balances.first { it.memberId == "2" }
            assertEquals(0L, bBal.netBalance.amountMinor)

            val cBal = balances.first { it.memberId == "3" }
            assertEquals(-5000L, cBal.netBalance.amountMinor)

            // Invariant: sum of net balances is 0
            assertEquals(0L, balances.sumOf { it.netBalance.amountMinor })

            // Check settlement plan calculated from persisted data
            val settlementPlan = settlementRepo.calculateSettlementPlan("g1")
            assertFalse(settlementPlan.isSettled)
            assertEquals(1, settlementPlan.transfers.size)
            val transfer = settlementPlan.transfers[0]
            assertEquals("3", transfer.fromMemberId) // Charlie pays
            assertEquals("1", transfer.toMemberId)   // Alice
            assertEquals(5000L, transfer.amount.amountMinor) // ₹50

            // Check budget state calculated from persisted data
            val budgetState = budgetRepo.calculateBudgetState("g1")
            assertNotNull(budgetState)
            assertEquals(50000L, budgetState!!.totalSpent.amountMinor) // 300 + 200 = ₹500
            assertEquals(50000L, budgetState.remainingBaseBudget.amountMinor) // 1000 - 500 = ₹500
            assertEquals(20000L, budgetState.remainingBuffer.amountMinor) // full buffer intact
            assertFalse(budgetState.isBaseBudgetExceeded)
        }
    }
}
