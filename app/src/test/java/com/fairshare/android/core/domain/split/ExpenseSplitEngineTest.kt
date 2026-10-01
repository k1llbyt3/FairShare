package com.fairshare.android.core.domain.split

import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseItem
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.SplitMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseSplitEngineTest {

    @Test
    fun testEqualSplit() {
        val expense = Expense(
            id = "e1",
            description = "Lunch",
            totalAmount = Money(90000L), // ₹900.00
            payers = listOf(ExpensePayer("A", Money(90000L))),
            participants = listOf(
                ExpenseParticipant("A"),
                ExpenseParticipant("B"),
                ExpenseParticipant("C")
            ),
            splitMethod = SplitMethod.EQUAL
        )

        val splits = ExpenseSplitEngine.calculateSplits(expense)
        assertEquals(30000L, splits["A"]?.amountMinor)
        assertEquals(30000L, splits["B"]?.amountMinor)
        assertEquals(30000L, splits["C"]?.amountMinor)
        assertEquals(90000L, splits.values.sumOf { it.amountMinor })
    }

    @Test
    fun testExactSplit() {
        val expense = Expense(
            id = "e2",
            description = "Groceries",
            totalAmount = Money(100000L), // ₹1,000.00
            payers = listOf(ExpensePayer("A", Money(100000L))),
            participants = listOf(
                ExpenseParticipant("A", exactAmount = Money(50000L)),
                ExpenseParticipant("B", exactAmount = Money(30000L)),
                ExpenseParticipant("C", exactAmount = Money(20000L))
            ),
            splitMethod = SplitMethod.EXACT
        )

        val splits = ExpenseSplitEngine.calculateSplits(expense)
        assertEquals(50000L, splits["A"]?.amountMinor)
        assertEquals(30000L, splits["B"]?.amountMinor)
        assertEquals(20000L, splits["C"]?.amountMinor)
        assertEquals(100000L, splits.values.sumOf { it.amountMinor })
    }

    @Test
    fun testPercentageSplit() {
        val expense = Expense(
            id = "e3",
            description = "Hotel",
            totalAmount = Money(10000L), // ₹100.00
            payers = listOf(ExpensePayer("A", Money(10000L))),
            participants = listOf(
                ExpenseParticipant("A", percentageBasisPoints = 5000), // 50%
                ExpenseParticipant("B", percentageBasisPoints = 3000), // 30%
                ExpenseParticipant("C", percentageBasisPoints = 2000)  // 20%
            ),
            splitMethod = SplitMethod.PERCENTAGE
        )

        val splits = ExpenseSplitEngine.calculateSplits(expense)
        assertEquals(5000L, splits["A"]?.amountMinor)
        assertEquals(3000L, splits["B"]?.amountMinor)
        assertEquals(2000L, splits["C"]?.amountMinor)
        assertEquals(10000L, splits.values.sumOf { it.amountMinor })
    }

    @Test
    fun testSharesSplit() {
        val expense = Expense(
            id = "e4",
            description = "Cab",
            totalAmount = Money(40000L), // ₹400.00
            payers = listOf(ExpensePayer("A", Money(40000L))),
            participants = listOf(
                ExpenseParticipant("A", shares = 2), // 2 shares (50%)
                ExpenseParticipant("B", shares = 1), // 1 share  (25%)
                ExpenseParticipant("C", shares = 1)  // 1 share  (25%)
            ),
            splitMethod = SplitMethod.SHARES
        )

        val splits = ExpenseSplitEngine.calculateSplits(expense)
        assertEquals(20000L, splits["A"]?.amountMinor)
        assertEquals(10000L, splits["B"]?.amountMinor)
        assertEquals(10000L, splits["C"]?.amountMinor)
        assertEquals(40000L, splits.values.sumOf { it.amountMinor })
    }

    @Test
    fun testItemizedSplit() {
        val expense = Expense(
            id = "e5",
            description = "Dinner",
            totalAmount = Money(110000L), // ₹1,100.00
            payers = listOf(ExpensePayer("A", Money(110000L))),
            participants = listOf(
                ExpenseParticipant("A"),
                ExpenseParticipant("B"),
                ExpenseParticipant("C")
            ),
            splitMethod = SplitMethod.ITEMIZED,
            items = listOf(
                // Pizza 600 shared by A + B
                ExpenseItem("i1", "Pizza", Money(60000L), listOf("A", "B")),
                // Drinks 200 for B only
                ExpenseItem("i2", "Drinks", Money(20000L), listOf("B")),
                // Dessert 300 shared by A + C
                ExpenseItem("i3", "Dessert", Money(30000L), listOf("A", "C"))
            )
        )

        val splits = ExpenseSplitEngine.calculateSplits(expense)
        // A: 300 (pizza) + 150 (dessert) = 450
        // B: 300 (pizza) + 200 (drinks) = 500
        // C: 150 (dessert) = 150
        assertEquals(45000L, splits["A"]?.amountMinor)
        assertEquals(50000L, splits["B"]?.amountMinor)
        assertEquals(15000L, splits["C"]?.amountMinor)
        assertEquals(110000L, splits.values.sumOf { it.amountMinor })
    }

    @Test
    fun testItemizedSplitWithTaxReconciliation() {
        val expense = Expense(
            id = "e6",
            description = "Cafe",
            totalAmount = Money(110000L), // ₹1,100.00 total (includes ₹100 tax)
            payers = listOf(ExpensePayer("A", Money(110000L))),
            participants = listOf(
                ExpenseParticipant("A"),
                ExpenseParticipant("B")
            ),
            splitMethod = SplitMethod.ITEMIZED,
            items = listOf(
                ExpenseItem("i1", "Burger", Money(50000L), listOf("A")),
                ExpenseItem("i2", "Pasta", Money(50000L), listOf("B"))
            )
        )

        val splits = ExpenseSplitEngine.calculateSplits(expense)
        // Each had 500 item, total is 1100, tax 100 is split 50/50 -> 550 each
        assertEquals(55000L, splits["A"]?.amountMinor)
        assertEquals(55000L, splits["B"]?.amountMinor)
        assertEquals(110000L, splits.values.sumOf { it.amountMinor })
    }

    @Test
    fun testPayerExcluded() {
        // Payer A pays ₹100, but only B and C participate
        val expense = Expense(
            id = "e7",
            description = "Gift",
            totalAmount = Money(10000L),
            payers = listOf(ExpensePayer("A", Money(10000L))),
            participants = listOf(
                ExpenseParticipant("B"),
                ExpenseParticipant("C")
            ),
            splitMethod = SplitMethod.EQUAL
        )

        val splits = ExpenseSplitEngine.calculateSplits(expense)
        // A does not participate
        assertEquals(null, splits["A"])
        assertEquals(5000L, splits["B"]?.amountMinor)
        assertEquals(5000L, splits["C"]?.amountMinor)
        assertEquals(10000L, splits.values.sumOf { it.amountMinor })
    }

    @Test
    fun testParticipantExclusionFlag() {
        // A, B, C listed, but C marked excluded
        val expense = Expense(
            id = "e8",
            description = "Drinks",
            totalAmount = Money(20000L),
            payers = listOf(ExpensePayer("A", Money(20000L))),
            participants = listOf(
                ExpenseParticipant("A"),
                ExpenseParticipant("B"),
                ExpenseParticipant("C", excluded = true)
            ),
            splitMethod = SplitMethod.EQUAL
        )

        val splits = ExpenseSplitEngine.calculateSplits(expense)
        assertEquals(10000L, splits["A"]?.amountMinor)
        assertEquals(10000L, splits["B"]?.amountMinor)
        assertEquals(0L, splits["C"]?.amountMinor)
        assertEquals(20000L, splits.values.sumOf { it.amountMinor })
    }
}
