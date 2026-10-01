package com.fairshare.android.feature.expenses

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.SplitMethod

data class QuickExpenseDraft(
    val amount: Money,
    val description: String,
    val category: ExpenseCategory,
    val splitMethod: SplitMethod = SplitMethod.EQUAL,
    val rawText: String,
    val isValid: Boolean = true
)

object QuickExpenseParser {

    private val AMOUNT_REGEX = Regex("""(?:₹|INR\s*|Rs\.?\s*)?([0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""")

    fun parse(input: String, currency: Currency = Currency.INR): QuickExpenseDraft? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val match = AMOUNT_REGEX.findAll(trimmed).firstOrNull { result ->
            val numStr = result.groupValues[1].replace(",", "")
            val value = numStr.toDoubleOrNull() ?: 0.0
            value > 0.0
        } ?: return null

        val matchedNumberStr = match.groupValues[1].replace(",", "")
        val parsedMinor = parseToMinorUnits(matchedNumberStr)
        if (parsedMinor <= 0L) return null

        // Remove the matched amount and any currency symbols from the text to form description
        val remainingText = (trimmed.substring(0, match.range.first) + " " + trimmed.substring(match.range.last + 1))
            .replace(Regex("""(?:₹|INR|Rs\.?)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val description = if (remainingText.isNotBlank()) {
            remainingText.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            "Quick Expense"
        }

        val category = inferCategory(description)

        return QuickExpenseDraft(
            amount = Money(parsedMinor, currency),
            description = description,
            category = category,
            splitMethod = SplitMethod.EQUAL,
            rawText = trimmed,
            isValid = true
        )
    }

    private fun parseToMinorUnits(numberStr: String): Long {
        return try {
            val parts = numberStr.split(".")
            val major = parts[0].toLongOrNull() ?: 0L
            val minor = if (parts.size > 1) {
                val frac = parts[1]
                when (frac.length) {
                    0 -> 0L
                    1 -> (frac + "0").toLongOrNull() ?: 0L
                    else -> frac.take(2).toLongOrNull() ?: 0L
                }
            } else 0L
            major * 100L + minor
        } catch (_: Exception) {
            0L
        }
    }

    private fun inferCategory(text: String): ExpenseCategory {
        val lower = text.lowercase()
        return when {
            lower.containsAny("dinner", "lunch", "breakfast", "food", "drinks", "beer", "wine", "cafe", "coffee", "tea", "snack", "pizza", "burger", "meal", "restaurant") ->
                ExpenseCategory.FOOD
            lower.containsAny("petrol", "diesel", "fuel", "gas", "cng") ->
                ExpenseCategory.FUEL
            lower.containsAny("taxi", "cab", "uber", "ola", "auto", "bus", "metro", "train", "flight", "fare", "toll") ->
                ExpenseCategory.TRANSPORT
            lower.containsAny("hotel", "resort", "stay", "airbnb", "hostel", "room", "lodge") ->
                ExpenseCategory.ACCOMMODATION
            lower.containsAny("ticket", "tickets", "movie", "cinema", "show", "pass") ->
                ExpenseCategory.TICKETS
            lower.containsAny("trek", "safari", "bowling", "museum", "park", "game", "tour", "activity", "activities") ->
                ExpenseCategory.ACTIVITIES
            lower.containsAny("shopping", "clothes", "market", "mall", "groceries", "grocery", "mart") ->
                ExpenseCategory.SHOPPING
            lower.containsAny("wifi", "internet", "bill", "bills", "recharge", "electricity", "water", "utility") ->
                ExpenseCategory.UTILITIES
            else -> ExpenseCategory.OTHER
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
}
