package com.fairshare.android.feature.expenses

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
import com.fairshare.android.core.domain.ledger.LedgerEngine
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.ExpenseItem
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.domain.split.ExpenseSplitEngine
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
class ExpenseWorkflowIntegrationTest {

    private lateinit var db: FairShareDatabase
    private lateinit var groupRepo: GroupRepository
    private lateinit var expenseRepo: ExpenseRepository
    private lateinit var settlementRepo: SettlementRepository

    private val groupId = "group_trip_1"
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
            name = "Goa Trip",
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
    fun testP7_EqualSplit_SaveAndLedgerZeroSum() = runBlocking {
        val total = Money.fromRupees(1200.0) // ₹1,200 / 4 = ₹300 each
        val expense = Expense(
            id = "exp_dinner_1",
            groupId = groupId,
            description = "Dinner",
            totalAmount = total,
            payers = listOf(ExpensePayer(rahul.id, total)),
            participants = allMembers.map { ExpenseParticipant(it.id) },
            splitMethod = SplitMethod.EQUAL,
            mode = PaymentMode.UPI,
            category = ExpenseCategory.FOOD,
            timestamp = System.currentTimeMillis()
        )

        expenseRepo.saveExpense(expense, groupId, actorId = rahul.id)

        val saved = expenseRepo.getExpense(expense.id)
        assertNotNull(saved)
        assertEquals("Dinner", saved!!.description)
        assertEquals(120000L, saved.totalAmount.amountMinor)
        assertEquals(SplitMethod.EQUAL, saved.splitMethod)
        assertEquals(1, saved.payers.size)
        assertEquals(rahul.id, saved.payers[0].memberId)

        val splits = ExpenseSplitEngine.calculateSplits(saved)
        assertEquals(4, splits.size)
        assertEquals(30000L, splits[rahul.id]!!.amountMinor)
        assertEquals(30000L, splits[arjun.id]!!.amountMinor)
        assertEquals(30000L, splits[sneha.id]!!.amountMinor)
        assertEquals(30000L, splits[vikram.id]!!.amountMinor)

        val balances = settlementRepo.calculateGroupBalances(groupId)
        // Rahul paid 1200, consumed 300 -> net +900
        val rahulBal = balances.find { it.memberId == rahul.id }
        assertEquals(90000L, rahulBal!!.netBalance.amountMinor)

        // Arjun, Sneha, Vikram paid 0, consumed 300 -> net -300 each
        val arjunBal = balances.find { it.memberId == arjun.id }
        assertEquals(-30000L, arjunBal!!.netBalance.amountMinor)

        // Zero-sum invariant
        val totalNet = balances.sumOf { it.netBalance.amountMinor }
        assertEquals(0L, totalNet)
    }

    @Test
    fun testP7_ExactSplit_SaveAndReconcile() = runBlocking {
        val total = Money.fromRupees(1000.0)
        val expense = Expense(
            id = "exp_exact_1",
            groupId = groupId,
            description = "Groceries",
            totalAmount = total,
            payers = listOf(ExpensePayer(rahul.id, total)),
            participants = listOf(
                ExpenseParticipant(memberId = rahul.id, exactAmount = Money.fromRupees(500.0)),
                ExpenseParticipant(memberId = arjun.id, exactAmount = Money.fromRupees(300.0)),
                ExpenseParticipant(memberId = sneha.id, exactAmount = Money.fromRupees(200.0)),
                ExpenseParticipant(memberId = vikram.id, excluded = true)
            ),
            splitMethod = SplitMethod.EXACT,
            mode = PaymentMode.CASH,
            category = ExpenseCategory.SHOPPING,
            timestamp = System.currentTimeMillis()
        )

        expenseRepo.saveExpense(expense, groupId, actorId = rahul.id)

        val retrieved = expenseRepo.getExpense(expense.id)
        assertNotNull(retrieved)
        val splits = ExpenseSplitEngine.calculateSplits(retrieved!!)
        assertEquals(50000L, splits[rahul.id]!!.amountMinor)
        assertEquals(30000L, splits[arjun.id]!!.amountMinor)
        assertEquals(20000L, splits[sneha.id]!!.amountMinor)
        assertEquals(0L, splits[vikram.id]!!.amountMinor)
    }

    @Test
    fun testP7_PercentageSplit_SaveAndReconcile() = runBlocking {
        val total = Money.fromRupees(1000.0)
        val expense = Expense(
            id = "exp_pct_1",
            groupId = groupId,
            description = "Resort Stay",
            totalAmount = total,
            payers = listOf(ExpensePayer(rahul.id, total)),
            participants = listOf(
                ExpenseParticipant(memberId = rahul.id, percentageBasisPoints = 5000), // 50%
                ExpenseParticipant(memberId = arjun.id, percentageBasisPoints = 3000), // 30%
                ExpenseParticipant(memberId = sneha.id, percentageBasisPoints = 2000)  // 20%
            ),
            splitMethod = SplitMethod.PERCENTAGE,
            mode = PaymentMode.ONLINE,
            category = ExpenseCategory.ACCOMMODATION,
            timestamp = System.currentTimeMillis()
        )

        expenseRepo.saveExpense(expense, groupId, actorId = rahul.id)

        val retrieved = expenseRepo.getExpense(expense.id)
        assertNotNull(retrieved)
        val splits = ExpenseSplitEngine.calculateSplits(retrieved!!)
        assertEquals(50000L, splits[rahul.id]!!.amountMinor)
        assertEquals(30000L, splits[arjun.id]!!.amountMinor)
        assertEquals(20000L, splits[sneha.id]!!.amountMinor)
    }

