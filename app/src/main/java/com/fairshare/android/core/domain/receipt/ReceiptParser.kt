package com.fairshare.android.core.domain.receipt

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

object ReceiptParser {

    private val EXCLUDE_MERCHANT_WORDS = listOf(
        "TAX", "INVOICE", "RECEIPT", "BILL", "WELCOME", "CASH", "MEMO", "ORDER",
        "DUPLICATE", "CUSTOMER", "COPY", "TABLE", "DATE", "TIME", "TOKEN", "GUEST",
        "GSTIN", "FSSAI", "PHONE", "TEL", "MOBILE", "TOTAL", "SUBTOTAL", "AMOUNT",
        "THANK YOU", "VISIT AGAIN", "FEEDBACK"
    )

    private val TOTAL_KEYWORDS = listOf(
        "GRAND TOTAL", "NET TOTAL", "TOTAL DUE", "TOTAL AMOUNT", "TOTAL PAYABLE",
        "NET PAYABLE", "FINAL AMOUNT", "BILL TOTAL", "AMOUNT DUE", "TOTAL"
    )
    private val SUBTOTAL_KEYWORDS = listOf("SUB TOTAL", "SUBTOTAL", "SUB-TOTAL", "NET AMOUNT", "ITEMS TOTAL")
    private val TAX_KEYWORDS = listOf("GST", "CGST", "SGST", "IGST", "VAT", "SALES TAX", "SERVICE TAX", "TAX", "TAXES")
    private val SERVICE_CHARGE_KEYWORDS = listOf("SERVICE CHARGE", "SERVICE CHG", "SERVICE FEE", "TIP", "GRATUITY")
    private val DISCOUNT_KEYWORDS = listOf("DISCOUNT", "LESS", "OFFER", "SAVINGS", "DISC")

    fun parse(rawText: String, defaultCurrency: Currency = Currency.INR): ParsedReceipt {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        
        // Detect currency symbol if present in receipt text (₹, $, €, £)
        val activeCurrency = when {
            rawText.contains("₹") || rawText.contains("INR", ignoreCase = true) || rawText.contains("Rs", ignoreCase = true) -> Currency.INR
            rawText.contains("$") || rawText.contains("USD", ignoreCase = true) -> Currency.USD
            rawText.contains("€") || rawText.contains("EUR", ignoreCase = true) -> Currency.EUR
            rawText.contains("£") || rawText.contains("GBP", ignoreCase = true) -> Currency.GBP
            else -> defaultCurrency
        }

        if (lines.isEmpty()) {
            return ParsedReceipt(
                merchant = null,
                merchantConfidence = ReceiptConfidenceState.NOT_DETECTED,
                invoiceId = null,
                totalAmount = Money.zero(activeCurrency),
                totalConfidence = ReceiptConfidenceState.NOT_DETECTED,
                currency = activeCurrency,
                rawText = rawText,
                arithmeticValid = false,
                validationNotes = listOf("No readable text found on receipt")
            )
        }

        // 1. Merchant detection: scan top non-excluded lines
        var detectedMerchant: String? = null
        var merchantConfidence = ReceiptConfidenceState.NOT_DETECTED
        for (i in 0 until minOf(5, lines.size)) {
            val line = lines[i]
            val upper = line.uppercase()
            val containsExclude = EXCLUDE_MERCHANT_WORDS.any { upper.contains(it) }
            val hasDigitsOnlyOrSymbols = line.all { it.isDigit() || it.isWhitespace() || it == '-' || it == ':' || it == '/' || it == '#' || it == '.' }
            if (!containsExclude && !hasDigitsOnlyOrSymbols && line.length in 3..50) {
                detectedMerchant = line
                merchantConfidence = if (i == 0) ReceiptConfidenceState.DETECTED else ReceiptConfidenceState.NEEDS_REVIEW
                break
            }
        }

        // 2. Invoice / Bill / Receipt ID detection
        var detectedInvoiceId: String? = null
        val invoiceRegex = Pattern.compile(
            """(?:INVOICE|BILL|RECEIPT|TOKEN|ORDER|TAX\s+INVOICE|INV|REC)\s*(?:NO|NUMBER|ID|#)?\s*[:\s#]\s*([A-Za-z0-9\-_/]+)""",
            Pattern.CASE_INSENSITIVE
        )
        for (line in lines) {
            val matcher = invoiceRegex.matcher(line)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim()
                if (!candidate.isNullOrBlank() && candidate.length >= 2 && candidate.any { it.isDigit() || it.isLetter() }) {
                    detectedInvoiceId = candidate
                    break
                }
            }
        }

