package com.fairshare.android.core.domain.natural

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SplitMethod
import java.util.regex.Pattern

object NaturalLanguageExpenseParser {

    private val AMOUNT_REGEX = Pattern.compile(
        """(?:₹|RS\.?|INR)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
        Pattern.CASE_INSENSITIVE
    )

    fun parse(
        text: String,
        members: List<Member>,
        currentMember: Member,
        currency: Currency = Currency.INR
    ): ParsedExpenseDraft {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()

        // 1. Extract amount
        var extractedAmountMinor = 0L
        val amountMatcher = AMOUNT_REGEX.matcher(trimmed)
        var amountSpanStart = -1
        var amountSpanEnd = -1

        while (amountMatcher.find()) {
            val numStr = amountMatcher.group(1)?.replace(",", "") ?: continue
            val minor = parseAmountMinor(numStr)
            if (minor != null && minor > 0L) {
                extractedAmountMinor = minor
                amountSpanStart = amountMatcher.start()
                amountSpanEnd = amountMatcher.end()
                break
            }
        }

        // 2. Identify Payer
        var payerId: String? = null
        var payerName: String? = null
        var isPayerConfident = false

        // Check for self references
        if (lower.contains("i paid") || lower.contains("paid by me") || lower.startsWith("paid ") || lower.contains(" my ")) {
            payerId = currentMember.id
            payerName = currentMember.name
            isPayerConfident = true
        } else {
            // Check member names
            for (m in members) {
                val nameLower = m.name.lowercase()
                if (lower.contains("$nameLower paid") || lower.contains("paid by $nameLower") || lower.startsWith(nameLower)) {
                    payerId = m.id
                    payerName = m.name
                    isPayerConfident = true
                    break
                }
            }
        }

        // Default to current member if not specified
        if (payerId == null) {
            payerId = currentMember.id
            payerName = currentMember.name
            isPayerConfident = false
        }

        // 3. Identify Participants
        val participantIds = mutableListOf<String>()
        var isParticipantsConfident = false

        if (lower.contains("everyone") || lower.contains("all") || lower.contains("for all") || lower.contains("for everyone")) {
            participantIds.addAll(members.map { it.id })
            isParticipantsConfident = true
        } else if (lower.contains("with ") || lower.contains("for ") || lower.contains("and ")) {
            for (m in members) {
                if (lower.contains(m.name.lowercase())) {
                    participantIds.add(m.id)
                }
            }
            if (payerId != null && !participantIds.contains(payerId)) {
                participantIds.add(payerId)
            }
            if (participantIds.isNotEmpty()) {
                isParticipantsConfident = true
            }
        }

        // If no explicit participants detected, default to all members
        if (participantIds.isEmpty()) {
            participantIds.addAll(members.map { it.id })
            isParticipantsConfident = false
        }

        // 4. Extract Description
        val description = extractDescription(trimmed, members)

        // 5. Infer Category
        val category = inferCategory(description)

        return ParsedExpenseDraft(
            amount = Money(extractedAmountMinor, currency),
            description = description.ifBlank { category.displayName },
            payerId = payerId,
            payerName = payerName,
            participantIds = participantIds.distinct(),
            category = category,
            splitMethod = SplitMethod.EQUAL,
            mode = PaymentMode.ONLINE,
            rawText = trimmed,
            isPayerConfident = isPayerConfident,
            isParticipantsConfident = isParticipantsConfident
        )
    }

    private fun parseAmountMinor(numStr: String): Long? {
        return try {
            val parts = numStr.split(".")
            val major = parts[0].toLong()
            val minor = if (parts.size > 1) {
                val dec = parts[1]
                if (dec.length == 1) dec.toLong() * 10 else dec.take(2).toLong()
            } else {
                0L
            }
            major * 100 + minor
        } catch (_: Exception) {
            null
        }
    }

    private fun extractDescription(text: String, members: List<Member>): String {
        // Strip out amount, currency symbols, words like "paid", "by", "for", "with", and member names
        var clean = text
        clean = clean.replace(Regex("""(?i)(?:₹|rs\.?|inr)\s*[0-9,]+(?:\.[0-9]{1,2})?"""), "")
        clean = clean.replace(Regex("""(?i)\b[0-9,]+(?:\.[0-9]{1,2})?\b"""), "")
        clean = clean.replace(Regex("""(?i)\b(paid by|paid for|paid|for everyone|for all|everyone|all|with|for)\b"""), " ")

        for (m in members) {
            clean = clean.replace(Regex("(?i)\\b${Regex.escape(m.name)}\\b"), " ")
        }
        clean = clean.replace(Regex("""(?i)\b(me|i|we)\b"""), " ")

        val desc = clean.replace(Regex("""\s+"""), " ").trim()
        return if (desc.length in 2..50) desc else "Expense"
    }

    private fun inferCategory(description: String): ExpenseCategory {
        val upper = description.uppercase()
        return when {
            upper.contains("DINNER") || upper.contains("LUNCH") || upper.contains("BREAKFAST") ||
                    upper.contains("FOOD") || upper.contains("PIZZA") || upper.contains("BURGER") ||
                    upper.contains("CAFE") || upper.contains("DRINKS") || upper.contains("BEER") ||
                    upper.contains("RESTAURANT") || upper.contains("SNACKS") -> ExpenseCategory.FOOD

            upper.contains("TAXI") || upper.contains("CAB") || upper.contains("UBER") ||
                    upper.contains("OLA") || upper.contains("METRO") || upper.contains("BUS") ||
                    upper.contains("TRAIN") || upper.contains("FLIGHT") -> ExpenseCategory.TRANSPORT

            upper.contains("PETROL") || upper.contains("DIESEL") || upper.contains("GAS") ||
                    upper.contains("FUEL") -> ExpenseCategory.FUEL

            upper.contains("HOTEL") || upper.contains("RESORT") || upper.contains("STAY") ||
                    upper.contains("ROOM") || upper.contains("AIRBNB") -> ExpenseCategory.ACCOMMODATION

            upper.contains("TICKETS") || upper.contains("ENTRY") || upper.contains("PASS") ||
                    upper.contains("CINEMA") || upper.contains("MOVIE") -> ExpenseCategory.TICKETS

            upper.contains("SHOPPING") || upper.contains("GROCERY") || upper.contains("MALL") ||
                    upper.contains("MART") -> ExpenseCategory.SHOPPING

            upper.contains("WIFI") || upper.contains("BILL") || upper.contains("ELECTRICITY") ||
                    upper.contains("WATER") -> ExpenseCategory.UTILITIES

            upper.contains("ACTIVITY") || upper.contains("BOATING") || upper.contains("PARK") ||
                    upper.contains("SCUBA") || upper.contains("TREK") -> ExpenseCategory.ACTIVITIES

            else -> ExpenseCategory.OTHER
        }
    }
}
