package com.fairshare.android.feature.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.model.Expense
import com.fairshare.android.core.model.Member
import com.fairshare.android.core.model.Money
import com.fairshare.android.core.model.PaymentMode
import java.util.UUID

@Composable
fun AddExpenseDialog(
    members: List<Member>,
    onDismiss: () -> Unit,
    onSaveExpense: (Expense) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedPayerId by remember { mutableStateOf(members.firstOrNull()?.id ?: "") }
    var paymentMode by remember { mutableStateOf(PaymentMode.ONLINE) }
    val involvedMembers = remember { mutableStateListOf<String>().apply { addAll(members.map { it.id }) } }

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
                Text(
                    text = "Add New Expense",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Description Input
                Text(
                    text = "Description",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("e.g. Dinner, Fuel, Groceries", color = FairShareTheme.colors.disabled) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Amount Input (₹)
                Text(
                    text = "Amount (₹)",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) {
                            amountText = input
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

                Spacer(modifier = Modifier.height(12.dp))

                // Payment Mode Toggle (Online vs Cash)
                Text(
                    text = "Payment Mode",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isOnline = paymentMode == PaymentMode.ONLINE
                    val isCash = paymentMode == PaymentMode.CASH

                    Surface(
                        shape = FairShareTheme.shapes.button,
                        color = if (isOnline) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isOnline) FairShareTheme.colors.accent else FairShareTheme.colors.border
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { paymentMode = PaymentMode.ONLINE }
                    ) {
                        Text(
                            text = "Online / UPI",
                            style = FairShareTheme.typography.section,
                            color = if (isOnline) FairShareTheme.colors.accent else FairShareTheme.colors.textSecondary,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                        )
                    }

                    Surface(
                        shape = FairShareTheme.shapes.button,
                        color = if (isCash) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCash) FairShareTheme.colors.accent else FairShareTheme.colors.border
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { paymentMode = PaymentMode.CASH }
                    ) {
                        Text(
                            text = "Cash",
                            style = FairShareTheme.typography.section,
                            color = if (isCash) FairShareTheme.colors.accent else FairShareTheme.colors.textSecondary,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Split With Members
                Text(
                    text = "Split With (${involvedMembers.size}/${members.size})",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    members.forEach { member ->
                        val isSelected = involvedMembers.contains(member.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isSelected) {
                                        if (involvedMembers.size > 1) involvedMembers.remove(member.id)
                                    } else {
                                        involvedMembers.add(member.id)
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) involvedMembers.add(member.id)
                                    else if (involvedMembers.size > 1) involvedMembers.remove(member.id)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = FairShareTheme.colors.accent,
                                    uncheckedColor = FairShareTheme.colors.border
                                )
                            )
                            Text(
                                text = member.name,
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
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
                        text = "Save",
                        onClick = {
                            val parsedRupees = amountText.toDoubleOrNull() ?: 0.0
                            if (parsedRupees > 0 && description.isNotBlank()) {
                                onSaveExpense(
                                    Expense(
                                        id = UUID.randomUUID().toString(),
                                        payerId = selectedPayerId,
                                        amount = Money.fromRupees(parsedRupees),
                                        involvedMemberIds = involvedMembers.toList(),
                                        mode = paymentMode,
                                        description = description.trim()
                                    )
                                )
                            }
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f),
                        enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0.0 && description.isNotBlank()
                    )
                }
            }
        }
    }
}
