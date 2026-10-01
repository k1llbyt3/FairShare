package com.fairshare.android.feature.expenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.ExpenseItem
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.domain.rounding.RoundingEngine
import java.util.Locale
import java.util.UUID

@Composable
fun SplitConfigDialog(
    totalAmount: Money,
    members: List<Member>,
    initialSplitMethod: SplitMethod,
    initialParticipants: List<ExpenseParticipant>,
    initialItems: List<ExpenseItem>,
    onDismiss: () -> Unit,
    onConfirmSplit: (SplitMethod, List<ExpenseParticipant>, List<ExpenseItem>) -> Unit
) {
    var selectedMethod by remember { mutableStateOf(initialSplitMethod) }

    // Independent participant selection (memberId -> Boolean isSelected)
    val participantSelection = remember {
        mutableStateMapOf<String, Boolean>().apply {
            members.forEach { m ->
                val p = initialParticipants.find { it.memberId == m.id }
                this[m.id] = p?.let { !it.excluded } ?: true
            }
        }
    }

    val currency = totalAmount.currency
    val selectedMembers = members.filter { participantSelection[it.id] == true }
    val selectedCount = selectedMembers.size

    // Exact split inputs (memberId -> string amount)
    val exactAmounts = remember {
        mutableStateMapOf<String, String>().apply {
            members.forEach { m ->
                val p = initialParticipants.find { it.memberId == m.id }
                val initialPaise = p?.exactAmount?.amountMinor
                this[m.id] = if (initialPaise != null && initialPaise > 0L) {
                    val r = initialPaise / 100
                    val pa = initialPaise % 100
                    if (pa == 0L) "$r" else String.format(Locale.ROOT, "%.2f", initialPaise / 100.0)
                } else ""
            }
        }
    }

    // Percentage split inputs (memberId -> string percentage)
    val percentageInputs = remember {
        mutableStateMapOf<String, String>().apply {
            members.forEach { m ->
                val p = initialParticipants.find { it.memberId == m.id }
                val bps = p?.percentageBasisPoints
                this[m.id] = if (bps != null && bps > 0) {
                    val pct = bps / 100.0
                    if (pct % 1.0 == 0.0) pct.toInt().toString() else pct.toString()
                } else ""
            }
        }
    }

    // Shares split inputs (memberId -> integer share units)
    val shareUnits = remember {
        mutableStateMapOf<String, Int>().apply {
            members.forEach { m ->
                val p = initialParticipants.find { it.memberId == m.id }
                this[m.id] = p?.shares ?: 1
            }
        }
    }

    // Itemized items
    val itemsList = remember {
        mutableStateListOf<ExpenseItem>().apply {
            if (initialItems.isNotEmpty()) {
                addAll(initialItems)
            } else {
                add(
                    ExpenseItem(
                        id = UUID.randomUUID().toString(),
                        name = "Item 1",
                        amount = totalAmount,
                        participantMemberIds = selectedMembers.map { it.id }.ifEmpty { members.map { it.id } }
                    )
                )
            }
        }
    }

    var newItemName by remember { mutableStateOf("") }
    var newItemAmountText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .fillMaxHeight(0.88f)
                .imePadding()
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // Header (Fixed at top)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Split Configuration",
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Text(
                            text = "Total: ${totalAmount.formatted()}",
                            style = FairShareTheme.typography.section,
                            color = FairShareTheme.colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = FairShareTheme.colors.border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Section 1: Independent Participant Selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "1. PARTICIPANTS ($selectedCount of ${members.size})",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Select participating members",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textTertiary
                            )
                        }
                        Row {
                            TextButton(
                                onClick = { members.forEach { participantSelection[it.id] = true } }
                            ) {
                                Text(
                                    text = "All",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(
                                onClick = { members.forEach { participantSelection[it.id] = false } }
                            ) {
                                Text(
                                    text = "None",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Member Selection Checkbox List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(FairShareTheme.colors.surface)
                            .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        members.forEach { member ->
                            val isSelected = participantSelection[member.id] == true
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        participantSelection[member.id] = !isSelected
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        participantSelection[member.id] = checked
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = FairShareTheme.colors.accent,
                                        uncheckedColor = FairShareTheme.colors.border
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = member.name + if (member.isCurrentUser) " (You)" else "",
                                    style = FairShareTheme.typography.body,
                                    color = if (isSelected) FairShareTheme.colors.textPrimary else FairShareTheme.colors.disabled,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Text(
                                        text = "Included",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.positive,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(
                                        text = "Excluded (₹0)",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.disabled
                                    )
                                }
                            }
                        }
                    }

                    if (selectedCount == 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "⚠ Please select at least one participating member.",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.negative
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Section 2: Split Method Selector Tabs
                    Text(
                        text = "2. SPLIT METHOD",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SplitMethod.values().forEach { method ->
                            val isSelected = selectedMethod == method
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedMethod = method }
                            ) {
                                Text(
                                    text = method.name.lowercase().replaceFirstChar { it.titlecase() },
                                    style = FairShareTheme.typography.metadata,
                                    color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textSecondary,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = FairShareTheme.colors.border, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Section 3: Split Calculations & Breakdown
                    var isValid = false

                    when (selectedMethod) {
                        SplitMethod.EQUAL -> {
                            isValid = selectedCount > 0

                            val calculatedShares = remember(selectedMembers, totalAmount) {
                                if (selectedCount > 0 && totalAmount.amountMinor > 0L) {
                                    RoundingEngine.distributeEqual(
                                        totalAmount.amountMinor,
                                        selectedMembers.map { it.id }
                                    )
                                } else emptyMap()
                            }

                            Text(
                                text = if (selectedCount > 0)
                                    "Divided equally among $selectedCount member(s):"
                                else "No participants selected",
                                style = FairShareTheme.typography.supporting,
                                color = FairShareTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            members.forEach { member ->
                                val isSelected = participantSelection[member.id] == true
                                val shareMinor = calculatedShares[member.id] ?: 0L
                                val shareMoney = Money(shareMinor, currency)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp, horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = member.name,
                                        style = FairShareTheme.typography.body,
                                        color = if (isSelected) FairShareTheme.colors.textPrimary else FairShareTheme.colors.disabled
                                    )
                                    Text(
                                        text = if (isSelected) shareMoney.formatted() else "Excluded (₹0.00)",
                                        style = FairShareTheme.typography.body,
                                        color = if (isSelected) FairShareTheme.colors.textPrimary else FairShareTheme.colors.disabled,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        SplitMethod.EXACT -> {
                            val parsedExactMinors = selectedMembers.associate { m ->
                                val text = exactAmounts[m.id]?.trim() ?: ""
                                val rupees = text.toDoubleOrNull() ?: 0.0
                                m.id to (rupees * 100).toLong()
                            }
                            val assignedTotalMinor = parsedExactMinors.values.sum()
                            val diffMinor = totalAmount.amountMinor - assignedTotalMinor
                            isValid = diffMinor == 0L && totalAmount.amountMinor > 0L && selectedCount > 0

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Assigned: ${Money(assignedTotalMinor, currency).formatted()}",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                val statusColor = when {
                                    diffMinor == 0L && selectedCount > 0 -> FairShareTheme.colors.positive
                                    diffMinor > 0L -> FairShareTheme.colors.negative
                                    else -> FairShareTheme.colors.negative
                                }
                                val statusText = when {
                                    selectedCount == 0 -> "No participants"
                                    diffMinor == 0L -> "Exact match"
                                    diffMinor > 0L -> "${Money(diffMinor, currency).formatted()} remaining"
                                    else -> "${Money(-diffMinor, currency).formatted()} over total"
                                }
                                Text(
                                    text = statusText,
                                    style = FairShareTheme.typography.metadata,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            members.forEach { member ->
                                val isSelected = participantSelection[member.id] == true
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = member.name,
                                        style = FairShareTheme.typography.body,
                                        color = if (isSelected) FairShareTheme.colors.textPrimary else FairShareTheme.colors.disabled,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        OutlinedTextField(
                                            value = exactAmounts[member.id] ?: "",
                                            onValueChange = { input ->
                                                if (input.all { it.isDigit() || it == '.' }) {
                                                    exactAmounts[member.id] = input
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
                                            modifier = Modifier.width(120.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "Excluded (₹0.00)",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.disabled
                                        )
                                    }
                                }
                            }
                        }

                        SplitMethod.PERCENTAGE -> {
                            val parsedBasisPoints = selectedMembers.associate { m ->
                                val text = percentageInputs[m.id]?.trim() ?: ""
                                val pct = text.toDoubleOrNull() ?: 0.0
                                m.id to (pct * 100).toInt()
                            }
                            val totalBps = parsedBasisPoints.values.sum()
                            val diffBps = 10000 - totalBps
                            isValid = diffBps == 0 && totalAmount.amountMinor > 0L && selectedCount > 0

                            val calculatedShares = remember(parsedBasisPoints, totalAmount, selectedCount) {
                                if (totalBps == 10000 && totalAmount.amountMinor > 0L && selectedCount > 0) {
                                    val weights = selectedMembers.map {
                                        RoundingEngine.WeightedParticipant(
                                            id = it.id,
                                            weight = (parsedBasisPoints[it.id] ?: 0).toLong()
                                        )
                                    }
                                    RoundingEngine.distribute(totalAmount.amountMinor, weights)
                                } else emptyMap()
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total: ${(totalBps / 100.0)}%",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                val statusColor = if (isValid) FairShareTheme.colors.positive else FairShareTheme.colors.negative
                                val statusText = if (isValid) "100.0% satisfied" else "${(diffBps / 100.0)}% remaining"
                                Text(
                                    text = statusText,
                                    style = FairShareTheme.typography.metadata,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            members.forEach { member ->
                                val isSelected = participantSelection[member.id] == true
                                val shareMinor = calculatedShares[member.id] ?: 0L
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = member.name,
                                            style = FairShareTheme.typography.body,
                                            color = if (isSelected) FairShareTheme.colors.textPrimary else FairShareTheme.colors.disabled
                                        )
                                        if (isSelected && shareMinor > 0L) {
                                            Text(
                                                text = Money(shareMinor, currency).formatted(),
                                                style = FairShareTheme.typography.metadata,
                                                color = FairShareTheme.colors.textSecondary
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        OutlinedTextField(
                                            value = percentageInputs[member.id] ?: "",
                                            onValueChange = { input ->
                                                if (input.all { it.isDigit() || it == '.' }) {
                                                    percentageInputs[member.id] = input
                                                }
                                            },
                                            placeholder = { Text("0", color = FairShareTheme.colors.disabled) },
                                            suffix = { Text("%", color = FairShareTheme.colors.textSecondary) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = FairShareTheme.colors.accent,
                                                unfocusedBorderColor = FairShareTheme.colors.border,
                                                focusedTextColor = FairShareTheme.colors.textPrimary,
                                                unfocusedTextColor = FairShareTheme.colors.textPrimary
                                            ),
                                            modifier = Modifier.width(100.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "Excluded (0%)",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.disabled
                                        )
                                    }
                                }
                            }
                        }

                        SplitMethod.SHARES -> {
                            val totalShares = selectedMembers.sumOf { shareUnits[it.id] ?: 1 }
                            isValid = totalShares > 0 && totalAmount.amountMinor > 0L && selectedCount > 0

                            val calculatedShares = remember(shareUnits.toMap(), totalAmount, selectedCount) {
                                if (totalShares > 0 && totalAmount.amountMinor > 0L && selectedCount > 0) {
                                    val weights = selectedMembers.map {
                                        RoundingEngine.WeightedParticipant(
                                            id = it.id,
                                            weight = (shareUnits[it.id] ?: 1).toLong()
                                        )
                                    }
                                    RoundingEngine.distribute(totalAmount.amountMinor, weights)
                                } else emptyMap()
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total Units: $totalShares shares",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                Text(
                                    text = if (isValid) "Valid share ratio" else "Invalid shares",
                                    style = FairShareTheme.typography.metadata,
                                    color = if (isValid) FairShareTheme.colors.positive else FairShareTheme.colors.negative,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            members.forEach { member ->
                                val isSelected = participantSelection[member.id] == true
                                val shareMinor = calculatedShares[member.id] ?: 0L
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = member.name,
                                            style = FairShareTheme.typography.body,
                                            color = if (isSelected) FairShareTheme.colors.textPrimary else FairShareTheme.colors.disabled
                                        )
                                        if (isSelected && shareMinor > 0L) {
                                            Text(
                                                text = Money(shareMinor, currency).formatted(),
                                                style = FairShareTheme.typography.metadata,
                                                color = FairShareTheme.colors.textSecondary
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(
                                                onClick = {
                                                    val cur = shareUnits[member.id] ?: 1
                                                    if (cur > 1) shareUnits[member.id] = cur - 1
                                                }
                                            ) {
                                                Text("-", fontSize = 18.sp, color = FairShareTheme.colors.accent, fontWeight = FontWeight.Bold)
                                            }
                                            Text(
                                                text = "${shareUnits[member.id] ?: 1}",
                                                style = FairShareTheme.typography.body,
                                                fontWeight = FontWeight.Bold,
                                                color = FairShareTheme.colors.textPrimary,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                            TextButton(
                                                onClick = {
                                                    val cur = shareUnits[member.id] ?: 1
                                                    shareUnits[member.id] = cur + 1
                                                }
                                            ) {
                                                Text("+", fontSize = 18.sp, color = FairShareTheme.colors.accent, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Excluded (0 shares)",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.disabled
                                        )
                                    }
                                }
                            }
                        }

                        SplitMethod.ITEMIZED -> {
                            isValid = itemsList.isNotEmpty() && itemsList.all { it.amount.amountMinor > 0L } && selectedCount > 0

                            val itemsTotalMinor = itemsList.sumOf { it.amount.amountMinor }
                            val diffMinor = totalAmount.amountMinor - itemsTotalMinor

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Items Total: ${Money(itemsTotalMinor, currency).formatted()}",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                val statusText = if (diffMinor == 0L) "Matches Total" else "${Money(diffMinor, currency).formatted()} remaining"
                                Text(
                                    text = statusText,
                                    style = FairShareTheme.typography.metadata,
                                    color = if (diffMinor == 0L) FairShareTheme.colors.positive else FairShareTheme.colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            itemsList.forEachIndexed { index, item ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = FairShareTheme.colors.surface,
                                    border = BorderStroke(1.dp, FairShareTheme.colors.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.name,
                                                style = FairShareTheme.typography.body,
                                                color = FairShareTheme.colors.textPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = item.amount.formatted(),
                                                    style = FairShareTheme.typography.body,
                                                    color = FairShareTheme.colors.textPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                IconButton(
                                                    onClick = { if (itemsList.size > 1) itemsList.removeAt(index) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Remove Item",
                                                        tint = FairShareTheme.colors.negative,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Add new item row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newItemName,
                                    onValueChange = { newItemName = it },
                                    placeholder = { Text("Item name", color = FairShareTheme.colors.disabled) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = FairShareTheme.colors.accent,
                                        unfocusedBorderColor = FairShareTheme.colors.border,
                                        focusedTextColor = FairShareTheme.colors.textPrimary,
                                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                                    )
                                )
                                OutlinedTextField(
                                    value = newItemAmountText,
                                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) newItemAmountText = it },
                                    placeholder = { Text("0.00", color = FairShareTheme.colors.disabled) },
                                    singleLine = true,
                                    prefix = { Text("₹", color = FairShareTheme.colors.textSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.width(96.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = FairShareTheme.colors.accent,
                                        unfocusedBorderColor = FairShareTheme.colors.border,
                                        focusedTextColor = FairShareTheme.colors.textPrimary,
                                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                                    )
                                )
                                IconButton(
                                    onClick = {
                                        val amt = newItemAmountText.toDoubleOrNull() ?: 0.0
                                        if (newItemName.isNotBlank() && amt > 0.0) {
                                            itemsList.add(
                                                ExpenseItem(
                                                    id = UUID.randomUUID().toString(),
                                                    name = newItemName.trim(),
                                                    amount = Money((amt * 100).toLong(), currency),
                                                    participantMemberIds = selectedMembers.map { it.id }
                                                )
                                            )
                                            newItemName = ""
                                            newItemAmountText = ""
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Item",
                                        tint = FairShareTheme.colors.accent
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = FairShareTheme.colors.border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons (Fixed at bottom)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FSButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    FSButton(
                        text = "Save Split",
                        onClick = {
                            val finalParticipants = members.map { member ->
                                val isSelected = participantSelection[member.id] == true
                                when (selectedMethod) {
                                    SplitMethod.EQUAL -> {
                                        ExpenseParticipant(
                                            memberId = member.id,
                                            excluded = !isSelected
                                        )
                                    }
                                    SplitMethod.EXACT -> {
                                        val amt = exactAmounts[member.id]?.toDoubleOrNull() ?: 0.0
                                        ExpenseParticipant(
                                            memberId = member.id,
                                            exactAmount = Money((amt * 100).toLong(), currency),
                                            excluded = !isSelected
                                        )
                                    }
                                    SplitMethod.PERCENTAGE -> {
                                        val pct = percentageInputs[member.id]?.toDoubleOrNull() ?: 0.0
                                        ExpenseParticipant(
                                            memberId = member.id,
                                            percentageBasisPoints = (pct * 100).toInt(),
                                            excluded = !isSelected
                                        )
                                    }
                                    SplitMethod.SHARES -> {
                                        ExpenseParticipant(
                                            memberId = member.id,
                                            shares = shareUnits[member.id] ?: 1,
                                            excluded = !isSelected
                                        )
                                    }
                                    SplitMethod.ITEMIZED -> {
                                        ExpenseParticipant(
                                            memberId = member.id,
                                            excluded = !isSelected
                                        )
                                    }
                                }
                            }
                            onConfirmSplit(selectedMethod, finalParticipants, itemsList.toList())
                        },
                        enabled = selectedCount > 0 && when (selectedMethod) {
                            SplitMethod.EQUAL -> true
                            SplitMethod.EXACT -> {
                                val sum = selectedMembers.sumOf { (exactAmounts[it.id]?.toDoubleOrNull() ?: 0.0) * 100 }.toLong()
                                sum == totalAmount.amountMinor && totalAmount.amountMinor > 0L
                            }
                            SplitMethod.PERCENTAGE -> {
                                val totalBps = selectedMembers.sumOf { ((percentageInputs[it.id]?.toDoubleOrNull() ?: 0.0) * 100).toInt() }
                                totalBps == 10000 && totalAmount.amountMinor > 0L
                            }
                            SplitMethod.SHARES -> {
                                selectedMembers.sumOf { shareUnits[it.id] ?: 1 } > 0 && totalAmount.amountMinor > 0L
                            }
                            SplitMethod.ITEMIZED -> itemsList.isNotEmpty() && itemsList.all { it.amount.amountMinor > 0L }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
