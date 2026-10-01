package com.fairshare.android.feature.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SettlementPayment
import java.util.UUID

@Composable
fun RecordPaymentDialog(
    members: List<Member>,
    groupId: String = "",
    initialFromMemberId: String? = null,
    initialToMemberId: String? = null,
    suggestedAmount: Money? = null,
    onDismiss: () -> Unit,
    onSavePayment: suspend (SettlementPayment) -> Unit
) {
    var fromMemberId by remember {
        mutableStateOf(
            initialFromMemberId ?: members.firstOrNull()?.id ?: ""
        )
    }
    var toMemberId by remember {
        mutableStateOf(
            initialToMemberId ?: members.getOrNull(1)?.id ?: members.firstOrNull()?.id ?: ""
        )
    }

    val initialAmountText = suggestedAmount?.let {
        String.format(java.util.Locale.US, "%.2f", it.amountMinor / 100.0)
    } ?: ""

    var amountText by remember { mutableStateOf(initialAmountText) }
    var selectedMode by remember { mutableStateOf(PaymentMode.UPI) }
    var noteText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    var expandedFromMenu by remember { mutableStateOf(false) }
    var expandedToMenu by remember { mutableStateOf(false) }
    var expandedModeMenu by remember { mutableStateOf(false) }

    val fromMember = members.firstOrNull { it.id == fromMemberId }
    val toMember = members.firstOrNull { it.id == toMemberId }

    val parsedRupees = amountText.toDoubleOrNull() ?: 0.0
    val parsedMinor = (parsedRupees * 100).toLong()
    val paymentMoney = Money(parsedMinor, suggestedAmount?.currency ?: Currency.INR)

    val remainingMoney = if (suggestedAmount != null && parsedMinor > 0L) {
        val remMinor = suggestedAmount.amountMinor - parsedMinor
        if (remMinor > 0L) Money(remMinor, suggestedAmount.currency) else Money.zero(suggestedAmount.currency)
    } else null

    val isPartial = suggestedAmount != null && parsedMinor > 0L && parsedMinor < suggestedAmount.amountMinor
    val isExactFull = suggestedAmount != null && parsedMinor == suggestedAmount.amountMinor
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Record Settlement Payment",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    if (isPartial) {
                        FSStatusBadge(
                            text = "Partial",
                            textColor = FairShareTheme.colors.accent,
                            backgroundColor = FairShareTheme.colors.accentSoft
                        )
                    } else if (isExactFull) {
                        FSStatusBadge(
                            text = "Full",
                            textColor = FairShareTheme.colors.positive,
                            backgroundColor = FairShareTheme.colors.positiveSoft
                        )
                    }
                }

                if (saveErrorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FairShareTheme.colors.negative.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .border(1.dp, FairShareTheme.colors.negative, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = saveErrorMessage!!,
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.negative
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Payer (From)
                Text(
                    text = "Payer (Who Paid)",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(FairShareTheme.colors.surface)
                            .clickable { expandedFromMenu = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = fromMember?.name ?: "Select payer",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "▼",
                            fontSize = 10.sp,
                            color = FairShareTheme.colors.textTertiary
                        )
                    }
                    DropdownMenu(
                        expanded = expandedFromMenu,
                        onDismissRequest = { expandedFromMenu = false },
                        modifier = Modifier.background(FairShareTheme.colors.surfaceElevated)
                    ) {
                        members.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = m.name + if (m.isCurrentUser) " (You)" else "",
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary
                                    )
                                },
                                onClick = {
                                    fromMemberId = m.id
                                    expandedFromMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Recipient (To)
                Text(
                    text = "Recipient (Who Received)",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(FairShareTheme.colors.surface)
                            .clickable { expandedToMenu = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = toMember?.name ?: "Select recipient",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "▼",
                            fontSize = 10.sp,
                            color = FairShareTheme.colors.textTertiary
                        )
                    }
                    DropdownMenu(
                        expanded = expandedToMenu,
                        onDismissRequest = { expandedToMenu = false },
                        modifier = Modifier.background(FairShareTheme.colors.surfaceElevated)
                    ) {
                        members.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = m.name + if (m.isCurrentUser) " (You)" else "",
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary
                                    )
                                },
                                onClick = {
                                    toMemberId = m.id
                                    expandedToMenu = false
                                }
                            )
                        }
                    }
                }

                if (fromMemberId == toMemberId) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Payer and recipient cannot be the same member",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.negative
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Amount
                Text(
                    text = "Amount Paid (₹)",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) {
                            amountText = input
                            saveErrorMessage = null
                        }
                    },
                    placeholder = { Text("0.00", color = FairShareTheme.colors.disabled) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Chips for Suggested / Partial
                if (suggestedAmount != null && suggestedAmount.amountMinor > 0L) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val fullText = String.format(java.util.Locale.US, "%.2f", suggestedAmount.amountMinor / 100.0)
                        val halfMinor = suggestedAmount.amountMinor / 2
                        val halfText = String.format(java.util.Locale.US, "%.2f", halfMinor / 100.0)

                        Box(
                            modifier = Modifier
                                .border(1.dp, FairShareTheme.colors.accent, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(FairShareTheme.colors.accentSoft)
                                .clickable { amountText = fullText }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Full: ${suggestedAmount.formatted()}",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.accent,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (halfMinor > 0L) {
                            Box(
                                modifier = Modifier
                                .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(FairShareTheme.colors.surface)
                                .clickable { amountText = halfText }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Half: ${Money(halfMinor, suggestedAmount.currency).formatted()}",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                            }
                        }
                    }

                    // Remaining breakdown
                    if (remainingMoney != null && remainingMoney.amountMinor > 0L) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FairShareTheme.colors.surface, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Remaining obligation after this payment:",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textSecondary
                            )
                            Text(
                                text = remainingMoney.formatted(),
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.negative,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Payment Mode
                Text(
                    text = "Payment Mode",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(FairShareTheme.colors.surface)
                            .clickable { expandedModeMenu = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val modeLabel = when (selectedMode) {
                            PaymentMode.UPI -> "UPI"
                            PaymentMode.CASH -> "Cash"
                            PaymentMode.CARD -> "Card / Netbanking"
                            PaymentMode.WALLET -> "Wallet"
                            PaymentMode.ONLINE -> "Online"
                            PaymentMode.OTHER -> "Other"
                        }
                        Text(
                            text = modeLabel,
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Text(
                            text = "▼",
                            fontSize = 10.sp,
                            color = FairShareTheme.colors.textTertiary
                        )
                    }
                    DropdownMenu(
                        expanded = expandedModeMenu,
                        onDismissRequest = { expandedModeMenu = false },
                        modifier = Modifier.background(FairShareTheme.colors.surfaceElevated)
                    ) {
                        PaymentMode.values().forEach { mode ->
                            val label = when (mode) {
                                PaymentMode.UPI -> "UPI"
                                PaymentMode.CASH -> "Cash"
                                PaymentMode.CARD -> "Card / Netbanking"
                                PaymentMode.WALLET -> "Wallet"
                                PaymentMode.ONLINE -> "Online"
                                PaymentMode.OTHER -> "Other"
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary
                                    )
                                },
                                onClick = {
                                    selectedMode = mode
                                    expandedModeMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Note / Reference
                Text(
                    text = "Note or Reference (Optional)",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("e.g. GPay ref / settlement", color = FairShareTheme.colors.disabled) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FSButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                        enabled = !isSubmitting
                    )
                    FSButton(
                        text = if (isSubmitting) "Saving..." else if (isPartial) "Record Partial" else "Record Payment",
                        onClick = {
                            if (isSubmitting) return@FSButton
                            if (parsedMinor > 0L && fromMemberId != toMemberId) {
                                isSubmitting = true
                                saveErrorMessage = null
                                val payment = SettlementPayment(
                                    id = UUID.randomUUID().toString(),
                                    fromMemberId = fromMemberId,
                                    toMemberId = toMemberId,
                                    amount = paymentMoney,
                                    timestamp = System.currentTimeMillis(),
                                    mode = selectedMode,
                                    note = noteText.ifBlank { null },
                                    groupId = groupId
                                )
                                scope.launch {
                                    try {
                                        onSavePayment(payment)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        saveErrorMessage = e.localizedMessage ?: "Failed to record payment. Please try again."
                                        isSubmitting = false
                                    }
                                }
                            }
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f),
                        enabled = !isSubmitting && parsedMinor > 0L && fromMemberId.isNotBlank() && toMemberId.isNotBlank() && fromMemberId != toMemberId
                    )
                }
            }
        }
    }
}