        // 3. Date detection
        var detectedDateMillis: Long? = null
        val dateRegex = Pattern.compile(
            """\b(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}|\d{4}[/\-.]\d{1,2}[/\-.]\d{1,2}|\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{2,4})\b""",
            Pattern.CASE_INSENSITIVE
        )
        val dateFormats = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd.MM.yyyy", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
            SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yy", Locale.ENGLISH),
            SimpleDateFormat("dd-MM-yy", Locale.ENGLISH),
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)
        )
        for (line in lines) {
            val matcher = dateRegex.matcher(line)
            if (matcher.find()) {
                val dateStr = matcher.group(1)?.trim() ?: continue
                for (fmt in dateFormats) {
                    try {
                        val parsed = fmt.parse(dateStr)
                        if (parsed != null && parsed.time > 0L) {
                            detectedDateMillis = parsed.time
                            break
                        }
                    } catch (_: Exception) {}
                }
                if (detectedDateMillis != null) break
            }
        }

        // 4. Extract totals, subtotal, tax, service, discount & line items
        var detectedTotalMinor: Long? = null
        var totalConfidence = ReceiptConfidenceState.NOT_DETECTED
        var detectedSubtotalMinor: Long? = null
        var detectedTaxMinor: Long? = null
        var detectedServiceMinor: Long? = null
        var detectedDiscountMinor: Long? = null

        val parsedItems = mutableListOf<ReceiptLineItem>()

        for (line in lines) {
            val upper = line.uppercase()

            // Check subtotal first so "SUBTOTAL" is not caught by total
            if (matchesKeyword(upper, SUBTOTAL_KEYWORDS) && detectedSubtotalMinor == null) {
                extractAmountMinor(line)?.let { detectedSubtotalMinor = it }
                continue
            }

            // Check total with priority keywords
            if (matchesKeyword(upper, TOTAL_KEYWORDS) && detectedTotalMinor == null) {
                extractAmountMinor(line)?.let {
                    detectedTotalMinor = it
                    totalConfidence = ReceiptConfidenceState.DETECTED
                }
                continue
            }

            // Check tax
            if (matchesKeyword(upper, TAX_KEYWORDS) && detectedTaxMinor == null) {
                extractAmountMinor(line)?.let { detectedTaxMinor = it }
                continue
            }

            // Check service charge
            if (matchesKeyword(upper, SERVICE_CHARGE_KEYWORDS) && detectedServiceMinor == null) {
                extractAmountMinor(line)?.let { detectedServiceMinor = it }
                continue
            }

            // Check discount
            if (matchesKeyword(upper, DISCOUNT_KEYWORDS) && detectedDiscountMinor == null) {
                extractAmountMinor(line)?.let { detectedDiscountMinor = it }
                continue
            }

            // Potential line item: contains a description and an amount at the end
            val itemAmount = extractLineItemAmount(line)
            if (itemAmount != null && itemAmount > 0L) {
                val itemName = extractLineItemName(line)
                val isExcludedItem = EXCLUDE_MERCHANT_WORDS.any { upper.contains(it) } ||
                        matchesKeyword(upper, TOTAL_KEYWORDS + SUBTOTAL_KEYWORDS + TAX_KEYWORDS + SERVICE_CHARGE_KEYWORDS + DISCOUNT_KEYWORDS)
                if (itemName.isNotBlank() && !isExcludedItem && itemName.length in 2..60) {
                    parsedItems.add(
                        ReceiptLineItem(
                            id = UUID.randomUUID().toString(),
                            name = itemName,
                            amountMinor = itemAmount,
                            quantity = 1,
                            confidence = ReceiptConfidenceState.DETECTED
                        )
                    )
                }
            }
        }

        // Fallback for total: look at items sum or maximum bottom monetary value (NEVER use top-of-receipt number)
        if (detectedTotalMinor == null) {
            if (parsedItems.isNotEmpty()) {
                val sumItems = parsedItems.sumOf { it.amountMinor }
                val tax = detectedTaxMinor ?: 0L
                val service = detectedServiceMinor ?: 0L
                val discount = detectedDiscountMinor ?: 0L
                detectedTotalMinor = sumItems + tax + service - discount
                totalConfidence = ReceiptConfidenceState.NEEDS_REVIEW
            } else {
                // Take amounts ONLY from bottom half / last lines of receipt
                val searchLines = if (lines.size > 4) lines.drop(lines.size / 2) else lines
                val amounts = searchLines.mapNotNull { extractAmountMinor(it) }
                if (amounts.isNotEmpty()) {
                    detectedTotalMinor = amounts.maxOrNull()
                    totalConfidence = ReceiptConfidenceState.NEEDS_REVIEW
                } else {
                    detectedTotalMinor = 0L
                    totalConfidence = ReceiptConfidenceState.NOT_DETECTED
                }
            }
        }

        // Arithmetic validation
        val validationNotes = mutableListOf<String>()
        var arithmeticValid = true

        val total = detectedTotalMinor ?: 0L
        val subtotal = detectedSubtotalMinor
        val tax = detectedTaxMinor ?: 0L
        val service = detectedServiceMinor ?: 0L
        val discount = detectedDiscountMinor ?: 0L

        if (subtotal != null && total > 0L) {
            val expectedTotal = subtotal + tax + service - discount
            if (kotlin.math.abs(expectedTotal - total) > 5) {
                arithmeticValid = false
                validationNotes.add("Calculated subtotal + tax (${Money(expectedTotal, activeCurrency).formatted()}) differs from detected total (${Money(total, activeCurrency).formatted()})")
            }
        }

        if (parsedItems.isNotEmpty()) {
            val itemsSum = parsedItems.sumOf { it.amountMinor }
            val checkTarget = subtotal ?: total
            if (subtotal != null && kotlin.math.abs(itemsSum - checkTarget) > 5) {
                validationNotes.add("Sum of line items (${Money(itemsSum, activeCurrency).formatted()}) differs from subtotal (${Money(checkTarget, activeCurrency).formatted()})")
            }
        }

        return ParsedReceipt(
            merchant = detectedMerchant,
            merchantConfidence = merchantConfidence,
            invoiceId = detectedInvoiceId,
            totalAmount = Money(total, activeCurrency),
            totalConfidence = totalConfidence,
            subtotalAmount = subtotal?.let { Money(it, activeCurrency) },
            taxAmount = detectedTaxMinor?.let { Money(it, activeCurrency) },
            serviceChargeAmount = detectedServiceMinor?.let { Money(it, activeCurrency) },
            discountAmount = detectedDiscountMinor?.let { Money(it, activeCurrency) },
            currency = activeCurrency,
            dateEpochMillis = detectedDateMillis ?: System.currentTimeMillis(),
            items = parsedItems,
            rawText = rawText,
            arithmeticValid = arithmeticValid,
            validationNotes = validationNotes
        )
    }

    private fun matchesKeyword(text: String, keywords: List<String>): Boolean {
        return keywords.any { kw ->
            if (kw == "TOTAL") {
                val cleaned = text.replace("SUBTOTAL", "").replace("SUB TOTAL", "").replace("SUB-TOTAL", "")
                cleaned.contains("TOTAL")
            } else {
                text.contains(kw)
            }
        }
    }

    private fun extractAmountMinor(line: String): Long? {
        // Matches amounts formatted with ₹, $, €, £, Rs, INR, USD, EUR, GBP
        val regex = Pattern.compile("""(?:[₹$€£]|RS\.?|INR|USD|EUR|GBP)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE)
        val matcher = regex.matcher(line)
        var lastFound: Long? = null
        while (matcher.find()) {
            val numStr = matcher.group(1)?.replace(",", "") ?: continue
            try {
                val parts = numStr.split(".")
                val major = parts[0].toLong()
                val minor = if (parts.size > 1) {
                    val dec = parts[1]
                    if (dec.length == 1) dec.toLong() * 10 else dec.take(2).toLong()
                } else {
                    0L
                }
                lastFound = major * 100 + minor
            } catch (_: Exception) {}
        }
        return lastFound
    }

    private fun extractLineItemAmount(line: String): Long? {
        val amountRegex = Pattern.compile("""\s+([0-9,]+(?:\.[0-9]{1,2})?)$""")
        val matcher = amountRegex.matcher(line)
        if (matcher.find()) {
            val numStr = matcher.group(1)?.replace(",", "") ?: return null
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
        return null
    }

    private fun extractLineItemName(line: String): String {
        val amountRegex = Pattern.compile("""\s+([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)$""")
        val matcher = amountRegex.matcher(line)
        if (matcher.find()) {
            val name = line.substring(0, matcher.start()).trim()
            return name.replace(Regex("""^\d+\s*[xX*]\s*"""), "").trim()
        }
        return line.trim()
    }
}
