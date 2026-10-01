package com.fairshare.android.feature.expenses

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.SplitMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickExpenseParserTest {

    @Test
    fun parse_rupeesSymbolAndItem_returnsCorrectDraft() {
        val draft = QuickExpenseParser.parse("₹1200 dinner")
        assertNotNull(draft)
        assertEquals(120000L, draft!!.amount.amountMinor)
        assertEquals("Dinner", draft.description)
        assertEquals(ExpenseCategory.FOOD, draft.category)
        assertEquals(SplitMethod.EQUAL, draft.splitMethod)
        assertTrue(draft.isValid)
    }

    @Test
    fun parse_plainNumberAndTransportItem_returnsCorrectDraft() {
        val draft = QuickExpenseParser.parse("500 uber")
        assertNotNull(draft)
        assertEquals(50000L, draft!!.amount.amountMinor)
        assertEquals("Uber", draft.description)
        assertEquals(ExpenseCategory.TRANSPORT, draft.category)
    }

    @Test
    fun parse_decimalAmountWithCommas_parsesExactMinorUnitsWithoutFloatingPointLoss() {
        val draft = QuickExpenseParser.parse("1,840.50 team lunch")
        assertNotNull(draft)
        assertEquals(184050L, draft!!.amount.amountMinor)
        assertEquals("Team lunch", draft.description)
        assertEquals(ExpenseCategory.FOOD, draft.category)
    }

    @Test
    fun parse_hotelAccommodationKeyword_infersAccommodationCategory() {
        val draft = QuickExpenseParser.parse("Goa hotel stay ₹4500")
        assertNotNull(draft)
        assertEquals(450000L, draft!!.amount.amountMinor)
        assertEquals(ExpenseCategory.ACCOMMODATION, draft.category)
    }

    @Test
    fun parse_fuelKeyword_infersFuelCategory() {
        val draft = QuickExpenseParser.parse("Petrol 2000")
        assertNotNull(draft)
        assertEquals(200000L, draft!!.amount.amountMinor)
        assertEquals(ExpenseCategory.FUEL, draft.category)
    }

    @Test
    fun parse_ticketsKeyword_infersTicketsCategory() {
        val draft = QuickExpenseParser.parse("Movie tickets 600")
        assertNotNull(draft)
        assertEquals(60000L, draft!!.amount.amountMinor)
        assertEquals(ExpenseCategory.TICKETS, draft.category)
    }

    @Test
    fun parse_blankOrInvalidInput_returnsNull() {
        assertNull(QuickExpenseParser.parse(""))
        assertNull(QuickExpenseParser.parse("   "))
        assertNull(QuickExpenseParser.parse("just some words without amount"))
        assertNull(QuickExpenseParser.parse("₹0"))
    }
}