    @Test
    fun testP7_SharesSplit_SaveAndReconcile() = runBlocking {
        val total = Money.fromRupees(1000.0) // 2 + 1 + 1 = 4 shares -> 500, 250, 250
        val expense = Expense(
            id = "exp_shares_1",
            groupId = groupId,
            description = "Gasoline",
            totalAmount = total,
            payers = listOf(ExpensePayer(rahul.id, total)),
            participants = listOf(
                ExpenseParticipant(memberId = rahul.id, shares = 2),
                ExpenseParticipant(memberId = arjun.id, shares = 1),
                ExpenseParticipant(memberId = sneha.id, shares = 1)
            ),
            splitMethod = SplitMethod.SHARES,
            mode = PaymentMode.CARD,
            category = ExpenseCategory.FUEL,
            timestamp = System.currentTimeMillis()
        )

        expenseRepo.saveExpense(expense, groupId, actorId = rahul.id)

        val retrieved = expenseRepo.getExpense(expense.id)
        assertNotNull(retrieved)
        val splits = ExpenseSplitEngine.calculateSplits(retrieved!!)
        assertEquals(50000L, splits[rahul.id]!!.amountMinor)
        assertEquals(25000L, splits[arjun.id]!!.amountMinor)
        assertEquals(25000L, splits[sneha.id]!!.amountMinor)
    }

    @Test
    fun testP7_ItemizedSplit_SaveAndReconcile() = runBlocking {
        val total = Money.fromRupees(1100.0)
        val items = listOf(
            ExpenseItem(
                id = "item_1",
                name = "Pizza",
                amount = Money.fromRupees(600.0),
                participantMemberIds = listOf(rahul.id, arjun.id) // 300 each
            ),
            ExpenseItem(
                id = "item_2",
                name = "Drinks",
                amount = Money.fromRupees(200.0),
                participantMemberIds = listOf(arjun.id) // 200
            ),
            ExpenseItem(
                id = "item_3",
                name = "Dessert",
                amount = Money.fromRupees(300.0),
                participantMemberIds = listOf(rahul.id, sneha.id) // 150 each
            )
        )

        val expense = Expense(
            id = "exp_itemized_1",
            groupId = groupId,
            description = "Italian Bistro",
            totalAmount = total,
            payers = listOf(ExpensePayer(rahul.id, total)),
            participants = listOf(
                ExpenseParticipant(rahul.id),
                ExpenseParticipant(arjun.id),
                ExpenseParticipant(sneha.id)
            ),
            splitMethod = SplitMethod.ITEMIZED,
            items = items,
            mode = PaymentMode.ONLINE,
            category = ExpenseCategory.FOOD,
            timestamp = System.currentTimeMillis()
        )

        expenseRepo.saveExpense(expense, groupId, actorId = rahul.id)

        val retrieved = expenseRepo.getExpense(expense.id)
        assertNotNull(retrieved)
        assertEquals(3, retrieved!!.items.size)

        val splits = ExpenseSplitEngine.calculateSplits(retrieved)
        // Rahul: 300 (pizza) + 150 (dessert) = 450
        // Arjun: 300 (pizza) + 200 (drinks) = 500
        // Sneha: 150 (dessert) = 150
        assertEquals(45000L, splits[rahul.id]!!.amountMinor)
        assertEquals(50000L, splits[arjun.id]!!.amountMinor)
        assertEquals(15000L, splits[sneha.id]!!.amountMinor)
    }

    @Test
    fun testP7_MultiPayer_SaveAndLedgerZeroSum() = runBlocking {
        val total = Money.fromRupees(2000.0)
        // Rahul paid 1200, Arjun paid 800
        val payers = listOf(
            ExpensePayer(rahul.id, Money.fromRupees(1200.0)),
            ExpensePayer(arjun.id, Money.fromRupees(800.0))
        )
        val expense = Expense(
            id = "exp_multi_1",
            groupId = groupId,
            description = "Boat Cruise",
            totalAmount = total,
            payers = payers,
            participants = allMembers.map { ExpenseParticipant(it.id) },
            splitMethod = SplitMethod.EQUAL,
            mode = PaymentMode.ONLINE,
            category = ExpenseCategory.ACTIVITIES,
            timestamp = System.currentTimeMillis()
        )

        expenseRepo.saveExpense(expense, groupId, actorId = rahul.id)

        val balances = settlementRepo.calculateGroupBalances(groupId)
        // Each owes 500
        // Rahul paid 1200, owes 500 -> +700
        // Arjun paid 800, owes 500 -> +300
        // Sneha paid 0, owes 500 -> -500
        // Vikram paid 0, owes 500 -> -500
        val rBal = balances.find { it.memberId == rahul.id }
        val aBal = balances.find { it.memberId == arjun.id }
        val sBal = balances.find { it.memberId == sneha.id }
        val vBal = balances.find { it.memberId == vikram.id }

        assertEquals(70000L, rBal!!.netBalance.amountMinor)
        assertEquals(30000L, aBal!!.netBalance.amountMinor)
        assertEquals(-50000L, sBal!!.netBalance.amountMinor)
        assertEquals(-50000L, vBal!!.netBalance.amountMinor)
        assertEquals(0L, balances.sumOf { it.netBalance.amountMinor })
    }

