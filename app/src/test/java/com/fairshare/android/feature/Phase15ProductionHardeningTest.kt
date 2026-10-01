package com.fairshare.android.feature

import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.domain.balance.BalanceEngine
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
import com.fairshare.android.core.domain.search.SearchEngine
import com.fairshare.android.core.domain.search.SearchFilter
import com.fairshare.android.core.domain.settlement.SettlementEngine
import com.fairshare.android.core.domain.simulation.SimulationEngine
import com.fairshare.android.core.domain.vault.FairShareVaultEngine
import com.fairshare.android.core.domain.vault.VaultContribution
import com.fairshare.android.core.network.backend.FairShareBackendService
import com.fairshare.android.core.network.backend.guard.AuthorizationException
import com.fairshare.android.core.network.backend.guard.ConflictException
import com.fairshare.android.core.network.backend.model.BackendExpense
import com.fairshare.android.core.network.backend.model.BackendExpenseParticipant
import com.fairshare.android.core.network.backend.model.BackendExpensePayer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase15ProductionHardeningTest {

    private lateinit var db: FairShareDatabase
    private lateinit var backendService: FairShareBackendService

    private val inr = Currency("INR", "₹", 2)
    private val memberA = Member(id = "user_alpha", name = "Aarav", isCurrentUser = true)
    private val memberB = Member(id = "user_beta", name = "Bhavna")
    private val memberC = Member(id = "user_gamma", name = "Chirag")
    private val members = listOf(memberA, memberB, memberC)
    private val groupId = "group_phase15_prod"

    @Before
    fun setUp() = runBlocking {
        db = FairShareDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        backendService = FairShareBackendService()

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
                name = "Phase 15 Production Group",
                createdById = memberA.id
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun loginUser(phone: String, name: String): com.fairshare.android.core.network.backend.AuthResult {
        backendService.requestPhoneVerification(phone)
        val gateway = backendService.verificationGateway as com.fairshare.android.core.network.backend.auth.RealSmsVerificationGateway
        val code = gateway.getCodeForTesting(phone)!!
        return backendService.verifyPhoneCode(phone, code, displayName = name)
    }

    // -------------------------------------------------------------
    // 1. Security & Backend Authorization Hardening
    // -------------------------------------------------------------

    @Test
    fun testBackendAuthorizationEnforcementForNonMembers() {
        val authA = loginUser("+919876543210", "Aarav")
        val sessionA = authA.session.token

        // User A creates a group
        val group = backendService.createGroup(sessionA, "Private Group", inr.code)

        // User B authenticates (not a member of User A's group)
        val authB = loginUser("+919876543211", "Bhavna")
        val sessionB = authB.session.token

        // User B tries to read messages or send message in User A's private group
        assertThrows(AuthorizationException::class.java) {
            backendService.sendMessage(sessionB, group.id, "Attempted intrusion")
        }

        assertThrows(AuthorizationException::class.java) {
            backendService.listMessages(sessionB, group.id)
        }
    }

    // -------------------------------------------------------------
    // 2. Financial Invariants & Minor Units Conservation
    // -------------------------------------------------------------

    @Test
    fun testFinancialConservationInvariantAcrossExpensesAndSettlements() {
        // Expense 1: Aarav pays ₹3,000 split equally among Aarav, Bhavna, Chirag (1000 each)
        val exp1 = Expense(
            id = "exp_prod_1",
            groupId = groupId,
            description = "Villa Stay",
            totalAmount = Money(300000L, inr),
            payers = listOf(ExpensePayer(memberA.id, Money(300000L, inr))),
            participants = members.map { ExpenseParticipant(it.id) },
            splitMethod = SplitMethod.EQUAL
        )

        // Expense 2: Bhavna pays ₹1,500 split equally among Aarav and Chirag (750 each)
        val exp2 = Expense(
            id = "exp_prod_2",
            groupId = groupId,
            description = "Groceries",
            totalAmount = Money(150000L, inr),
            payers = listOf(ExpensePayer(memberB.id, Money(150000L, inr))),
            participants = listOf(ExpenseParticipant(memberA.id), ExpenseParticipant(memberC.id)),
            splitMethod = SplitMethod.EQUAL
        )

        val expenses = listOf(exp1, exp2)
        val initialBalances = BalanceEngine.calculateGroupBalances(members, expenses, emptyList(), currency = inr)

        // Zero-sum invariant: Sum of all net balances in a group must ALWAYS equal exactly 0
        val sumNetBalances = initialBalances.sumOf { it.netBalance.amountMinor }
        assertEquals(0L, sumNetBalances)

        // Settlement: Calculate optimal debt transfers
        val settlementPlan = SettlementEngine.calculateSettlement(initialBalances, inr)
        assertTrue(settlementPlan.transfers.isNotEmpty())

        // Record a settlement payment: Chirag pays Aarav ₹1,000
        val payment = SettlementPayment(
            id = "pay_prod_1",
            groupId = groupId,
            fromMemberId = memberC.id,
            toMemberId = memberA.id,
            amount = Money(100000L, inr),
            mode = PaymentMode.UPI
        )

        val updatedBalances = BalanceEngine.calculateGroupBalances(members, expenses, listOf(payment), currency = inr)
        val updatedSum = updatedBalances.sumOf { it.netBalance.amountMinor }
        assertEquals(0L, updatedSum)

        // Chirag's debt must decrease by exactly 100000 minor units
        val chiragOld = initialBalances.first { it.memberId == memberC.id }.netBalance.amountMinor
        val chiragNew = updatedBalances.first { it.memberId == memberC.id }.netBalance.amountMinor
        assertEquals(chiragOld + 100000L, chiragNew)
    }

    // -------------------------------------------------------------
    // 3. Concurrency & Revision Conflict Guard
    // -------------------------------------------------------------

    @Test
    fun testConcurrentModificationRevisionConflictDetection() {
        val authA = loginUser("+919876543210", "Aarav")
        val sessionA = authA.session.token
        val group = backendService.createGroup(sessionA, "Conflict Test Group", inr.code)

        val exp = backendService.createExpense(
            sessionToken = sessionA,
            groupId = group.id,
            expense = BackendExpense(
                id = "exp_concurrent_1",
                groupId = group.id,
                createdById = authA.user.id,
                payerId = authA.user.id,
                amountMinor = 100000L,
                description = "Original Description",
                payers = listOf(BackendExpensePayer(authA.user.id, 100000L)),
                participants = listOf(BackendExpenseParticipant(authA.user.id, 100000L)),
                revision = 1L
            )
        )

        // First update advances revision to 2
        val updatedOnce = backendService.updateExpense(
            sessionToken = sessionA,
            groupId = group.id,
            expense = exp.copy(description = "Updated Once", revision = 1L)
        )
        assertEquals(2L, updatedOnce.revision)

        // Stale concurrent update based on old revision 1 must be rejected with ConflictException
        val staleExpense = exp.copy(description = "Stale Concurrent Update", revision = 1L)
        assertThrows(ConflictException::class.java) {
            backendService.updateExpense(sessionA, group.id, staleExpense)
        }
    }

    // -------------------------------------------------------------
    // 4. Large Dataset Performance & Search Isolation
    // -------------------------------------------------------------

    @Test
    fun testLargeDatasetPerformanceAndSearchIsolation() {
        // Generate 120 expenses across various categories
        val manyExpenses = (1..120).map { i ->
            val cat = when (i % 4) {
                0 -> ExpenseCategory.FOOD
                1 -> ExpenseCategory.TRANSPORT
                2 -> ExpenseCategory.ACCOMMODATION
                else -> ExpenseCategory.ACTIVITIES
            }
            Expense(
                id = "exp_perf_$i",
                groupId = groupId,
                description = "Expense item #$i",
                totalAmount = Money(i * 10000L, inr),
                payers = listOf(ExpensePayer(memberA.id, Money(i * 10000L, inr))),
                participants = members.map { ExpenseParticipant(it.id) },
                splitMethod = SplitMethod.EQUAL,
                category = cat,
                timestamp = 1000L + i
            )
        }

        val startTime = System.currentTimeMillis()
        val balances = BalanceEngine.calculateGroupBalances(members, manyExpenses, emptyList(), currency = inr)
        val settlement = SettlementEngine.calculateSettlement(balances, inr)
        val elapsedMs = System.currentTimeMillis() - startTime

        // Invariant: Calculation must be instantaneous (< 100ms)
        assertTrue("Balance calculation took ${elapsedMs}ms, expected < 100ms", elapsedMs < 100L)
        assertEquals(0L, balances.sumOf { it.netBalance.amountMinor })

        // Search engine performance & category isolation
        val searchFood = SearchEngine.executeSearch(
            filter = SearchFilter(category = ExpenseCategory.FOOD),
            expenses = manyExpenses,
            members = members
        )
        // 120 / 4 = 30 food items
        assertEquals(30, searchFood.size)
        assertTrue(searchFood.all { it.subtitle.startsWith("Food") })
    }

    // -------------------------------------------------------------
    // 5. What-If Simulation Non-Mutating & Vault Virtual Ledger
    // -------------------------------------------------------------

    @Test
    fun testWhatIfNonMutatingAndVaultPureAccounting() {
        val baseExpenses = listOf(
            Expense(
                id = "e1",
                groupId = groupId,
                description = "Lunch",
                totalAmount = Money(60000L, inr),
                payers = listOf(ExpensePayer(memberA.id, Money(60000L, inr))),
                participants = listOf(ExpenseParticipant(memberA.id), ExpenseParticipant(memberB.id)),
                splitMethod = SplitMethod.EQUAL
            )
        )

        val hypothetical = Expense(
            id = "hyp_e2",
            groupId = groupId,
            description = "Potential Safari Tour",
            totalAmount = Money(900000L, inr),
            payers = listOf(ExpensePayer(memberB.id, Money(900000L, inr))),
            participants = members.map { ExpenseParticipant(it.id) },
            splitMethod = SplitMethod.EQUAL
        )

        val sim = SimulationEngine.simulateExpense(
            members = members,
            currentExpenses = baseExpenses,
            currentPayments = emptyList(),
            candidateExpense = hypothetical,
            currency = inr
        )

        // Strict verification: Base expenses collection untouched
        assertEquals(1, baseExpenses.size)
        assertEquals("Lunch", baseExpenses[0].description)

        // Simulation balance deltas correctly formed
        assertNotNull(sim.balanceDeltas[memberB.id])
        // Bhavna paid 9,000 for 3 people (her share 3,000) -> her delta is +6,000 (+600000 minor)
        assertEquals(600000L, sim.balanceDeltas[memberB.id]!!.amountMinor)

        // Vault virtual ledger verification
        val vault = FairShareVaultEngine.calculateVaultState(
            groupId = groupId,
            fundName = "Safari Vault",
            members = members,
            contributions = listOf(
                VaultContribution("vc1", memberA.id, Money(500000L, inr)),
                VaultContribution("vc2", memberB.id, Money(500000L, inr)),
                VaultContribution("vc3", memberC.id, Money(500000L, inr))
            ),
            poolExpenses = baseExpenses,
            currency = inr
        )

        assertEquals(1500000L, vault.totalContributed.amountMinor)
        assertEquals(60000L, vault.totalSpentFromPool.amountMinor)
        assertEquals(1440000L, vault.remainingPoolBalance.amountMinor)
    }
}
