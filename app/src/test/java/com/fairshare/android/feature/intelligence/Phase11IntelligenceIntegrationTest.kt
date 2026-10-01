package com.fairshare.android.feature.intelligence

import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.repository.CandidateRepository
import com.fairshare.android.core.domain.budget.BudgetEngine
import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.intelligence.AnomalyAlert
import com.fairshare.android.core.domain.intelligence.AnomalyDetectionEngine
import com.fairshare.android.core.domain.intelligence.AnomalyType
import com.fairshare.android.core.domain.intelligence.ExpenseBundlingEngine
import com.fairshare.android.core.domain.intelligence.ExpenseOwnershipEngine
import com.fairshare.android.core.domain.intelligence.FairShareMemoryEngine
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.Group
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.domain.natural.NaturalLanguageExpenseParser
import com.fairshare.android.core.domain.receipt.ParsedReceipt
import com.fairshare.android.core.domain.receipt.ReceiptConfidenceState
import com.fairshare.android.core.domain.receipt.ReceiptParser
import com.fairshare.android.core.domain.simulation.SimulationEngine
import com.fairshare.android.core.domain.transactions.CandidateSource
import com.fairshare.android.core.domain.transactions.CandidateStatus
import com.fairshare.android.core.domain.transactions.TransactionCandidate
import com.fairshare.android.core.domain.transactions.TransactionParser
import com.fairshare.android.core.domain.vault.FairShareVaultEngine
import com.fairshare.android.core.domain.vault.VaultContribution
import com.fairshare.android.core.domain.vault.VaultLedgerState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase11IntelligenceIntegrationTest {

    private lateinit var db: FairShareDatabase
    private lateinit var candidateRepo: CandidateRepository

    private val inr = Currency("INR", "₹", 2)
    private val userA = Member(id = "user_a", name = "Aarav", isCurrentUser = true)
    private val userB = Member(id = "user_b", name = "Bhavna")
    private val userC = Member(id = "user_c", name = "Chirag")
    private val members = listOf(userA, userB, userC)

    @Before
    fun setUp() {
        db = FairShareDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        candidateRepo = CandidateRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------------------------------------------------------
    // 1. Receipt OCR & Parser Tests
    // ---------------------------------------------------------

    @Test
    fun testReceiptParser_extractsMerchantTotalsAndItems() {
        val receiptText = """
            ABC RESTAURANT
            Table 4, Order #102
            Date: 26/09/2026
            
            1x Margherita Pizza 600.00
            2x Cold Drinks 200.00
            1x Chocolate Dessert 300.00
            
            Subtotal 1100.00
            GST 99.00
            Service Charge 50.00
            Total 1249.00
        """.trimIndent()

        val parsed = ReceiptParser.parse(receiptText, inr)

        assertEquals("ABC RESTAURANT", parsed.merchant)
        assertEquals(ReceiptConfidenceState.DETECTED, parsed.merchantConfidence)
        assertEquals(124900L, parsed.totalAmount.amountMinor)
        assertEquals(110000L, parsed.subtotalAmount?.amountMinor)
        assertEquals(9900L, parsed.taxAmount?.amountMinor)
        assertEquals(5000L, parsed.serviceChargeAmount?.amountMinor)
        assertTrue(parsed.arithmeticValid)
        assertEquals(3, parsed.items.size)
        assertEquals("Margherita Pizza", parsed.items[0].name)
        assertEquals(60000L, parsed.items[0].amountMinor)
    }

    @Test
    fun testReceiptParser_detectsArithmeticMismatch() {
        val corruptedText = """
            CAFE MOCHA
            Subtotal 1000.00
            Tax 100.00
            Total 1500.00
        """.trimIndent()

        val parsed = ReceiptParser.parse(corruptedText, inr)

        assertFalse(parsed.arithmeticValid)
        assertTrue(parsed.validationNotes.isNotEmpty())
    }

    // ---------------------------------------------------------
    // 2. Transaction Detection & Parsing Tests
    // ---------------------------------------------------------

    @Test
    fun testTransactionParser_parsesUpiNotification() {
        val sms = "Paid Rs 850.00 to Swiggy via UPI Ref 928374 on 26-Sep"
        val candidate = TransactionParser.parse(sms, CandidateSource.NOTIFICATION, inr)

        assertNotNull(candidate)
        assertEquals(85000L, candidate!!.amount.amountMinor)
        assertEquals("Swiggy", candidate.detectedMerchant)
        assertEquals(ExpenseCategory.FOOD, candidate.inferredCategory)
        assertEquals(PaymentMode.UPI, candidate.inferredMode)
        assertEquals(CandidateStatus.PENDING, candidate.status)
    }

    @Test
    fun testTransactionParser_parsesBankDebitText() {
        val bankText = "Your a/c ending 4567 is debited by INR 1,200.00 at Uber on 26-Sep-2026."
        val candidate = TransactionParser.parse(bankText, CandidateSource.SMS_PASTED, inr)

        assertNotNull(candidate)
        assertEquals(120000L, candidate!!.amount.amountMinor)
        assertEquals("Uber", candidate.detectedMerchant)
        assertEquals(ExpenseCategory.TRANSPORT, candidate.inferredCategory)
    }

    @Test
    fun testCandidateRepository_duplicateDetectionAndStatusUpdates() = runBlocking {
        val candidate = TransactionCandidate(
            id = "cand_1",
            groupId = "group_1",
            rawSource = CandidateSource.NOTIFICATION,
            rawText = "Paid Rs 500 to Uber",
            amount = Money(50000L, inr),
            detectedMerchant = "Uber",
            detectedTimestamp = System.currentTimeMillis()
        )

        candidateRepo.saveCandidate(candidate)
        val pending = candidateRepo.getPendingCandidates()
        assertEquals(1, pending.size)

        // Existing expense with same merchant & amount
        val existingExpenses = listOf(
            Expense(
                id = "exp_1",
                description = "Uber cab ride",
                totalAmount = Money(50000L, inr),
                payers = listOf(ExpensePayer(userA.id, Money(50000L, inr))),
                participants = listOf(ExpenseParticipant(userA.id)),
                timestamp = System.currentTimeMillis() - 1000L
            )
        )

        val (isDup, reason) = candidateRepo.checkDuplicate(candidate, existingExpenses)
        assertTrue(isDup)
        assertNotNull(reason)

        // Update candidate status to CONFIRMED
        candidateRepo.updateCandidateStatus(candidate.id, CandidateStatus.CONFIRMED)
        val pendingAfterConfirm = candidateRepo.getPendingCandidates()
        assertEquals(0, pendingAfterConfirm.size)
    }

    // ---------------------------------------------------------
    // 3. Anomaly Detection Tests
    // ---------------------------------------------------------

    @Test
    fun testAnomalyDetection_flagsUnusuallyLargeExpenseWithNeutralTone() {
        val normalExpenses = listOf(
            Expense(id = "e1", description = "Lunch", totalAmount = Money(40000L, inr), payers = listOf(ExpensePayer(userA.id, Money(40000L, inr))), participants = emptyList()),
            Expense(id = "e2", description = "Tea", totalAmount = Money(20000L, inr), payers = listOf(ExpensePayer(userA.id, Money(20000L, inr))), participants = emptyList()),
            Expense(id = "e3", description = "Snacks", totalAmount = Money(30000L, inr), payers = listOf(ExpensePayer(userA.id, Money(30000L, inr))), participants = emptyList())
        )

        val spikeExpense = Expense(
            id = "e_spike",
            description = "Dinner Banquet",
            totalAmount = Money(400000L, inr), // ₹4,000 vs avg ₹300
            payers = listOf(ExpensePayer(userA.id, Money(400000L, inr))),
            participants = emptyList(),
            category = ExpenseCategory.FOOD
        )

        val alerts = AnomalyDetectionEngine.detectAnomalies(spikeExpense, normalExpenses)
        assertTrue(alerts.isNotEmpty())
        val largeAlert = alerts.firstOrNull { it.type == AnomalyType.UNUSUALLY_LARGE }
        assertNotNull(largeAlert)
        assertEquals("Higher than usual", largeAlert!!.title)
        assertFalse(largeAlert.explanation.contains("fraud", ignoreCase = true))
    }

    @Test
    fun testAnomalyDetection_flagsDuplicateExpense() {
        val now = System.currentTimeMillis()
        val first = Expense(id = "e1", description = "Cafe Coffee Day", totalAmount = Money(45000L, inr), payers = listOf(ExpensePayer(userA.id, Money(45000L, inr))), participants = emptyList(), timestamp = now - 60000L)
        val second = Expense(id = "e2", description = "Cafe Coffee Day", totalAmount = Money(45000L, inr), payers = listOf(ExpensePayer(userA.id, Money(45000L, inr))), participants = emptyList(), timestamp = now)

        val alerts = AnomalyDetectionEngine.detectAnomalies(second, listOf(first))
        val dupAlert = alerts.firstOrNull { it.type == AnomalyType.POSSIBLE_DUPLICATE }
        assertNotNull(dupAlert)
        assertEquals("Possible duplicate", dupAlert!!.title)
    }

    // ---------------------------------------------------------
    // 4. FairShare Memory Tests
    // ---------------------------------------------------------

    @Test
    fun testFairShareMemory_learnsPayerAndSplitForCategory() {
        val historical = listOf(
            Expense(id = "e1", description = "Fuel 1", totalAmount = Money(200000L, inr), payers = listOf(ExpensePayer(userB.id, Money(200000L, inr))), participants = listOf(ExpenseParticipant(userA.id), ExpenseParticipant(userB.id), ExpenseParticipant(userC.id, excluded = true)), splitMethod = SplitMethod.EQUAL, category = ExpenseCategory.FUEL),
            Expense(id = "e2", description = "Fuel 2", totalAmount = Money(150000L, inr), payers = listOf(ExpensePayer(userB.id, Money(150000L, inr))), participants = listOf(ExpenseParticipant(userA.id), ExpenseParticipant(userB.id), ExpenseParticipant(userC.id, excluded = true)), splitMethod = SplitMethod.EQUAL, category = ExpenseCategory.FUEL)
        )

        val suggestion = FairShareMemoryEngine.learnPatternForCategory(ExpenseCategory.FUEL, historical, members)
        assertNotNull(suggestion)
        assertEquals(userB.id, suggestion!!.suggestedPayerId)
        assertEquals("Bhavna", suggestion.suggestedPayerName)
        assertEquals(SplitMethod.EQUAL, suggestion.suggestedSplitMethod)
        assertTrue(suggestion.excludedMemberIds.contains(userC.id))
        assertTrue(suggestion.explanation.contains("Bhavna usually pays"))
    }

    // ---------------------------------------------------------
    // 5. Expense Bundling Tests
    // ---------------------------------------------------------

    @Test
    fun testExpenseBundling_suggestsActivityBundleWithoutModifyingUnderlyingExpenses() {
        val now = System.currentTimeMillis()
        val dinner = Expense(id = "e_din", description = "Dinner", totalAmount = Money(120000L, inr), payers = listOf(ExpensePayer(userA.id, Money(120000L, inr))), participants = emptyList(), category = ExpenseCategory.FOOD, timestamp = now)
        val taxi = Expense(id = "e_cab", description = "Uber", totalAmount = Money(35000L, inr), payers = listOf(ExpensePayer(userA.id, Money(35000L, inr))), participants = emptyList(), category = ExpenseCategory.TRANSPORT, timestamp = now + (30 * 60 * 1000L))

        val bundles = ExpenseBundlingEngine.suggestBundles(listOf(dinner, taxi))
        assertEquals(1, bundles.size)
        assertEquals(155000L, bundles[0].totalAmount.amountMinor)
        assertEquals(2, bundles[0].expenseCount)
        assertTrue(bundles[0].expenseIds.contains(dinner.id))
        assertTrue(bundles[0].expenseIds.contains(taxi.id))

        // Underlying expenses are unchanged
        assertEquals(120000L, dinner.totalAmount.amountMinor)
        assertEquals(35000L, taxi.totalAmount.amountMinor)
    }

    // ---------------------------------------------------------
    // 6. Expense Ownership Tests
    // ---------------------------------------------------------

    @Test
    fun testExpenseOwnership_calculatesContributionVsConsumption() {
        // Aarav pays ₹1,500 for Aarav, Bhavna, Chirag equally (₹500 each)
        val expense = Expense(
            id = "exp_own",
            description = "Dinner",
            totalAmount = Money(150000L, inr),
            payers = listOf(ExpensePayer(userA.id, Money(150000L, inr))),
            participants = listOf(
                ExpenseParticipant(userA.id),
                ExpenseParticipant(userB.id),
                ExpenseParticipant(userC.id)
            ),
            splitMethod = SplitMethod.EQUAL
        )

        val breakdown = ExpenseOwnershipEngine.computeExpenseOwnership(expense, members, inr)
        assertEquals(1, breakdown.payers.size)
        assertEquals("Aarav", breakdown.payers[0].first)
        assertEquals(150000L, breakdown.payers[0].second.amountMinor)

        val aaravOwnership = breakdown.memberOwnerships.first { it.memberId == userA.id }
        assertEquals(150000L, aaravOwnership.paidAmount.amountMinor)
        assertEquals(50000L, aaravOwnership.consumedAmount.amountMinor)
        assertEquals(100000L, aaravOwnership.netPosition.amountMinor) // +₹1,000

        val bhavnaOwnership = breakdown.memberOwnerships.first { it.memberId == userB.id }
        assertEquals(0L, bhavnaOwnership.paidAmount.amountMinor)
        assertEquals(50000L, bhavnaOwnership.consumedAmount.amountMinor)
        assertEquals(-50000L, bhavnaOwnership.netPosition.amountMinor) // -₹500

        // Zero-sum invariant: sum of net positions across participants equals zero
        val sumNet = breakdown.memberOwnerships.sumOf { it.netPosition.amountMinor }
        assertEquals(0L, sumNet)
    }

    // ---------------------------------------------------------
    // 7. What-If Simulator Tests
    // ---------------------------------------------------------

    @Test
    fun testWhatIfSimulator_computesHypotheticalBudgetAndBalancesWithoutMutation() {
        val currentExpenses = listOf(
            Expense(id = "e1", description = "Taxi", totalAmount = Money(100000L, inr), payers = listOf(ExpensePayer(userA.id, Money(100000L, inr))), participants = listOf(ExpenseParticipant(userA.id), ExpenseParticipant(userB.id)))
        )

        val budgetPlan = BudgetPlan(
            id = "group_sim",
            baseBudget = Money(1000000L, inr), // ₹10,000
            emergencyBuffer = Money(200000L, inr), // ₹2,000
            startDateEpochMs = System.currentTimeMillis() - 86400000L,
            endDateEpochMs = System.currentTimeMillis() + (4 * 86400000L)
        )

        val candidateExpense = Expense(
            id = "hypo_hotel",
            description = "Hotel Booking",
            totalAmount = Money(500000L, inr),
            payers = listOf(ExpensePayer(userA.id, Money(500000L, inr))),
            participants = listOf(ExpenseParticipant(userA.id), ExpenseParticipant(userB.id), ExpenseParticipant(userC.id)),
            splitMethod = SplitMethod.EQUAL
        )

        val outcome = SimulationEngine.simulateWhatIfExpense(
            members = members,
            currentExpenses = currentExpenses,
            currentPayments = emptyList(),
            candidateExpense = candidateExpense,
            budgetPlan = budgetPlan,
            currency = inr
        )

        // Previous remaining base: 10,000 - 1,000 = 9,000
        assertEquals(900000L, outcome.budgetResult?.previousRemainingBase?.amountMinor)
        // Simulated remaining base: 10,000 - 6,000 = 4,000
        assertEquals(400000L, outcome.budgetResult?.simulatedRemainingBase?.amountMinor)

        // Verifying NO MUTATION invariant: currentExpenses still has size 1
        assertEquals(1, currentExpenses.size)
    }

    // ---------------------------------------------------------
    // 8. FairShare Vault Tests
    // ---------------------------------------------------------

    @Test
    fun testFairShareVault_tracksContributionsAndAllocatedSpending() {
        val contributions = listOf(
            VaultContribution(id = "c1", memberId = userA.id, amount = Money(1000000L, inr)), // Aarav ₹10,000
            VaultContribution(id = "c2", memberId = userB.id, amount = Money(1000000L, inr))  // Bhavna ₹10,000
        )

        // Expenses paid from pool: ₹6,000 split equally among A, B, C (₹2,000 each)
        val poolExpenses = listOf(
            Expense(
                id = "pool_exp_1",
                description = "Group Dinner",
                totalAmount = Money(600000L, inr),
                payers = listOf(ExpensePayer(userA.id, Money(600000L, inr))), // recorded payer
                participants = listOf(ExpenseParticipant(userA.id), ExpenseParticipant(userB.id), ExpenseParticipant(userC.id)),
                splitMethod = SplitMethod.EQUAL
            )
        )

        val vaultState = FairShareVaultEngine.calculateVaultState(
            groupId = "group_v",
            fundName = "Goa Trip Fund",
            members = members,
            contributions = contributions,
            poolExpenses = poolExpenses,
            currency = inr
        )

        assertEquals(2000000L, vaultState.totalContributed.amountMinor)
        assertEquals(600000L, vaultState.totalSpentFromPool.amountMinor)
        assertEquals(1400000L, vaultState.remainingPoolBalance.amountMinor) // ₹14,000 remaining

        val aaravShare = vaultState.memberShares.first { it.memberId == userA.id }
        assertEquals(1000000L, aaravShare.totalContributed.amountMinor)
        assertEquals(200000L, aaravShare.allocatedSpent.amountMinor)
        assertEquals(800000L, aaravShare.remainingShare.amountMinor) // ₹8,000 remaining

        // Disclaimer check
        assertTrue(VaultLedgerState.DISCLAIMER.contains("Virtual group accounting ledger"))
    }

    // ---------------------------------------------------------
    // 9. Natural Language Expense Parser Tests
    // ---------------------------------------------------------

    @Test
    fun testNaturalLanguageParser_parsesTextWithParticipantsAndCategory() {
        val input = "paid 1200 for dinner with Bhavna and Chirag"
        val draft = NaturalLanguageExpenseParser.parse(input, members, userA, inr)

        assertEquals(120000L, draft.amount.amountMinor)
        assertEquals(userA.id, draft.payerId)
        assertEquals(ExpenseCategory.FOOD, draft.category)
        assertTrue(draft.participantIds.contains(userB.id))
        assertTrue(draft.participantIds.contains(userC.id))
        assertTrue(draft.participantIds.contains(userA.id))
    }

    @Test
    fun testNaturalLanguageParser_parsesThirdPersonPayerAndEveryone() {
        val input = "Bhavna paid 3500 for hotel for everyone"
        val draft = NaturalLanguageExpenseParser.parse(input, members, userA, inr)

        assertEquals(350000L, draft.amount.amountMinor)
        assertEquals(userB.id, draft.payerId)
        assertEquals(ExpenseCategory.ACCOMMODATION, draft.category)
        assertEquals(3, draft.participantIds.size)
    }
}
