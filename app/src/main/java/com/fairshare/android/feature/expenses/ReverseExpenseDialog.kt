package com.fairshare.android.feature.expenses

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.model.Expense

@Composable
fun ReverseExpenseDialog(
    expense: Expense,
    onConfirmReversal: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Reverse Expense",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.negative
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This will reverse this transaction in the group ledger and recalculate all member balances without deleting audit history.",
                    style = FairShareTheme.typography.supporting,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))

                FSCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = FairShareTheme.colors.surface
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = expense.description.ifBlank { "Expense" },
                            style = FairShareTheme.typography.section,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = expense.totalAmount.formatted(),
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Reason (optional)",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = { Text("e.g. Duplicate entry, Cancelled, Incorrect amount", color = FairShareTheme.colors.disabled) },
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
                        modifier = Modifier.weight(1f)
                    )
                    FSButton(
                        text = "Reverse",
                        onClick = {
                            onConfirmReversal(reason.trim())
                            onDismiss()
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
