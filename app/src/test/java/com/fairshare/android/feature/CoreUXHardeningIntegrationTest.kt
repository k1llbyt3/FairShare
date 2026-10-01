package com.fairshare.android.feature

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.database.repository.BudgetRepository
import com.fairshare.android.core.database.repository.ExpenseRepository
import com.fairshare.android.core.database.repository.GroupRepository
import com.fairshare.android.core.database.repository.SettlementRepository
import com.fairshare.android.core.domain.balance.BalanceEngine
import com.fairshare.android.core.domain.budget.BudgetEngine
import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.budget.BufferState
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.domain.settlement.SettlementEngine
import com.fairshare.android.core.domain.simulation.SimulationEngine
import com.fairshare.android.core.domain.summary.DailySummaryEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoreUXHardeningIntegrationTest {

    private lateinit var db: FairShareDatabase
    private lateinit var groupRepo: GroupRepository
    private lateinit var expenseRepo: ExpenseRepository
    private lateinit var settlementRepo: SettlementRepository
    private lateinit var budgetRepo: BudgetRepository

    private val inr = Currency("INR", "₹", 2)
    private val groupId = "group_hardened_journey"
    private val userA = Member(id = "user_a", name = "Aarav", isCurrentUser = true)
    private val userB = Member(id = "user_b", name = "Bhavna")
    private val userC = Member(id = "user_c", name = "Chirag")
    private val members = listOf(userA, userB, userC)

    private val tripStartEpoch = 1775000000000L
    private val dayMillis = 86_400_000L
    private val tripEndEpoch = tripStartEpoch + (4 * dayMillis) // 5 days

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = FairShareDatabase.createInMemory(context)
        groupRepo = GroupRepository(db)
        expenseRepo = ExpenseRepository(db)
        settlementRepo = SettlementRepository(db)
        budgetRepo = BudgetRepository(db)

        val group = GroupEntity(
            id = groupId,
            name = "Coorg Trek 2026",
            createdById = userA.id,
            currency = "INR",
            createdAt = tripStartEpoch
        )
        val userEntities = members.mapIndexed { idx, m ->
            UserEntity(id = m.id, phoneNumber = "+91900000000$idx", displayName = m.name)
        }
        groupRepo.createGroup(group, userEntities)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testCompleteProductionJourneyEndToEnd() = runBlocking {
        // Step 1: Set Budget with Base Budget and Buffer
        val plan = BudgetPlan(
            baseBudget = Money(3_000_000, inr), // 30,000 INR
            emergencyBuffer = Money(600_000, inr), // 6,000 INR
            startDateEpochMs = tripStartEpoch,
            endDateEpochMs = tripEndEpoch,
            categoryAllocations = mapOf(
                ExpenseCategory.ACCOMMODATION to Money(1_500_000, inr),
                ExpenseCategory.FOOD to Money(1_000_000, inr),
                ExpenseCategory.TRANSPORT to Money(500_000, inr)
            )
        )
        budgetRepo.setBudget(groupId, plan, userA.id)
        val persistedBudget = budgetRepo.getActiveBudget(groupId)
        assertNotNull(persistedBudget)
        assertEquals(3_000_000L, persistedBudget!!.baseBudget.amountMinor)
        assertEquals(600_000L, persistedBudget.emergencyBuffer.amountMinor)

        // Step 2: Add Expenses (HomeStay + Dinner)
        // Expense 1: Aarav pays 15,000 INR for HomeStay, split equally among all 3 (5,000 each)
        val expStay = Expense(
            id = "exp_stay_coorg",
            groupId = groupId,
            description = "Coffee Estate HomeStay",
            totalAmount = Money(1_500_000, inr),
            payers = listOf(ExpensePayer(userA.id, Money(1_500_000, inr))),
            participants = members.map { ExpenseParticipant(it.id) },
            splitMethod = SplitMethod.EQUAL,
            category = ExpenseCategory.ACCOMMODATION,
            timestamp = tripStartEpoch
        )
        expenseRepo.saveExpense(expStay, groupId, userA.id)

        // Expense 2: Bhavna pays 3,000 INR for Dinner, split equally (1,000 each)
        val expDinner = Expense(
            id = "exp_dinner_coorg",
            groupId = groupId,
            description = "Traditional Kodava Dinner",
            totalAmount = Money(300_000, inr),
            payers = listOf(ExpensePayer(userB.id, Money(300_000, inr))),
            participants = members.map { ExpenseParticipant(it.id) },
            splitMethod = SplitMethod.EQUAL,
            category = ExpenseCategory.FOOD,
            timestamp = tripStartEpoch + dayMillis
        )
        expenseRepo.saveExpense(expDinner, groupId, userB.id)

        // Step 3: Verify Authoritative Ledger Balances
        val expensesList = expenseRepo.getExpenses(groupId)
        assertEquals(2, expensesList.size)

        val balances = settlementRepo.calculateGroupBalances(groupId)
        assertEquals(3, balances.size)

        // Net sum of all balances must always be exactly zero
        val netSum = balances.sumOf { it.netBalance.amountMinor }
        assertEquals(0L, netSum)

        // Aarav paid 15,000, consumed 5,000 + 1,000 = 6,000 -> net +9,000 INR (+900,000 minor)
        val aaravBal = balances.first { it.memberId == userA.id }
        assertEquals(900_000L, aaravBal.netBalance.amountMinor)

        // Bhavna paid 3,000, consumed 5,000 + 1,000 = 6,000 -> net -3,000 INR (-300,000 minor)
        val bhavnaBal = balances.first { it.memberId == userB.id }
        assertEquals(-300_000L, bhavnaBal.netBalance.amountMinor)

        // Chirag paid 0, consumed 6,000 -> net -6,000 INR (-600,000 minor)
        val chiragBal = balances.first { it.memberId == userC.id }
        assertEquals(-600_000L, chiragBal.netBalance.amountMinor)

        // Step 4: Verify Settlement Plan Optimization
        val settlementPlan = SettlementEngine.calculateSettlement(balances, inr)
        assertFalse(settlementPlan.isSettled)
        assertEquals(2, settlementPlan.transfers.size)

        // Step 5: Test Non-destructive Payment Simulation
        val hypotheticalPayment = SettlementPayment(
            id = "sim_pay",
            fromMemberId = userB.id,
            toMemberId = userA.id,
            amount = Money(300_000, inr),
            groupId = groupId
        )
        val simResult = SimulationEngine.simulatePayment(
            members = members,
            currentExpenses = expensesList,
            currentPayments = emptyList(),
            candidatePayment = hypotheticalPayment,
            currency = inr
        )
        val simBhavnaNet = simResult.simulatedBalances.first { it.memberId == userB.id }.netBalance.amountMinor
        assertEquals(0L, simBhavnaNet) // Bhavna becomes settled in simulation

        // Authoritative payments must remain completely unchanged
        val realPaymentsBefore = settlementRepo.getPayments(groupId)
        assertTrue(realPaymentsBefore.isEmpty())

        // Step 6: Record Real Payment and Verify Balance Settlement
        settlementRepo.recordPayment(hypotheticalPayment, groupId, userB.id)
        val realPaymentsAfter = settlementRepo.getPayments(groupId)
        assertEquals(1, realPaymentsAfter.size)

        val updatedBalances = settlementRepo.calculateGroupBalances(groupId)
        val updatedBhavna = updatedBalances.first { it.memberId == userB.id }
        assertEquals(0L, updatedBhavna.netBalance.amountMinor) // Truly settled

        // Step 7: Verify Budget State (Total spent = 18,000 INR, base remaining = 12,000 INR, buffer UNUSED)
        val budgetState = BudgetEngine.calculateBudgetState(
            plan = persistedBudget,
            expenses = expenseRepo.getExpenses(groupId),
            currentEpochMs = tripStartEpoch + dayMillis
        )
        assertEquals(1_800_000L, budgetState.totalSpent.amountMinor)
        assertEquals(1_200_000L, budgetState.remainingBaseBudget.amountMinor)
        assertEquals(600_000L, budgetState.remainingBuffer.amountMinor)
        assertEquals(BufferState.UNUSED, budgetState.bufferState)
        assertFalse(budgetState.isBaseBudgetExceeded)

        // Step 8: Verify Daily Brief Facts for Day 2 (Dinner was on Day 2)
        val dailyFacts = DailySummaryEngine.calculateDailyFacts(
            plan = persistedBudget,
            expenses = expenseRepo.getExpenses(groupId),
            targetDateEpochMs = tripStartEpoch + dayMillis,
            currency = inr
        )
        assertEquals(300_000L, dailyFacts.totalSpentToday.amountMinor)
        assertEquals(1, dailyFacts.todayExpenses.size)
        assertEquals("Traditional Kodava Dinner", dailyFacts.todayExpenses[0].description)
        assertNotNull(dailyFacts.headline)
        assertTrue(dailyFacts.headline.isNotBlank())

        // Step 9: Reversal and Historical Isolation
        // Reverse Dinner expense
        expenseRepo.reverseExpense(
            id = expDinner.id,
            groupId = groupId,
            reason = "Duplicate entry entered by mistake",
            actorId = userB.id
        )
        val activeExpensesAfterReversal = expenseRepo.getExpenses(groupId)
        assertEquals(1, activeExpensesAfterReversal.size)
        assertEquals(expStay.id, activeExpensesAfterReversal[0].id)

        // Recalculated budget reflects reversed expense
        val budgetAfterReversal = BudgetEngine.calculateBudgetState(
            plan = persistedBudget,
            expenses = activeExpensesAfterReversal,
            currentEpochMs = tripStartEpoch + dayMillis
        )
        assertEquals(1_500_000L, budgetAfterReversal.totalSpent.amountMinor)
        assertEquals(1_500_000L, budgetAfterReversal.remainingBaseBudget.amountMinor)
    }
}
