package com.fairshare.android.core.domain.transactions

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.PaymentMode
import java.util.UUID
import java.util.regex.Pattern

object TransactionParser {

    private val AMOUNT_PATTERNS = listOf(
        Pattern.compile("""(?:paid|debited|spent|sent|transferred|withdrawn|purchase of)\s+(?:inr|rs\.?|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:inr|rs\.?|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s+(?:paid|debited|spent|sent)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:debited by|transferred for)\s+(?:inr|rs\.?|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE)
    )

    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("""(?:to|at|info:)\s+([A-Za-z0-9&'._\- ]{2,35}?)(?:\s+(?:on|via|using|ref|txn|avl|bal|ending|dt|from)|[.,;]|$)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:vpa|upi:)\s*([a-zA-Z0-9.\-_@]+)""", Pattern.CASE_INSENSITIVE)
    )

    fun parse(
        rawText: String,
        source: CandidateSource = CandidateSource.NOTIFICATION,
        currency: Currency = Currency.INR,
        timestamp: Long = System.currentTimeMillis()
    ): TransactionCandidate? {
        val trimmed = rawText.trim()
        if (trimmed.length < 10) return null

        // 1. Amount extraction
        var extractedMinor: Long? = null
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                val numStr = matcher.group(1)?.replace(",", "") ?: continue
                extractedMinor = parseAmountMinor(numStr)
                if (extractedMinor != null) break
            }
        }

        if (extractedMinor == null || extractedMinor <= 0L) {
            return null // Do not guess if amount cannot be detected confidently
        }

        // 2. Merchant extraction
        var extractedMerchant: String? = null
        for (pattern in MERCHANT_PATTERNS) {
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                val cand = matcher.group(1)?.trim()
                if (!cand.isNullOrBlank() && !isNoiseWord(cand)) {
                    extractedMerchant = cleanMerchantName(cand)
                    break
                }
            }
        }

        // 3. Category inference based on merchant name and keywords
        val inferredCategory = inferCategory(extractedMerchant, trimmed)

        // 4. Payment mode inference
        val inferredMode = inferPaymentMode(trimmed)

        return TransactionCandidate(
            id = UUID.randomUUID().toString(),
            rawSource = source,
            rawText = trimmed,
            amount = Money(extractedMinor, currency),
            detectedMerchant = extractedMerchant,
            detectedTimestamp = timestamp,
            inferredCategory = inferredCategory,
            inferredMode = inferredMode,
            status = CandidateStatus.PENDING
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

    private fun isNoiseWord(word: String): Boolean {
        val upper = word.uppercase()
        val noise = listOf("YOUR", "ACCOUNT", "A/C", "BANK", "CARD", "THE", "AN", "CREDITED", "OTP", "DEAR")
        return noise.contains(upper) || upper.startsWith("XX")
    }

    private fun cleanMerchantName(merchant: String): String {
        return merchant.replace(Regex("""(?i)\b(pvt|ltd|limited|inc|corp|upi|vpa)\b"""), "")
            .replace(Regex("""[^a-zA-Z0-9&' ]"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private fun inferCategory(merchant: String?, fullText: String): ExpenseCategory {
        val combined = "${merchant ?: ""} $fullText".uppercase()
        return when {
            combined.contains("SWIGGY") || combined.contains("ZOMATO") || combined.contains("RESTAURANT") ||
                    combined.contains("CAFE") || combined.contains("DINER") || combined.contains("PIZZA") ||
                    combined.contains("BURGER") || combined.contains("FOOD") || combined.contains("BAKERY") -> ExpenseCategory.FOOD

            combined.contains("UBER") || combined.contains("OLA") || combined.contains("METRO") ||
                    combined.contains("CAB") || combined.contains("AUTO") || combined.contains("BUS") ||
                    combined.contains("RAILWAY") || combined.contains("IRCTC") || combined.contains("FLIGHT") -> ExpenseCategory.TRANSPORT

            combined.contains("PETROL") || combined.contains("DIESEL") || combined.contains("FUEL") ||
                    combined.contains("HPCL") || combined.contains("BPCL") || combined.contains("IOCL") ||
                    combined.contains("SHELL") -> ExpenseCategory.FUEL

            combined.contains("HOTEL") || combined.contains("AIRBNB") || combined.contains("RESORT") ||
                    combined.contains("STAY") || combined.contains("OYO") || combined.contains("LODGE") -> ExpenseCategory.ACCOMMODATION

            combined.contains("BOOKMYSHOW") || combined.contains("CINEMA") || combined.contains("PVR") ||
                    combined.contains("INOX") || combined.contains("TICKET") || combined.contains("PARK") ||
                    combined.contains("MUSEUM") -> ExpenseCategory.ACTIVITIES

            combined.contains("AMAZON") || combined.contains("FLIPKART") || combined.contains("MYNTRA") ||
                    combined.contains("MART") || combined.contains("STORE") || combined.contains("MALL") -> ExpenseCategory.SHOPPING

            combined.contains("ELECTRICITY") || combined.contains("WATER") || combined.contains("WIFI") ||
                    combined.contains("BROADBAND") || combined.contains("GAS") -> ExpenseCategory.UTILITIES

            else -> ExpenseCategory.OTHER
        }
    }

    private fun inferPaymentMode(fullText: String): PaymentMode {
        val upper = fullText.uppercase()
        return when {
            upper.contains("UPI") || upper.contains("GPAY") || upper.contains("PHONEPE") || upper.contains("PAYTM") -> PaymentMode.UPI
            upper.contains("CARD") || upper.contains("DEBIT") || upper.contains("CREDIT") || upper.contains("VISA") || upper.contains("MASTERCARD") -> PaymentMode.CARD
            upper.contains("WALLET") -> PaymentMode.WALLET
            else -> PaymentMode.ONLINE
        }
    }
}
