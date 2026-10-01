package com.fairshare.android.feature.expenses

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FairShareTheme

enum class ExpenseEntryMode {
    MANUAL,
    QUICK_ENTRY,
    NATURAL_LANGUAGE,
    RECEIPT_SCAN,
    TRANSACTION_IMPORT,
    SIMULATOR
}

@Composable
fun AddExpenseRouterDialog(
    onSelectMode: (ExpenseEntryMode) -> Unit,
    onDismiss: () -> Unit
) {
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
                    text = "Add Expense",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose how you want to record or test an expense",
                    style = FairShareTheme.typography.supporting,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Manual
                RouterOptionCard(
                    title = "Manual Entry",
                    subtitle = "Enter amount, payer, participants, and custom split modes",
                    onClick = { onSelectMode(ExpenseEntryMode.MANUAL) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Quick Entry
                RouterOptionCard(
                    title = "Quick Entry",
                    subtitle = "Fast single-screen expense entry with quick splits",
                    onClick = { onSelectMode(ExpenseEntryMode.QUICK_ENTRY) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Smart Natural-Language & Voice
                RouterOptionCard(
                    title = "Smart Text / Voice Input",
                    subtitle = "Type or speak phrases like 'paid 1200 for dinner with Arjun and Neha'",
                    onClick = { onSelectMode(ExpenseEntryMode.NATURAL_LANGUAGE) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option 3: Scan Receipt
                RouterOptionCard(
                    title = "Scan Receipt",
                    subtitle = "On-device OCR extracts merchant, total, and line items for review",
                    onClick = { onSelectMode(ExpenseEntryMode.RECEIPT_SCAN) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option 4: Transaction Detection / Import
                RouterOptionCard(
                    title = "Transaction Detection & Import",
                    subtitle = "Review detected bank/UPI payment notifications or paste SMS text",
                    onClick = { onSelectMode(ExpenseEntryMode.TRANSACTION_IMPORT) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option 5: What-If Simulator
                RouterOptionCard(
                    title = "What-If Simulator",
                    subtitle = "Preview hypothetical balance and budget impacts with zero state mutation",
                    onClick = { onSelectMode(ExpenseEntryMode.SIMULATOR) }
                )

                Spacer(modifier = Modifier.height(18.dp))

                FSButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    variant = FSButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RouterOptionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = FairShareTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, FairShareTheme.colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = FairShareTheme.typography.section,
                color = FairShareTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = FairShareTheme.typography.metadata,
                color = FairShareTheme.colors.textSecondary
            )
        }
    }
}
