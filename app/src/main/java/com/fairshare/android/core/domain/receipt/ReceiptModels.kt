package com.fairshare.android.core.domain.receipt

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money

enum class ReceiptConfidenceState {
    DETECTED,
    NEEDS_REVIEW,
    NOT_DETECTED
}

data class ReceiptLineItem(
    val id: String,
    val name: String,
    val amountMinor: Long,
    val quantity: Int = 1,
    val confidence: ReceiptConfidenceState = ReceiptConfidenceState.DETECTED,
    val assignedMemberIds: List<String> = emptyList()
)

data class ParsedReceipt(
    val merchant: String?,
    val merchantConfidence: ReceiptConfidenceState = ReceiptConfidenceState.NOT_DETECTED,
    val invoiceId: String? = null,
    val totalAmount: Money,
    val totalConfidence: ReceiptConfidenceState = ReceiptConfidenceState.NOT_DETECTED,
    val subtotalAmount: Money? = null,
    val taxAmount: Money? = null,
    val serviceChargeAmount: Money? = null,
    val discountAmount: Money? = null,
    val currency: Currency = Currency.INR,
    val dateEpochMillis: Long? = null,
    val items: List<ReceiptLineItem> = emptyList(),
    val rawText: String = "",
    val arithmeticValid: Boolean = true,
    val validationNotes: List<String> = emptyList()
)
