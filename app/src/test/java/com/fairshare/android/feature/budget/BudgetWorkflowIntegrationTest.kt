package com.fairshare.android.feature.budget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.database.repository.BudgetRepository
import com.fairshare.android.core.database.repository.ExpenseRepository
import com.fairshare.android.core.database.repository.SettlementRepository
import com.fairshare.android.core.domain.budget.BudgetEngine
import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.budget.BufferState
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.domain.summary.DailySummaryEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.max
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BudgetWorkflowIntegrationTest {

    private lateinit var db: FairShareDatabase
    private lateinit var budgetRepo: BudgetRepository
    private lateinit var expenseRepo: ExpenseRepository
    private lateinit var settlementRepo: SettlementRepository

    private val groupId = "group_budget_test_1"
    private val actorId = "user_host_1"
    private val inr = Currency("INR", "₹", 2)

    private val startDateEpoch = 1774000000000L // Day 1
    private val dayMillis = 86_400_000L
    private val endDateEpoch = startDateEpoch + (5 * dayMillis) // 5-day trip

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = FairShareDatabase.createInMemory(context)
        budgetRepo = BudgetRepository(db)
        expenseRepo = ExpenseRepository(db)
        settlementRepo = SettlementRepository(db)

        db.groupDao().insertGroup(
            GroupEntity(
                id = groupId,
                name = "Manali Adventure",
                createdById = actorId,
                currency = "INR",
                createdAt = startDateEpoch,
                isArchived = false
            )
        )
        db.userDao().insertUser(
            UserEntity(
                id = actorId,
                phoneNumber = "+919876543210",
                displayName = "Host User"
            )
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testBudgetCreationAndBufferStateTransitions() = runBlocking {
        // Base budget = 50,000 INR (5,000,000 paise)
        // Emergency buffer = 10,000 INR (1,000,000 paise)
        // Total envelope = 60,000 INR (6,000,000 paise)
        val initialPlan = BudgetPlan(
            baseBudget = Money(5_000_000, inr),
            emergencyBuffer = Money(1_000_000, inr),
            startDateEpochMs = startDateEpoch,
            endDateEpochMs = endDateEpoch,
            categoryAllocations = mapOf(
                ExpenseCategory.FOOD to Money(2_000_000, inr),
                ExpenseCategory.TRANSPORT to Money(1_500_000, inr),
                ExpenseCategory.ACCOMMODATION to Money(1_500_000, inr)
            ),
            alertThresholdPercentage = 80
        )

        budgetRepo.setBudget(groupId, initialPlan, actorId)
        val retrievedPlan = budgetRepo.getActiveBudget(groupId)
        assertNotNull(retrievedPlan)
        assertEquals(5_000_000L, retrievedPlan!!.baseBudget.amountMinor)
        assertEquals(1_000_000L, retrievedPlan.emergencyBuffer.amountMinor)
        assertEquals(3, retrievedPlan.categoryAllocations.size)

        // 1. Initial State: No expenses
        val stateInitial = BudgetEngine.calculateBudgetState(
            plan = retrievedPlan,
            expenses = emptyList(),
            currentEpochMs = startDateEpoch
        )
        assertEquals(5_000_000L, stateInitial.remainingBaseBudget.amountMinor)
        assertEquals(1_000_000L, stateInitial.remainingBuffer.amountMinor)
        assertEquals(BufferState.UNUSED, stateInitial.bufferState)
        assertFalse(stateInitial.isBaseBudgetExceeded)
        assertFalse(stateInitial.isBufferExhausted)

        val velocityInitial = BudgetEngine.calculateSpendingVelocity(
            plan = retrievedPlan,
            totalSpent = stateInitial.totalSpent,
            currentEpochMs = startDateEpoch
        )
        assertEquals(5, velocityInitial.totalDays)
        assertEquals(1, velocityInitial.elapsedDays)
        assertEquals(1_000_000L, velocityInitial.plannedDailyRate.amountMinor) // 50,000 / 5 days = 10,000/day

        // 2. Add expense within base budget (30,000 INR)
        val exp1 = Expense(
            id = "exp_1",
            groupId = groupId,
            description = "Resort Booking",
            totalAmount = Money(3_000_000, inr),
            category = ExpenseCategory.ACCOMMODATION,
            timestamp = startDateEpoch,
            payers = listOf(ExpensePayer(actorId, Money(3_000_000, inr))),
            participants = listOf(ExpenseParticipant(actorId)),
            splitMethod = SplitMethod.EQUAL
        )
        expenseRepo.saveExpense(exp1, groupId, actorId)

        val stateWithinBase = BudgetEngine.calculateBudgetState(
            plan = retrievedPlan,
            expenses = listOf(exp1),
            currentEpochMs = startDateEpoch
        )
        assertEquals(2_000_000L, stateWithinBase.remainingBaseBudget.amountMinor)
        assertEquals(1_000_000L, stateWithinBase.remainingBuffer.amountMinor)
        assertEquals(BufferState.UNUSED, stateWithinBase.bufferState)
        assertFalse(stateWithinBase.isBaseBudgetExceeded)

        // 3. Add expense that crosses base budget and activates buffer (25,000 INR -> total 55,000 INR)
        // Base is 50,000, so 5,000 into 10,000 buffer (50% buffer consumed -> ACTIVE)
        val exp2 = Expense(
            id = "exp_2",
            groupId = groupId,
            description = "Luxury Dinner",
            totalAmount = Money(2_500_000, inr),
            category = ExpenseCategory.FOOD,
            timestamp = startDateEpoch + dayMillis,
            payers = listOf(ExpensePayer(actorId, Money(2_500_000, inr))),
            participants = listOf(ExpenseParticipant(actorId)),
            splitMethod = SplitMethod.EQUAL
        )
        expenseRepo.saveExpense(exp2, groupId, actorId)

        val stateBufferActive = BudgetEngine.calculateBudgetState(
            plan = retrievedPlan,
            expenses = listOf(exp1, exp2),
            currentEpochMs = startDateEpoch + dayMillis
        )
        assertEquals(0L, stateBufferActive.remainingBaseBudget.amountMinor)
        assertEquals(500_000L, stateBufferActive.remainingBuffer.amountMinor) // 5,000 INR remaining in buffer
        assertEquals(BufferState.ACTIVE, stateBufferActive.bufferState)
        assertTrue(stateBufferActive.isBaseBudgetExceeded)
        assertFalse(stateBufferActive.isBufferExhausted)

        // 4. Add expense pushing buffer to >= 80% used (4,000 INR -> total 59,000 INR, 90% buffer used -> NEAR_LIMIT)
        val exp3 = Expense(
            id = "exp_3",
            groupId = groupId,
            description = "Cab to Solang",
            totalAmount = Money(400_000, inr),
            category = ExpenseCategory.TRANSPORT,
            timestamp = startDateEpoch + (2 * dayMillis),
            payers = listOf(ExpensePayer(actorId, Money(400_000, inr))),
            participants = listOf(ExpenseParticipant(actorId)),
            splitMethod = SplitMethod.EQUAL
        )
        expenseRepo.saveExpense(exp3, groupId, actorId)

        val stateNearLimit = BudgetEngine.calculateBudgetState(
            plan = retrievedPlan,
            expenses = listOf(exp1, exp2, exp3),
            currentEpochMs = startDateEpoch + (2 * dayMillis)
        )
        assertEquals(0L, stateNearLimit.remainingBaseBudget.amountMinor)
        assertEquals(100_000L, stateNearLimit.remainingBuffer.amountMinor) // 1,000 INR remaining in buffer
        assertEquals(BufferState.NEAR_LIMIT, stateNearLimit.bufferState)
        assertFalse(stateNearLimit.isBufferExhausted)

        // 5. Add expense exceeding total envelope (2,000 INR -> total 61,000 INR -> EXHAUSTED and isBufferExhausted)
        val exp4 = Expense(
            id = "exp_4",
            groupId = groupId,
            description = "Souvenirs",
            totalAmount = Money(200_000, inr),
            category = ExpenseCategory.SHOPPING,
            timestamp = startDateEpoch + (3 * dayMillis),
            payers = listOf(ExpensePayer(actorId, Money(200_000, inr))),
            participants = listOf(ExpenseParticipant(actorId)),
            splitMethod = SplitMethod.EQUAL
        )
        expenseRepo.saveExpense(exp4, groupId, actorId)

        val stateExhausted = BudgetEngine.calculateBudgetState(
            plan = retrievedPlan,
            expenses = listOf(exp1, exp2, exp3, exp4),
            currentEpochMs = startDateEpoch + (3 * dayMillis)
        )
        assertEquals(0L, stateExhausted.remainingBaseBudget.amountMinor)
        assertEquals(0L, stateExhausted.remainingBuffer.amountMinor)
        assertEquals(BufferState.EXHAUSTED, stateExhausted.bufferState)
        assertTrue(stateExhausted.isBufferExhausted)
    }

    @Test
    fun testDailySummaryFactsAndTomorrowRecommendation() = runBlocking {
        val plan = BudgetPlan(
            baseBudget = Money(5_000_000, inr), // 50,000 INR
            emergencyBuffer = Money(1_000_000, inr), // 10,000 INR
            startDateEpochMs = startDateEpoch,
            endDateEpochMs = endDateEpoch
        )
        budgetRepo.setBudget(groupId, plan, actorId)

        // Day 2 (startDate + 1 day)
        val day2Epoch = startDateEpoch + dayMillis

        // Add 2 expenses on Day 2
        val lunch = Expense(
            id = "exp_lunch",
            groupId = groupId,
            description = "Mountain Cafe Lunch",
            totalAmount = Money(400_000, inr), // 4,000 INR
            category = ExpenseCategory.FOOD,
            timestamp = day2Epoch + 3600_000L,
            payers = listOf(ExpensePayer(actorId, Money(400_000, inr))),
            participants = listOf(ExpenseParticipant(actorId)),
            splitMethod = SplitMethod.EQUAL
        )
        val fuel = Expense(
            id = "exp_fuel",
            groupId = groupId,
            description = "SUV Diesel",
            totalAmount = Money(200_000, inr), // 2,000 INR
            category = ExpenseCategory.TRANSPORT,
            timestamp = day2Epoch + 7200_000L,
            payers = listOf(ExpensePayer(actorId, Money(200_000, inr))),
            participants = listOf(ExpenseParticipant(actorId)),
            splitMethod = SplitMethod.EQUAL
        )
        expenseRepo.saveExpense(lunch, groupId, actorId)
        expenseRepo.saveExpense(fuel, groupId, actorId)

        val expenses: List<Expense> = listOf(lunch, fuel)
        val dailyFacts = DailySummaryEngine.calculateDailyFacts(
            plan = plan,
            expenses = expenses,
            targetDateEpochMs = day2Epoch,
            currency = inr
        )

        // Today's total = 6,000 INR (600,000 minor)
        assertEquals(600_000L, dailyFacts.totalSpentToday.amountMinor)
        assertEquals(2, dailyFacts.todayExpenses.size)
        assertEquals(2, dailyFacts.categoryBreakdown.size)
        assertEquals(400_000L, dailyFacts.categoryBreakdown[ExpenseCategory.FOOD]?.amountMinor)
        assertEquals(200_000L, dailyFacts.categoryBreakdown[ExpenseCategory.TRANSPORT]?.amountMinor)

        // Daily target is 10,000 INR (1,000,000 paise) for a 5-day trip (50,000 / 5)
        // Today spent 6,000 INR, so differenceFromPlanned is -4,000 INR (under budget by 4,000)
        assertNotNull(dailyFacts.differenceFromPlanned)
        assertEquals(-400_000L, dailyFacts.differenceFromPlanned!!.amountMinor)
        assertFalse(dailyFacts.isOverPace)

        // Tomorrow's suggested limit is computed deterministically
        assertNotNull(dailyFacts.suggestedTomorrowLimit)
        assertTrue(dailyFacts.suggestedTomorrowLimit!!.amountMinor > 0L)
    }

    @Test
    fun testSettlementPaymentsDoNotAffectBudgetOrDailyBrief() = runBlocking {
        val plan = BudgetPlan(
            baseBudget = Money(2_000_000, inr),
            emergencyBuffer = Money(500_000, inr),
            startDateEpochMs = startDateEpoch,
            endDateEpochMs = endDateEpoch
        )
        budgetRepo.setBudget(groupId, plan, actorId)

        // Record a settlement payment of 5,000 INR
        settlementRepo.recordPayment(
            payment = SettlementPayment(
                id = "settle_1",
                fromMemberId = actorId,
                toMemberId = "user_recipient_2",
                amount = Money(500_000, inr),
                groupId = groupId
            ),
            groupId = groupId,
            actorId = actorId
        )

        // Load active budget and compute state with only group expenses
        val currentExpenses = expenseRepo.getExpenses(groupId)
        val budgetState = BudgetEngine.calculateBudgetState(
            plan = plan,
            expenses = currentExpenses,
            currentEpochMs = startDateEpoch
        )

        // Settlement must NOT be counted as an expense!
        assertEquals(0L, budgetState.totalSpent.amountMinor)
        assertEquals(2_000_000L, budgetState.remainingBaseBudget.amountMinor)
        assertEquals(BufferState.UNUSED, budgetState.bufferState)

        val dailySummary = DailySummaryEngine.calculateDailyFacts(
            plan = plan,
            expenses = currentExpenses,
            targetDateEpochMs = startDateEpoch,
            currency = inr
        )
        assertEquals(0L, dailySummary.totalSpentToday.amountMinor)
        assertTrue(dailySummary.todayExpenses.isEmpty())
    }

    @Test
    fun testEditingBudgetDoesNotAlterHistoricalExpenses() = runBlocking {
        val originalPlan = BudgetPlan(
            baseBudget = Money(3_000_000, inr),
            emergencyBuffer = Money(500_000, inr),
            startDateEpochMs = startDateEpoch,
            endDateEpochMs = endDateEpoch
        )
        budgetRepo.setBudget(groupId, originalPlan, actorId)

        val exp = Expense(
            id = "exp_stay",
            groupId = groupId,
            description = "Hotel Stay",
            totalAmount = Money(1_000_000, inr),
            category = ExpenseCategory.ACCOMMODATION,
            timestamp = startDateEpoch,
            payers = listOf(ExpensePayer(actorId, Money(1_000_000, inr))),
            participants = listOf(ExpenseParticipant(actorId)),
            splitMethod = SplitMethod.EQUAL
        )
        expenseRepo.saveExpense(exp, groupId, actorId)

        // Verify expense is recorded
        val expensesBefore = expenseRepo.getExpenses(groupId)
        assertEquals(1, expensesBefore.size)
        assertEquals(1_000_000L, expensesBefore[0].totalAmount.amountMinor)

        // Edit budget: Increase base budget to 5,000,000 minor units
        val updatedPlan = originalPlan.copy(
            baseBudget = Money(5_000_000, inr),
            emergencyBuffer = Money(1_500_000, inr)
        )
        budgetRepo.setBudget(groupId, updatedPlan, actorId)

        // Verify expenses are completely unchanged
        val expensesAfter = expenseRepo.getExpenses(groupId)
        assertEquals(1, expensesAfter.size)
        assertEquals("exp_stay", expensesAfter[0].id)
        assertEquals(1_000_000L, expensesAfter[0].totalAmount.amountMinor)

        // Recalculated state reflects new base budget
        val stateAfter = BudgetEngine.calculateBudgetState(
            plan = updatedPlan,
            expenses = expensesAfter,
            currentEpochMs = startDateEpoch
        )
        assertEquals(1_000_000L, stateAfter.totalSpent.amountMinor)
        assertEquals(4_000_000L, stateAfter.remainingBaseBudget.amountMinor) // 50,000 - 10,000 = 40,000
        assertEquals(1_500_000L, stateAfter.remainingBuffer.amountMinor)
    }

    @Test
    fun testZeroAndExpiredTripEdgeCases() {
        // Same start and end date (1-day trip)
        val singleDayPlan = BudgetPlan(
            baseBudget = Money(1_000_000, inr),
            emergencyBuffer = Money(200_000, inr),
            startDateEpochMs = startDateEpoch,
            endDateEpochMs = startDateEpoch
        )
        val state1 = BudgetEngine.calculateBudgetState(
            plan = singleDayPlan,
            expenses = emptyList(),
            currentEpochMs = startDateEpoch
        )
        val velocity1 = BudgetEngine.calculateSpendingVelocity(
            plan = singleDayPlan,
            totalSpent = state1.totalSpent,
            currentEpochMs = startDateEpoch
        )
        assertEquals(1, velocity1.totalDays)
        assertEquals(1_000_000L, velocity1.plannedDailyRate.amountMinor)

        val target1 = BudgetEngine.calculateDailyTarget(state1, max(1, velocity1.remainingDays))
        assertEquals(1_000_000L, target1.amountMinor)

        // Expired trip (currentEpochMs > endDateEpochMs)
        val expiredVelocity = BudgetEngine.calculateSpendingVelocity(
            plan = singleDayPlan,
            totalSpent = state1.totalSpent,
            currentEpochMs = startDateEpoch + (10 * dayMillis)
        )
        assertEquals(1, expiredVelocity.totalDays)
        assertEquals(0, expiredVelocity.remainingDays)
        assertEquals(BufferState.UNUSED, state1.bufferState)
    }
}
