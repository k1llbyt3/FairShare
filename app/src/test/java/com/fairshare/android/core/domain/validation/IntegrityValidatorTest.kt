package com.fairshare.android.core.domain.validation

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.ledger.GroupLedger
import com.fairshare.android.core.domain.ledger.MemberLedger
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.model.SplitMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityValidatorTest {

    @Test
    fun testValidExpense() {
        val expense = Expense(
            id = "e1",
            description = "Dinner",
            totalAmount = Money(30000L),
            payers = listOf(ExpensePayer("1", Money(30000L))),
            participants = listOf(ExpenseParticipant("1"), ExpenseParticipant("2")),
            splitMethod = SplitMethod.EQUAL
        )
        val result = IntegrityValidator.validateExpense(expense)
        assertTrue(result.isValid)
    }

    @Test
    fun testPayerSumMismatchFails() {
        val expense = Expense(
            id = "e1",
            description = "Dinner",
            totalAmount = Money(30000L),
            payers = listOf(ExpensePayer("1", Money(20000L))), // Only paid 200 of 300
            participants = listOf(ExpenseParticipant("1"), ExpenseParticipant("2")),
            splitMethod = SplitMethod.EQUAL
        )
        val result = IntegrityValidator.validateExpense(expense)
        assertFalse(result.isValid)
    }

    @Test
    fun testExactSplitSumMismatchFails() {
        val expense = Expense(
            id = "e1",
            description = "Dinner",
            totalAmount = Money(30000L),
            payers = listOf(ExpensePayer("1", Money(30000L))),
            participants = listOf(
                ExpenseParticipant("1", exactAmount = Money(10000L)),
                ExpenseParticipant("2", exactAmount = Money(10000L)) // 100 + 100 != 300
            ),
            splitMethod = SplitMethod.EXACT
        )
        val result = IntegrityValidator.validateExpense(expense)
        assertFalse(result.isValid)
    }

    @Test
    fun testPercentageNot100PercentFails() {
        val expense = Expense(
            id = "e1",
            description = "Dinner",
            totalAmount = Money(30000L),
            payers = listOf(ExpensePayer("1", Money(30000L))),
            participants = listOf(
                ExpenseParticipant("1", percentageBasisPoints = 5000),
                ExpenseParticipant("2", percentageBasisPoints = 4000) // 90% != 100%
            ),
            splitMethod = SplitMethod.PERCENTAGE
        )
        val result = IntegrityValidator.validateExpense(expense)
        assertFalse(result.isValid)
    }

    @Test
    fun testPaymentToSelfFails() {
        val payment = SettlementPayment(
            id = "p1",
            fromMemberId = "1",
            toMemberId = "1", // Same member
            amount = Money(10000L)
        )
        val result = IntegrityValidator.validatePayment(payment)
        assertFalse(result.isValid)
    }

    @Test
    fun testZeroOrNegativePaymentFails() {
        val payment = SettlementPayment(
            id = "p1",
            fromMemberId = "1",
            toMemberId = "2",
            amount = Money(-100L)
        )
        val result = IntegrityValidator.validatePayment(payment)
        assertFalse(result.isValid)
    }

    @Test
    fun testLedgerConservationValidation() {
        val validLedger = GroupLedger(
            currency = Currency.INR,
            memberLedgers = mapOf(
                "1" to MemberLedger("1", Money(100L), Money(0L), Money.ZERO, Money(100L)),
                "2" to MemberLedger("2", Money(0L), Money(100L), Money.ZERO, Money(-100L))
            ),
            totalExpenseSpend = Money(100L),
            totalDirectTransfers = Money.ZERO,
            totalAdjustments = Money.ZERO
        )
        assertTrue(IntegrityValidator.validateLedger(validLedger).isValid)

        val brokenLedger = GroupLedger(
            currency = Currency.INR,
            memberLedgers = mapOf(
                "1" to MemberLedger("1", Money(100L), Money(0L), Money.ZERO, Money(100L)),
                "2" to MemberLedger("2", Money(0L), Money(50L), Money.ZERO, Money(-50L))
            ),
            totalExpenseSpend = Money(100L),
            totalDirectTransfers = Money.ZERO,
            totalAdjustments = Money.ZERO
        )
        assertFalse(IntegrityValidator.validateLedger(brokenLedger).isValid)
    }
}
