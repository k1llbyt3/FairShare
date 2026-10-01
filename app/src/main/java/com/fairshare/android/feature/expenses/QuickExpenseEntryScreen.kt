package com.fairshare.android.feature.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SplitMethod
import java.util.UUID

@Composable
fun QuickExpenseEntryScreen(
    groupName: String,
    groupId: String,
    members: List<Member>,
    currency: Currency = Currency.INR,
    onBackClick: () -> Unit,
    onSaveExpense: (Expense) -> Unit
) {
    var rawInputText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ExpenseCategory.FOOD) }

    val defaultPayerId = members.firstOrNull { it.isCurrentUser }?.id
        ?: members.firstOrNull()?.id ?: ""
    var payerId by remember { mutableStateOf(defaultPayerId) }
    var selectedParticipantIds by remember {
        mutableStateOf(members.map { it.id }.toSet())
    }
    var expandedPayerMenu by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val fromMember = members.firstOrNull { it.id == payerId }

    fun applyParser(text: String) {
        val parsed = QuickExpenseParser.parse(text, currency)
        if (parsed != null) {
            amountText = String.format(java.util.Locale.US, "%.2f", parsed.amount.amountMinor / 100.0)
            descriptionText = parsed.description
            selectedCategory = parsed.category
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        color = FairShareTheme.colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = FairShareTheme.colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quick Expense",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Text(
                        text = groupName,
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = null,
                    tint = FairShareTheme.colors.accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Auto-Parser box
            Text(
                text = "Quick Smart Entry (e.g. 'Coffee 250' or 'Dinner 1200')",
                style = FairShareTheme.typography.metadata,
                color = FairShareTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = rawInputText,
                onValueChange = {
                    rawInputText = it
                    applyParser(it)
                    errorMessage = null
                },
                placeholder = { Text("e.g. Uber 340, Lunch 650", color = FairShareTheme.colors.disabled) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FairShareTheme.colors.accent,
                    unfocusedBorderColor = FairShareTheme.colors.border,
                    focusedTextColor = FairShareTheme.colors.textPrimary,
                    unfocusedTextColor = FairShareTheme.colors.textPrimary,
                    focusedContainerColor = FairShareTheme.colors.surface,
                    unfocusedContainerColor = FairShareTheme.colors.surface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Amount field
            Text(
                text = "Amount (₹) *",
                style = FairShareTheme.typography.metadata,
                color = FairShareTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    if (it.all { ch -> ch.isDigit() || ch == '.' }) {
                        amountText = it
                        errorMessage = null
                    }
                },
                placeholder = { Text("0.00", color = FairShareTheme.colors.disabled) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FairShareTheme.colors.accent,
                    unfocusedBorderColor = FairShareTheme.colors.border,
                    focusedTextColor = FairShareTheme.colors.textPrimary,
                    unfocusedTextColor = FairShareTheme.colors.textPrimary,
                    focusedContainerColor = FairShareTheme.colors.surface,
                    unfocusedContainerColor = FairShareTheme.colors.surface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Description field
            Text(
                text = "Description *",
                style = FairShareTheme.typography.metadata,
                color = FairShareTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = descriptionText,
                onValueChange = {
                    descriptionText = it
                    errorMessage = null
                },
                placeholder = { Text("What was this for?", color = FairShareTheme.colors.disabled) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FairShareTheme.colors.accent,
                    unfocusedBorderColor = FairShareTheme.colors.border,
                    focusedTextColor = FairShareTheme.colors.textPrimary,
                    unfocusedTextColor = FairShareTheme.colors.textPrimary,
                    focusedContainerColor = FairShareTheme.colors.surface,
                    unfocusedContainerColor = FairShareTheme.colors.surface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Payer selection
            Text(
                text = "Paid By",
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
                        .clickable { expandedPayerMenu = true }
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
                    expanded = expandedPayerMenu,
                    onDismissRequest = { expandedPayerMenu = false },
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
                                payerId = m.id
                                expandedPayerMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Participants selection
            Text(
                text = "Split With (${selectedParticipantIds.size} of ${members.size})",
                style = FairShareTheme.typography.metadata,
                color = FairShareTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FairShareTheme.colors.surface, RoundedCornerShape(8.dp))
                    .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                members.forEach { m ->
                    val isIncluded = selectedParticipantIds.contains(m.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                selectedParticipantIds = if (isIncluded) {
                                    if (selectedParticipantIds.size > 1) {
                                        selectedParticipantIds - m.id
                                    } else selectedParticipantIds
                                } else {
                                    selectedParticipantIds + m.id
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isIncluded) FairShareTheme.colors.accent else FairShareTheme.colors.surface)
                                .border(
                                    1.dp,
                                    if (isIncluded) FairShareTheme.colors.accent else FairShareTheme.colors.border,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isIncluded) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = FairShareTheme.colors.surface,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = m.name + if (m.isCurrentUser) " (You)" else "",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textPrimary
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage!!,
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.negative
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Action
            FSButton(
                text = if (isSaving) "Saving..." else "Save Quick Expense",
                onClick = {
                    val rupees = amountText.toDoubleOrNull() ?: 0.0
                    val minor = (rupees * 100).toLong()
                    if (minor <= 0L) {
                        errorMessage = "Please enter a valid amount"
                        return@FSButton
                    }
                    if (descriptionText.isBlank()) {
                        errorMessage = "Please enter a description"
                        return@FSButton
                    }
                    if (payerId.isBlank()) {
                        errorMessage = "Please select a payer"
                        return@FSButton
                    }
                    if (selectedParticipantIds.isEmpty()) {
                        errorMessage = "Please select at least one participant"
                        return@FSButton
                    }

                    isSaving = true
                    val expenseMoney = Money(minor, currency)
                    val expense = Expense(
                        id = UUID.randomUUID().toString(),
                        payerId = payerId,
                        amount = expenseMoney,
                        involvedMemberIds = selectedParticipantIds.toList(),
                        mode = PaymentMode.UPI,
                        description = descriptionText.trim(),
                        timestamp = System.currentTimeMillis(),
                        category = selectedCategory,
                        groupId = groupId
                    )

                    onSaveExpense(expense)
                },
                variant = FSButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving && amountText.isNotBlank() && descriptionText.isNotBlank()
            )
        }
    }
}
