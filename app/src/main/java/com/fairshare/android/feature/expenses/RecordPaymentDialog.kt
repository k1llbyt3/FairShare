package com.fairshare.android.feature.expenses

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.model.Member
import com.fairshare.android.core.model.Money
import com.fairshare.android.core.model.Payment
import java.util.UUID

@Composable
fun RecordPaymentDialog(
    members: List<Member>,
    onDismiss: () -> Unit,
    onSavePayment: (Payment) -> Unit
) {
    var fromMemberId by remember { mutableStateOf(members.firstOrNull()?.id ?: "") }
    var toMemberId by remember { mutableStateOf(members.getOrNull(1)?.id ?: members.firstOrNull()?.id ?: "") }
    var amountText by remember { mutableStateOf("") }

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
                    text = "Record Direct Payment",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))

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
                        text = "Record",
                        onClick = {
                            val parsedRupees = amountText.toDoubleOrNull() ?: 0.0
                            if (parsedRupees > 0 && fromMemberId != toMemberId) {
                                onSavePayment(
                                    Payment(
                                        id = UUID.randomUUID().toString(),
                                        fromMemberId = fromMemberId,
                                        toMemberId = toMemberId,
                                        amount = Money.fromRupees(parsedRupees)
                                    )
                                )
                            }
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f),
                        enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0.0 && fromMemberId != toMemberId
                    )
                }
            }
        }
    }
}
