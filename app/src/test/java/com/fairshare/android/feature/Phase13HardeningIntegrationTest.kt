package com.fairshare.android.feature

import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.intelligence.AnomalyDetectionEngine
import com.fairshare.android.core.domain.intelligence.AnomalyType
import com.fairshare.android.core.domain.intelligence.ExpenseBundlingEngine
import com.fairshare.android.core.domain.intelligence.FairShareMemoryEngine
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.domain.simulation.SimulationEngine
import com.fairshare.android.core.domain.vault.FairShareVaultEngine
import com.fairshare.android.core.domain.vault.VaultContribution
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase13HardeningIntegrationTest {

    private lateinit var db: FairShareDatabase
    private val inr = Currency("INR", "₹", 2)
    private val memberA = Member(id = "user_1", name = "Aarav", isCurrentUser = true)
    private val memberB = Member(id = "user_2", name = "Bhavna")
    private val memberC = Member(id = "user_3", name = "Chirag")
    private val members = listOf(memberA, memberB, memberC)
    private val groupId = "group_hardening_101"

    @Before
    fun setUp() = runBlocking {
        db = FairShareDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        db.userDao().insertUsers(
            listOf(
                UserEntity(id = memberA.id, phoneNumber = "+919876543210", displayName = memberA.name),
                UserEntity(id = memberB.id, phoneNumber = "+919876543211", displayName = memberB.name),
                UserEntity(id = memberC.id, phoneNumber = "+919876543212", displayName = memberC.name)
            )
        )
        db.groupDao().insertGroup(
            GroupEntity(
                id = groupId,
                name = "Production Hardening Group",
                createdById = memberA.id
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testVaultLedgerStateCalculation() {
        // Pool Contributions: Aarav contributes 5000, Bhavna contributes 5000
        val contributions = listOf(
            VaultContribution(
                id = UUID.randomUUID().toString(),
                memberId = memberA.id,
                amount = Money(500000L, inr)
            ),
            VaultContribution(
                id = UUID.randomUUID().toString(),
                memberId = memberB.id,
                amount = Money(500000L, inr)
            )
        )

        // Expense drawn from pool: ₹3,000 split equally among all 3 members (1000 each)
        val poolExpense = Expense(
            id = "exp_pool_1",
            groupId = groupId,
            description = "Resort Booking",
            totalAmount = Money(300000L, inr),
            payers = listOf(ExpensePayer(memberId = memberA.id, amount = Money(300000L, inr))),
            participants = members.map { ExpenseParticipant(memberId = it.id) },
            splitMethod = SplitMethod.EQUAL
        )

        val vaultState = FairShareVaultEngine.calculateVaultState(
            groupId = groupId,
            fundName = "Trip Vault",
            members = members,
            contributions = contributions,
            poolExpenses = listOf(poolExpense),
            currency = inr
        )

        // Verification
        assertEquals(1000000L, vaultState.totalContributed.amountMinor)
        assertEquals(300000L, vaultState.totalSpentFromPool.amountMinor)
        assertEquals(700000L, vaultState.remainingPoolBalance.amountMinor)

        // Member shares: Aarav contributed 5000, spent 1000 -> 4000 remaining
        val shareA = vaultState.memberShares.first { it.memberId == memberA.id }
        assertEquals(400000L, shareA.remainingShare.amountMinor)

        // Chirag contributed 0, spent 1000 -> -1000 remaining
        val shareC = vaultState.memberShares.first { it.memberId == memberC.id }
        assertEquals(-100000L, shareC.remainingShare.amountMinor)
    }

    @Test
    fun testWhatIfSimulationDoesNotMutateExpenses() {
        val originalExpense = Expense(
            id = "exp_orig_1",
            groupId = groupId,
            description = "Existing Dinner",
            totalAmount = Money(150000L, inr),
            payers = listOf(ExpensePayer(memberId = memberA.id, amount = Money(150000L, inr))),
            participants = listOf(memberA, memberB).map { ExpenseParticipant(memberId = it.id) },
            splitMethod = SplitMethod.EQUAL
        )
        val expensesList = listOf(originalExpense)
        val paymentsList = emptyList<SettlementPayment>()

        // Simulate hypothetical expense of 3000 paid by memberB split equally among 3 members
        val hypExpense = Expense(
            id = "hyp_1",
            groupId = groupId,
            description = "What-If Sightseeing",
            totalAmount = Money(300000L, inr),
            payers = listOf(ExpensePayer(memberId = memberB.id, amount = Money(300000L, inr))),
            participants = members.map { ExpenseParticipant(memberId = it.id) },
            splitMethod = SplitMethod.EQUAL
        )

        val simResult = SimulationEngine.simulateExpense(
            members = members,
            currentExpenses = expensesList,
            currentPayments = paymentsList,
            candidateExpense = hypExpense,
            currency = inr
        )

        // Original list and expense remain unaltered
        assertEquals(1, expensesList.size)
        assertEquals("Existing Dinner", expensesList[0].description)
        assertEquals(150000L, expensesList[0].totalAmount.amountMinor)

        // Simulation output produces deltas
        assertEquals(3, simResult.balanceDeltas.size)
        assertNotNull(simResult.balanceDeltas[memberB.id])
        assertTrue(simResult.balanceDeltas[memberB.id]!!.amountMinor > 0) // Bhavna paid, so her balance increases
    }

    @Test
    fun testIntelligenceEnginesExplainableAndNonMutating() {
        val pastExpenses = listOf(
            Expense(
                id = "p1",
                groupId = groupId,
                description = "Swiggy lunch",
                totalAmount = Money(50000L, inr),
                payers = listOf(ExpensePayer(memberA.id, Money(50000L, inr))),
                participants = listOf(ExpenseParticipant(memberA.id), ExpenseParticipant(memberB.id)),
                splitMethod = SplitMethod.EQUAL,
                category = ExpenseCategory.FOOD,
                timestamp = 1000L
            ),
            Expense(
                id = "p2",
                groupId = groupId,
                description = "Swiggy dinner",
                totalAmount = Money(60000L, inr),
                payers = listOf(ExpensePayer(memberA.id, Money(60000L, inr))),
                participants = listOf(ExpenseParticipant(memberA.id), ExpenseParticipant(memberB.id)),
                splitMethod = SplitMethod.EQUAL,
                category = ExpenseCategory.FOOD,
                timestamp = 2000L
            ),
            Expense(
                id = "p3",
                groupId = groupId,
                description = "Swiggy snacks",
                totalAmount = Money(45000L, inr),
                payers = listOf(ExpensePayer(memberA.id, Money(45000L, inr))),
                participants = listOf(ExpenseParticipant(memberA.id), ExpenseParticipant(memberB.id)),
                splitMethod = SplitMethod.EQUAL,
                category = ExpenseCategory.FOOD,
                timestamp = 3000L
            )
        )

        // Memory Engine detects recurring patterns
        val memorySuggestion = FairShareMemoryEngine.learnPatternForCategory(
            category = ExpenseCategory.FOOD,
            existingExpenses = pastExpenses,
            members = members
        )
        assertNotNull(memorySuggestion)
        assertEquals(memberA.id, memorySuggestion!!.suggestedPayerId)

        // Anomaly Detection: Outlier amount (e.g. 50,000 vs ~500)
        val outlierExpense = Expense(
            id = "outlier_1",
            groupId = groupId,
            description = "Expensive Dinner",
            totalAmount = Money(5000000L, inr),
            payers = listOf(ExpensePayer(memberA.id, Money(5000000L, inr))),
            participants = listOf(ExpenseParticipant(memberA.id)),
            splitMethod = SplitMethod.EQUAL,
            category = ExpenseCategory.FOOD
        )
        val anomalies = AnomalyDetectionEngine.detectAnomalies(
            targetExpense = outlierExpense,
            existingExpenses = pastExpenses
        )
        assertTrue(anomalies.isNotEmpty())
        assertEquals(AnomalyType.UNUSUALLY_LARGE, anomalies.first().type)
        assertTrue(anomalies.first().explanation.contains("significantly higher", ignoreCase = true))

        // Bundling Engine organizes into virtual collections without altering underlying records
        val bundle = ExpenseBundlingEngine.createBundle("Food Expenses", pastExpenses)
        assertEquals(3, bundle.expenseCount)
        assertEquals("Food Expenses", bundle.title)
    }
}
