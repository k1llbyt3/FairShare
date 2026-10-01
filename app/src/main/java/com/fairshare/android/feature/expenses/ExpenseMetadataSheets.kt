package com.fairshare.android.feature.expenses

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.PaymentMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySelectionDialog(
    selectedCategory: ExpenseCategory,
    onCategorySelected: (ExpenseCategory) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
                .imePadding()
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Select Category",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpenseCategory.values().forEach { category ->
                        val isSelected = category == selectedCategory
                        val label = category.name.lowercase().replaceFirstChar { it.titlecase() }
                        val iconStr = when (category) {
                            ExpenseCategory.FOOD -> "🍔"
                            ExpenseCategory.TRANSPORT -> "🚕"
                            ExpenseCategory.ACCOMMODATION -> "🏨"
                            ExpenseCategory.ACTIVITIES -> "🎬"
                            ExpenseCategory.SHOPPING -> "🛍️"
                            ExpenseCategory.FUEL -> "⛽"
                            ExpenseCategory.TICKETS -> "🎟️"
                            ExpenseCategory.UTILITIES -> "💡"
                            ExpenseCategory.OTHER -> "🔖"
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border
                            ),
                            modifier = Modifier
                                .clickable {
                                    onCategorySelected(category)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = iconStr, fontSize = 14.sp)
                                Text(
                                    text = label,
                                    style = FairShareTheme.typography.metadata,
                                    color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                FSButton(
                    text = "Close",
                    onClick = onDismiss,
                    variant = FSButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun PaymentModeSelectionDialog(
    selectedMode: PaymentMode,
    onModeSelected: (PaymentMode) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .imePadding()
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Payment Mode",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                PaymentMode.values().forEach { mode ->
                    val isSelected = mode == selectedMode
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable {
                                onModeSelected(mode)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (mode) {
                                    PaymentMode.UPI -> "UPI / QR"
                                    PaymentMode.ONLINE -> "Online Netbanking"
                                    PaymentMode.CARD -> "Debit / Credit Card"
                                    PaymentMode.CASH -> "Cash"
                                    PaymentMode.WALLET -> "Digital Wallet"
                                    PaymentMode.OTHER -> "Other"
                                },
                                style = FairShareTheme.typography.body,
                                color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    color = FairShareTheme.colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                FSButton(
                    text = "Close",
                    onClick = onDismiss,
                    variant = FSButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
