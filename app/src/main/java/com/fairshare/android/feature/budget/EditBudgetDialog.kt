package com.fairshare.android.feature.budget

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.ExpenseCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun EditBudgetDialog(
    initialPlan: BudgetPlan?,
    currency: Currency = Currency.INR,
    onDismiss: () -> Unit,
    onSaveBudget: (BudgetPlan) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }

    val initialBaseRupees = initialPlan?.baseBudget?.let {
        String.format(Locale.US, "%.2f", it.amountMinor / 100.0)
    } ?: ""
    val initialBufferRupees = initialPlan?.emergencyBuffer?.let {
        if (it.amountMinor > 0L) String.format(Locale.US, "%.2f", it.amountMinor / 100.0) else ""
    } ?: ""

    val now = System.currentTimeMillis()
    val initialStartStr = initialPlan?.let { if (it.startDateEpochMs > 0L) dateFormat.format(Date(it.startDateEpochMs)) else "" }
        ?: dateFormat.format(Date(now))
    val initialEndStr = initialPlan?.let { if (it.endDateEpochMs > 0L) dateFormat.format(Date(it.endDateEpochMs)) else "" }
        ?: dateFormat.format(Date(now + 7 * 86_400_000L))

    var baseAmountText by remember { mutableStateOf(initialBaseRupees) }
    var bufferAmountText by remember { mutableStateOf(initialBufferRupees) }
    var startDateText by remember { mutableStateOf(initialStartStr) }
    var endDateText by remember { mutableStateOf(initialEndStr) }
    var timezoneText by remember { mutableStateOf(initialPlan?.timezone ?: TimeZone.getDefault().id) }
    var alertThreshold by remember { mutableStateOf(initialPlan?.alertThresholdPercentage ?: 80) }

    // Category Allocations
    val categoryAllocations = remember {
        mutableStateMapOf<ExpenseCategory, String>().apply {
            initialPlan?.categoryAllocations?.forEach { (cat, money) ->
                put(cat, String.format(Locale.US, "%.2f", money.amountMinor / 100.0))
            }
        }
    }

    var showAddCategoryMenu by remember { mutableStateOf(false) }

    val parsedBase = baseAmountText.toDoubleOrNull() ?: 0.0
    val parsedBuffer = bufferAmountText.toDoubleOrNull() ?: 0.0
    val isValidBase = parsedBase > 0.0

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .imePadding()
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (initialPlan == null) "Set Group Budget" else "Edit Group Budget",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 1. Base Budget
                Text(
                    text = "Base Budget (${currency.symbol})",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = baseAmountText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) baseAmountText = input
                    },
                    placeholder = { Text("e.g. 20000.00", color = FairShareTheme.colors.disabled) },
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

                // 2. Emergency Buffer
                Text(
                    text = "Emergency Buffer (${currency.symbol}) — Optional",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = bufferAmountText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) bufferAmountText = input
                    },
                    placeholder = { Text("e.g. 5000.00", color = FairShareTheme.colors.disabled) },
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

                // 3. Start Date & End Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Start Date",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = startDateText,
                            onValueChange = { startDateText = it },
                            placeholder = { Text("YYYY-MM-DD", color = FairShareTheme.colors.disabled) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FairShareTheme.colors.accent,
                                unfocusedBorderColor = FairShareTheme.colors.border,
                                focusedTextColor = FairShareTheme.colors.textPrimary,
                                unfocusedTextColor = FairShareTheme.colors.textPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "End Date",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = endDateText,
                            onValueChange = { endDateText = it },
                            placeholder = { Text("YYYY-MM-DD", color = FairShareTheme.colors.disabled) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FairShareTheme.colors.accent,
                                unfocusedBorderColor = FairShareTheme.colors.border,
                                focusedTextColor = FairShareTheme.colors.textPrimary,
                                unfocusedTextColor = FairShareTheme.colors.textPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Alert Threshold Setting
                Text(
                    text = "Alert Threshold (% of Base Budget)",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(50, 75, 80, 90, 100).forEach { pct ->
                        val isSelected = alertThreshold == pct
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface)
                                .clickable { alertThreshold = pct }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$pct%",
                                style = FairShareTheme.typography.metadata,
                                color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = FairShareTheme.colors.border)
                Spacer(modifier = Modifier.height(12.dp))

                // 5. Category Allocations
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category Budgets",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        FSButton(
                            text = "+ Add",
                            onClick = { showAddCategoryMenu = true },
                            variant = FSButtonVariant.Secondary
                        )
                        DropdownMenu(
                            expanded = showAddCategoryMenu,
                            onDismissRequest = { showAddCategoryMenu = false },
                            modifier = Modifier.background(FairShareTheme.colors.surfaceElevated)
                        ) {
                            ExpenseCategory.values()
                                .filter { !categoryAllocations.containsKey(it) }
                                .forEach { cat ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = cat.displayName,
                                                style = FairShareTheme.typography.body,
                                                color = FairShareTheme.colors.textPrimary
                                            )
                                        },
                                        onClick = {
                                            categoryAllocations[cat] = ""
                                            showAddCategoryMenu = false
                                        }
                                    )
                                }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                categoryAllocations.forEach { (cat, amt) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cat.displayName,
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = amt,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() || it == '.' }) categoryAllocations[cat] = input
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
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "✕",
                            color = FairShareTheme.colors.negative,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { categoryAllocations.remove(cat) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

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
                        text = "Save Budget",
                        onClick = {
                            val baseMinor = (parsedBase * 100).toLong()
                            val bufferMinor = (parsedBuffer * 100).toLong()

                            val parsedStart = try {
                                dateFormat.parse(startDateText)?.time ?: now
                            } catch (_: Exception) {
                                now
                            }
                            val parsedEnd = try {
                                dateFormat.parse(endDateText)?.time ?: (now + 7 * 86_400_000L)
                            } catch (_: Exception) {
                                now + 7 * 86_400_000L
                            }

                            val catMap = categoryAllocations.mapNotNull { (cat, str) ->
                                val v = str.toDoubleOrNull() ?: 0.0
                                if (v > 0.0) cat to Money((v * 100).toLong(), currency) else null
                            }.toMap()

                            val plan = BudgetPlan(
                                id = initialPlan?.id ?: "",
                                baseBudget = Money(baseMinor, currency),
                                emergencyBuffer = Money(bufferMinor, currency),
                                startDateEpochMs = parsedStart,
                                endDateEpochMs = maxOf(parsedStart, parsedEnd),
                                timezone = timezoneText,
                                categoryAllocations = catMap,
                                alertThresholdPercentage = alertThreshold
                            )
                            onSaveBudget(plan)
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f),
                        enabled = isValidBase
                    )
                }
            }
        }
    }
}
