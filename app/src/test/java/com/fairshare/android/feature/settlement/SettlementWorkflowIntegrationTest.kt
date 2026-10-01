package com.fairshare.android.feature.settlement

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.database.repository.ExpenseRepository
import com.fairshare.android.core.database.repository.GroupRepository
import com.fairshare.android.core.database.repository.SettlementRepository
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettlementWorkflowIntegrationTest {

    private lateinit var db: FairShareDatabase
    private lateinit var groupRepo: GroupRepository
    private lateinit var expenseRepo: ExpenseRepository
    private lateinit var settlementRepo: SettlementRepository

    private val groupId = "group_settle_trip"
    private val rahul = Member(id = "user_rahul", name = "Rahul", isCurrentUser = true)
    private val arjun = Member(id = "user_arjun", name = "Arjun")
    private val sneha = Member(id = "user_sneha", name = "Sneha")
    private val vikram = Member(id = "user_vikram", name = "Vikram")
    private val allMembers = listOf(rahul, arjun, sneha, vikram)

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = FairShareDatabase.createInMemory(context)
        groupRepo = GroupRepository(db)
        expenseRepo = ExpenseRepository(db)
        settlementRepo = SettlementRepository(db)

        val groupEntity = GroupEntity(
            id = groupId,
            name = "Goa Trip 2026",
            createdById = rahul.id,
            currency = "INR"
        )
        val userEntities = allMembers.mapIndexed { index, it ->
            UserEntity(id = it.id, phoneNumber = "+91987654321$index", displayName = it.name)
        }
        groupRepo.createGroup(groupEntity, userEntities)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testMultiMemberSettlementAndZeroSumInvariant() = runBlocking {
        // Expense 1: Rahul pays ₹3000 for Villa, equal split across all 4 (₹750 each)
        val exp1Amount = Money.fromRupees(3000.0)
        val exp1 = Expense(
            id = "exp_villa",
            groupId = groupId,
            description = "Villa Stay",
            totalAmount = exp1Amount,
            payers = listOf(ExpensePayer(rahul.id, exp1Amount)),
            participants = allMembers.map { ExpenseParticipant(it.id) },
            splitMethod = SplitMethod.EQUAL,
            category = ExpenseCategory.ACCOMMODATION,
            mode = PaymentMode.ONLINE
        )
        expenseRepo.saveExpense(exp1, groupId, actorId = rahul.id)

        // Expense 2: Arjun pays ₹1200 for Dinner, split among Rahul, Arjun, Sneha (₹400 each, Vikram excluded)
        val exp2Amount = Money.fromRupees(1200.0)
        val exp2 = Expense(
            id = "exp_dinner",
            groupId = groupId,
            description = "Beach Dinner",
            totalAmount = exp2Amount,
            payers = listOf(ExpensePayer(arjun.id, exp2Amount)),
            participants = listOf(
                ExpenseParticipant(memberId = rahul.id),
                ExpenseParticipant(memberId = arjun.id),
                ExpenseParticipant(memberId = sneha.id),
                ExpenseParticipant(memberId = vikram.id, excluded = true)
            ),
            splitMethod = SplitMethod.EQUAL,
            category = ExpenseCategory.FOOD,
            mode = PaymentMode.CARD
        )
        expenseRepo.saveExpense(exp2, groupId, actorId = arjun.id)

        // Expense 3: Sneha pays ₹800 for Groceries, exact split (Rahul 300, Arjun 300, Sneha 200, Vikram 0)
        val exp3Amount = Money.fromRupees(800.0)
        val exp3 = Expense(
            id = "exp_groceries",
            groupId = groupId,
            description = "Supermarket Groceries",
            totalAmount = exp3Amount,
            payers = listOf(ExpensePayer(sneha.id, exp3Amount)),
            participants = listOf(
                ExpenseParticipant(memberId = rahul.id, exactAmount = Money.fromRupees(300.0)),
                ExpenseParticipant(memberId = arjun.id, exactAmount = Money.fromRupees(300.0)),
                ExpenseParticipant(memberId = sneha.id, exactAmount = Money.fromRupees(200.0)),
                ExpenseParticipant(memberId = vikram.id, excluded = true)
            ),
            splitMethod = SplitMethod.EXACT,
            category = ExpenseCategory.SHOPPING,
            mode = PaymentMode.UPI
        )
        expenseRepo.saveExpense(exp3, groupId, actorId = sneha.id)

        // 1. Verify Member Balances
        val balances = settlementRepo.calculateGroupBalances(groupId)
        assertEquals(4, balances.size)

        // Sum of all net balances must equal 0
        val sumNet = balances.sumOf { it.netBalance.amountMinor }
        assertEquals(0L, sumNet)

        val rahulBal = balances.first { it.memberId == rahul.id }
        val arjunBal = balances.first { it.memberId == arjun.id }
        val snehaBal = balances.first { it.memberId == sneha.id }
        val vikramBal = balances.first { it.memberId == vikram.id }

        // Rahul: Paid 3000, Consumed 750 + 400 + 300 = 1450. Net = +1550
        assertEquals(300000L, rahulBal.paid.amountMinor)
        assertEquals(145000L, rahulBal.consumed.amountMinor)
        assertEquals(155000L, rahulBal.netBalance.amountMinor)

        // Arjun: Paid 1200, Consumed 750 + 400 + 300 = 1450. Net = -250
        assertEquals(120000L, arjunBal.paid.amountMinor)
        assertEquals(145000L, arjunBal.consumed.amountMinor)
        assertEquals(-25000L, arjunBal.netBalance.amountMinor)

        // Sneha: Paid 800, Consumed 750 + 400 + 200 = 1350. Net = -550
        assertEquals(80000L, snehaBal.paid.amountMinor)
        assertEquals(135000L, snehaBal.consumed.amountMinor)
        assertEquals(-55000L, snehaBal.netBalance.amountMinor)

        // Vikram: Paid 0, Consumed 750. Net = -750
        assertEquals(0L, vikramBal.paid.amountMinor)
        assertEquals(75000L, vikramBal.consumed.amountMinor)
        assertEquals(-75000L, vikramBal.netBalance.amountMinor)

        // 2. Verify Suggested Settlement Plan
        val plan = settlementRepo.calculateSettlementPlan(groupId)
        assertFalse(plan.isSettled)
        assertEquals(3, plan.transfers.size)
        assertEquals(155000L, plan.totalTransferred.amountMinor)
        assertTrue(plan.isExactMinimum)
        assertEquals("Optimal settlement", plan.algorithmLabel)

        // All debtors pay Rahul
        val recipients = plan.transfers.map { it.toMemberId }.toSet()
        assertEquals(setOf(rahul.id), recipients)
    }

    @Test
    fun testMultiCreditorSuggestedSettlementLabel() = runBlocking {
        // Rahul and Arjun are creditors; Sneha and Vikram are debtors.
        val balances = listOf(
            com.fairshare.android.core.domain.balance.MemberBalance(rahul.id, rahul.name, Money.fromRupees(500.0)),
            com.fairshare.android.core.domain.balance.MemberBalance(arjun.id, arjun.name, Money.fromRupees(500.0)),
            com.fairshare.android.core.domain.balance.MemberBalance(sneha.id, sneha.name, Money.fromRupees(-600.0)),
            com.fairshare.android.core.domain.balance.MemberBalance(vikram.id, vikram.name, Money.fromRupees(-400.0))
        )

        val plan = SettlementEngine.calculateSettlement(balances)
        assertFalse(plan.isSettled)
        assertFalse(plan.isExactMinimum)
        assertEquals("Suggested settlement", plan.algorithmLabel)
        assertEquals(100000L, plan.totalTransferred.amountMinor)
    }

    @Test
    fun testSimulationIsolationGuarantees() = runBlocking {
        // Seed an expense
        val amount = Money.fromRupees(2000.0)
        val exp = Expense(
            id = "exp_flight",
            groupId = groupId,
            description = "Flight tickets",
            totalAmount = amount,
            payers = listOf(ExpensePayer(rahul.id, amount)),
            participants = allMembers.map { ExpenseParticipant(it.id) },
            splitMethod = SplitMethod.EQUAL,
            category = ExpenseCategory.TRANSPORT
        )
        expenseRepo.saveExpense(exp, groupId, actorId = rahul.id)

        val authoritativeExpenses = expenseRepo.getExpenses(groupId)
        val authoritativePaymentsBefore = settlementRepo.getPayments(groupId)
        assertTrue(authoritativePaymentsBefore.isEmpty())

        // Simulate hypothetical payment: Arjun pays Rahul ₹500
        val simPayment = SettlementPayment(
            id = "sim_pay_1",
            fromMemberId = arjun.id,
            toMemberId = rahul.id,
            amount = Money.fromRupees(500.0),
            groupId = groupId
        )

        val simResult = SimulationEngine.simulatePayment(
            members = allMembers,
            currentExpenses = authoritativeExpenses,
            currentPayments = authoritativePaymentsBefore,
            candidatePayment = simPayment,
            currency = Currency.INR
        )

        // Verify simulation math
        val prevArjunNet = simResult.previousBalances.first { it.memberId == arjun.id }.netBalance.amountMinor
        val nextArjunNet = simResult.simulatedBalances.first { it.memberId == arjun.id }.netBalance.amountMinor
        assertEquals(50000L, nextArjunNet - prevArjunNet) // Debt reduced by 500

        // CRITICAL ISOLATION CHECK: Authoritative database MUST NOT change
        val authoritativePaymentsAfter = settlementRepo.getPayments(groupId)
        assertTrue(authoritativePaymentsAfter.isEmpty())

        val dbBalances = settlementRepo.calculateGroupBalances(groupId)
        val dbArjunNet = dbBalances.first { it.memberId == arjun.id }.netBalance.amountMinor
        assertEquals(prevArjunNet, dbArjunNet)
    }

    @Test
    fun testPartialPaymentAndRemainingBalanceReconciliation() = runBlocking {
        // Rahul pays ₹1000 for Arjun (2-member direct debt)
        val amount = Money.fromRupees(1000.0)
        val exp = Expense(
            id = "exp_loan",
            groupId = groupId,
            description = "Direct Purchase for Arjun",
            totalAmount = amount,
            payers = listOf(ExpensePayer(rahul.id, amount)),
            participants = listOf(
                ExpenseParticipant(memberId = rahul.id, excluded = true),
                ExpenseParticipant(memberId = arjun.id)
            ),
            splitMethod = SplitMethod.EQUAL,
            category = ExpenseCategory.SHOPPING
        )
        expenseRepo.saveExpense(exp, groupId, actorId = rahul.id)

        // Arjun owes ₹1000
        val plan1 = settlementRepo.calculateSettlementPlan(groupId)
        assertEquals(1, plan1.transfers.size)
        assertEquals(100000L, plan1.transfers.first().amount.amountMinor)

        // Arjun makes a partial payment of ₹400
        val partialPayment = SettlementPayment(
            id = "pay_part_1",
            fromMemberId = arjun.id,
            toMemberId = rahul.id,
            amount = Money.fromRupees(400.0),
            groupId = groupId,
            mode = PaymentMode.UPI,
            note = "Partial payment 1"
        )
        settlementRepo.recordPayment(partialPayment, groupId, actorId = arjun.id)

        // Verify partial payment recorded
        val recordedPayments = settlementRepo.getPayments(groupId)
        assertEquals(1, recordedPayments.size)
        assertEquals(40000L, recordedPayments.first().amount.amountMinor)

        // Verify remaining balance: Arjun now owes ₹600
        val plan2 = settlementRepo.calculateSettlementPlan(groupId)
        assertFalse(plan2.isSettled)
        assertEquals(1, plan2.transfers.size)
        assertEquals(60000L, plan2.transfers.first().amount.amountMinor)
        assertEquals(60000L, plan2.totalTransferred.amountMinor)

        // Expenses total must NOT be altered by payments
        val expenses = expenseRepo.getExpenses(groupId)
        assertEquals(1, expenses.size)
        assertEquals(100000L, expenses.first().amount.amountMinor)

        // Arjun pays remaining ₹600 to achieve full settlement
        val finalPayment = SettlementPayment(
            id = "pay_final_2",
            fromMemberId = arjun.id,
            toMemberId = rahul.id,
            amount = Money.fromRupees(600.0),
            groupId = groupId,
            mode = PaymentMode.CASH,
            note = "Cleared balance"
        )
        settlementRepo.recordPayment(finalPayment, groupId, actorId = arjun.id)

        val plan3 = settlementRepo.calculateSettlementPlan(groupId)
        assertTrue(plan3.isSettled)
        assertEquals(0, plan3.transfers.size)
        assertEquals(0L, plan3.totalTransferred.amountMinor)
    }

    @Test
    fun testExpenseReversalRecalculatesSettlementState() = runBlocking {
        // Rahul pays ₹2000 for Arjun
        val amount = Money.fromRupees(2000.0)
        val exp = Expense(
            id = "exp_cancel_me",
            groupId = groupId,
            description = "Accidental Ticket",
            totalAmount = amount,
            payers = listOf(ExpensePayer(rahul.id, amount)),
            participants = listOf(
                ExpenseParticipant(memberId = rahul.id, excluded = true),
                ExpenseParticipant(memberId = arjun.id)
            ),
            splitMethod = SplitMethod.EQUAL,
            category = ExpenseCategory.OTHER
        )
        expenseRepo.saveExpense(exp, groupId, actorId = rahul.id)

        // Arjun owes ₹2000
        val planBefore = settlementRepo.calculateSettlementPlan(groupId)
        assertEquals(200000L, planBefore.totalTransferred.amountMinor)

        // Reverse the expense
        expenseRepo.reverseExpense(
            id = exp.id,
            groupId = groupId,
            reason = "Booked wrong date, vendor refunded",
            actorId = rahul.id
        )

        // Settlement plan must reflect 0 obligations
        val planAfter = settlementRepo.calculateSettlementPlan(groupId)
        assertTrue(planAfter.isSettled)
        assertEquals(0, planAfter.transfers.size)
        assertEquals(0L, planAfter.totalTransferred.amountMinor)
    }
}
