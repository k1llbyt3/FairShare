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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.Member

@Composable
fun PayerSelectionDialog(
    totalAmount: Money,
    members: List<Member>,
    currentPayers: List<ExpensePayer>,
    onDismiss: () -> Unit,
    onConfirmPayers: (List<ExpensePayer>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isMultiplePayersMode by remember { mutableStateOf(currentPayers.size > 1) }

    // Single payer selected ID
    var selectedSinglePayerId by remember {
        mutableStateOf(currentPayers.firstOrNull()?.memberId ?: members.firstOrNull()?.id ?: "")
    }

    // Multiple payers: memberId -> amount string
    val multiPayerAmounts = remember {
        mutableStateMapOf<String, String>().apply {
            members.forEach { m ->
                val existing = currentPayers.find { it.memberId == m.id }
                this[m.id] = if (existing != null && existing.amount.amountMinor > 0L) {
                    val p = existing.amount.amountMinor
                    if (p % 100 == 0L) "${p / 100}" else String.format(java.util.Locale.ROOT, "%.2f", p / 100.0)
                } else ""
            }
        }
    }

    val filteredMembers = remember(members, searchQuery) {
        val query = searchQuery.trim().lowercase()
        val list = if (query.isEmpty()) members else members.filter { it.name.lowercase().contains(query) }
        // Emphasize current user first
        list.sortedByDescending { it.isCurrentUser }
    }

    Dialog(onDismissRequest = onDismiss) {
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
                        text = "Paid By",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Text(
                        text = if (isMultiplePayersMode) "Single Payer" else "Multiple Payers",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.accent,
                        modifier = Modifier
                            .clickable { isMultiplePayersMode = !isMultiplePayersMode }
                            .padding(4.dp)
                    )
                }

                Text(
                    text = "Total: ${totalAmount.formatted()}",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search member...", color = FairShareTheme.colors.disabled) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!isMultiplePayersMode) {
                    // Single Payer Selection List
                    filteredMembers.forEach { member ->
                        val isSelected = member.id == selectedSinglePayerId

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedSinglePayerId = member.id }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val initials = member.name.take(2).uppercase()
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(FairShareTheme.colors.surfaceElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initials,
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.accent
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = member.name,
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (member.isCurrentUser) {
                                    FSStatusBadge(
                                        text = "YOU",
                                        textColor = FairShareTheme.colors.accent,
                                        backgroundColor = FairShareTheme.colors.accentSoft
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Multiple Payers Amount Inputs
                    val parsedAmounts = members.associate { m ->
                        val text = multiPayerAmounts[m.id]?.trim() ?: ""
                        val rupees = text.toDoubleOrNull() ?: 0.0
                        m.id to (rupees * 100).toLong()
                    }
                    val totalPaidMinor = parsedAmounts.values.sum()
                    val diffMinor = totalAmount.amountMinor - totalPaidMinor
                    val isValidMulti = diffMinor == 0L && totalAmount.amountMinor > 0L

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Paid sum: ${Money(totalPaidMinor, totalAmount.currency).formatted()}",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                        val statusText = when {
                            diffMinor == 0L -> "Exact match"
                            diffMinor > 0L -> "${Money(diffMinor, totalAmount.currency).formatted()} remaining"
                            else -> "${Money(-diffMinor, totalAmount.currency).formatted()} over total"
                        }
                        Text(
                            text = statusText,
                            style = FairShareTheme.typography.metadata,
                            color = if (isValidMulti) FairShareTheme.colors.positive else FairShareTheme.colors.negative,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    filteredMembers.forEach { member ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = if (member.isCurrentUser) "${member.name} (You)" else member.name,
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = multiPayerAmounts[member.id] ?: "",
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() || it == '.' }) {
                                        multiPayerAmounts[member.id] = input
                                    }
                                },
                                placeholder = { Text("0.00", color = FairShareTheme.colors.disabled) },
                                prefix = { Text("₹", color = FairShareTheme.colors.textSecondary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FairShareTheme.colors.accent,
                                    unfocusedBorderColor = FairShareTheme.colors.border,
                                    focusedTextColor = FairShareTheme.colors.textPrimary,
                                    unfocusedTextColor = FairShareTheme.colors.textPrimary
                                ),
                                modifier = Modifier.width(130.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FSButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    FSButton(
                        text = "Confirm Payer",
                        onClick = {
                            if (!isMultiplePayersMode) {
                                onConfirmPayers(
                                    listOf(
                                        ExpensePayer(
                                            memberId = selectedSinglePayerId,
                                            amount = totalAmount
                                        )
                                    )
                                )
                            } else {
                                val payers = members.mapNotNull { m ->
                                    val text = multiPayerAmounts[m.id]?.trim() ?: ""
                                    val rupees = text.toDoubleOrNull() ?: 0.0
                                    val minor = (rupees * 100).toLong()
                                    if (minor > 0L) {
                                        ExpensePayer(
                                            memberId = m.id,
                                            amount = Money(minor, totalAmount.currency)
                                        )
                                    } else null
                                }
                                onConfirmPayers(payers)
                            }
                            onDismiss()
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f),
                        enabled = if (!isMultiplePayersMode) {
                            selectedSinglePayerId.isNotBlank()
                        } else {
                            val parsedSum = members.sumOf { m ->
                                val text = multiPayerAmounts[m.id]?.trim() ?: ""
                                val rupees = text.toDoubleOrNull() ?: 0.0
                                (rupees * 100).toLong()
                            }
                            parsedSum == totalAmount.amountMinor && totalAmount.amountMinor > 0L
                        }
                    )
                }
            }
        }
    }
}