    @Test
    fun testP7_EditExpense_UpdatesDetailsAndLedger() = runBlocking {
        val initialTotal = Money.fromRupees(1000.0)
        val initialExpense = Expense(
            id = "exp_edit_test",
            groupId = groupId,
            description = "Cab to Airport",
            totalAmount = initialTotal,
            payers = listOf(ExpensePayer(rahul.id, initialTotal)),
            participants = listOf(ExpenseParticipant(rahul.id), ExpenseParticipant(arjun.id)),
            splitMethod = SplitMethod.EQUAL,
            mode = PaymentMode.CASH,
            category = ExpenseCategory.TRANSPORT
        )
        expenseRepo.saveExpense(initialExpense, groupId, actorId = rahul.id)

        // Now Edit: amount increases to 1600, Sneha is added
        val updatedTotal = Money.fromRupees(1600.0)
        val updatedExpense = initialExpense.copy(
            description = "Cab to Beach & Airport",
            totalAmount = updatedTotal,
            payers = listOf(ExpensePayer(rahul.id, updatedTotal)),
            participants = listOf(
                ExpenseParticipant(rahul.id),
                ExpenseParticipant(arjun.id),
                ExpenseParticipant(sneha.id)
            )
        )
        expenseRepo.updateExpense(updatedExpense, groupId, baseRevision = 1L, actorId = rahul.id)

        val retrieved = expenseRepo.getExpense(initialExpense.id)
        assertNotNull(retrieved)
        assertEquals("Cab to Beach & Airport", retrieved!!.description)
        assertEquals(160000L, retrieved.totalAmount.amountMinor)
        assertEquals(3, retrieved.participants.size)

        // Verify sync operation was enqueued for UPDATE
        val syncOps = db.syncDao().getOperationsForEntity(initialExpense.id)
        val updateOp = syncOps.find { it.operationType == "UPDATE" }
        assertNotNull(updateOp)
    }

    @Test
    fun testP7_ReversalWorkflow_ExcludesFromLedgerAndRetainsAuditHistory() = runBlocking {
        val total = Money.fromRupees(1200.0)
        val expense = Expense(
            id = "exp_to_reverse",
            groupId = groupId,
            description = "Accidental Double Entry",
            totalAmount = total,
            payers = listOf(ExpensePayer(rahul.id, total)),
            participants = allMembers.map { ExpenseParticipant(it.id) },
            splitMethod = SplitMethod.EQUAL,
            category = ExpenseCategory.OTHER
        )
        expenseRepo.saveExpense(expense, groupId, actorId = rahul.id)

        // Verify it affected balances before reversal
        var balances = settlementRepo.calculateGroupBalances(groupId)
        val rBalBefore = balances.find { it.memberId == rahul.id }
        assertEquals(90000L, rBalBefore!!.netBalance.amountMinor)

        // Perform Reversal
        expenseRepo.reverseExpense(
            id = expense.id,
            groupId = groupId,
            reason = "Duplicate entry entered twice by accident",
            actorId = rahul.id
        )

        // Verify getExpenses() (active list) excludes reversed expense
        val activeExpenses = expenseRepo.getExpenses(groupId)
        assertTrue(activeExpenses.none { it.id == expense.id })

        // Verify getExpense() returns with isDeleted == true
        val reversedRecord = expenseRepo.getExpense(expense.id)
        assertNotNull(reversedRecord)
        assertTrue(reversedRecord!!.isDeleted)

        // Verify Activity Event was recorded for audit trail
        val events = db.activityDao().getEventsForGroup(groupId)
        val reversalEvent = events.find { it.eventType == "EXPENSE_REVERSED" }
        assertNotNull(reversalEvent)
        assertTrue(reversalEvent!!.summaryText.contains("Duplicate entry entered twice by accident"))

        // Verify Balances recalculated back to 0
        balances = settlementRepo.calculateGroupBalances(groupId)
        val rBalAfter = balances.find { it.memberId == rahul.id }
        assertEquals(0L, rBalAfter!!.netBalance.amountMinor)
        assertEquals(0L, balances.sumOf { it.netBalance.amountMinor })

        // Verify Sync operation was enqueued for DELETE/reversal
        val syncOps = db.syncDao().getOperationsForEntity(expense.id)
        val deleteOp = syncOps.find { it.operationType == "DELETE" }
        assertNotNull(deleteOp)
        assertTrue(deleteOp!!.payloadJson.contains("Duplicate entry entered twice by accident"))
    }
}
