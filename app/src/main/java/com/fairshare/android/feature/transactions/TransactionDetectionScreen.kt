package com.fairshare.android.feature.transactions

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairshare.android.core.database.repository.CandidateRepository
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.domain.transactions.CandidateSource
import com.fairshare.android.core.domain.transactions.CandidateStatus
import com.fairshare.android.core.domain.transactions.TransactionCandidate
import com.fairshare.android.core.domain.transactions.TransactionParser
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun TransactionDetectionScreen(
    candidateRepository: CandidateRepository,
    existingExpenses: List<Expense>,
    members: List<Member>,
    onBackClick: () -> Unit,
    onConfirmExpense: (Expense) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isDetectionEnabled by remember {
        mutableStateOf(FairShareNotificationListenerService.isDetectionEnabled(context))
    }

    val candidates = remember { mutableStateListOf<TransactionCandidate>() }
    var selectedCandidateForReview by remember { mutableStateOf<TransactionCandidate?>(null) }
    var showPasteImportDialog by remember { mutableStateOf(false) }
    var pastedText by remember { mutableStateOf("") }

    suspend fun refreshCandidates() {
        val list = candidateRepository.getPendingCandidates()
        val checkedList = list.map { cand ->
            val (isDup, reason) = candidateRepository.checkDuplicate(cand, existingExpenses)
            cand.copy(isDuplicate = isDup, duplicateReason = reason)
        }
        candidates.clear()
        candidates.addAll(checkedList)
    }

    LaunchedEffect(Unit) {
        refreshCandidates()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .background(FairShareTheme.colors.background)
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FairShareTheme.colors.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "‹ Back",
                style = FairShareTheme.typography.body,
                color = FairShareTheme.colors.accent,
                modifier = Modifier.clickable { onBackClick() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Transaction Detection",
                style = FairShareTheme.typography.title,
                color = FairShareTheme.colors.textPrimary
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Opt-in Toggle Card
            item {
                FSCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notification Detection",
                                    style = FairShareTheme.typography.section,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Detect bank and UPI payment notifications automatically on-device.",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = isDetectionEnabled,
                                onCheckedChange = { checked ->
                                    isDetectionEnabled = checked
                                    FairShareNotificationListenerService.setDetectionEnabled(context, checked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = FairShareTheme.colors.accent,
                                    checkedTrackColor = FairShareTheme.colors.surfaceElevated
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Privacy Guarantee: Raw notifications are parsed exclusively on-device. No notification text is uploaded or stored remotely. Candidates never become expenses without your explicit confirmation.",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Paste SMS / Bank Text Import
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETECTED CANDIDATES (${candidates.size})",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.accent,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "+ Paste Text",
                        style = FairShareTheme.typography.body,
                        color = FairShareTheme.colors.accent,
                        modifier = Modifier.clickable { showPasteImportDialog = true }
                    )
                }

                if (showPasteImportDialog) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FSCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Import from Payment SMS / Text",
                                style = FairShareTheme.typography.section,
                                color = FairShareTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = pastedText,
                                onValueChange = { pastedText = it },
                                placeholder = { Text("e.g. Paid Rs 850.00 to ABC Restaurant via UPI...") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FairShareTheme.colors.accent,
                                    unfocusedBorderColor = FairShareTheme.colors.border,
                                    focusedTextColor = FairShareTheme.colors.textPrimary,
                                    unfocusedTextColor = FairShareTheme.colors.textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                FSButton(
                                    text = "Cancel",
                                    onClick = {
                                        showPasteImportDialog = false
                                        pastedText = ""
                                    },
                                    variant = FSButtonVariant.Secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FSButton(
                                    text = "Extract Candidate",
                                    onClick = {
                                        val candidate = TransactionParser.parse(
                                            rawText = pastedText,
                                            source = CandidateSource.SMS_PASTED
                                        )
                                        if (candidate != null) {
                                            scope.launch {
                                                candidateRepository.saveCandidate(candidate)
                                                refreshCandidates()
                                            }
                                            showPasteImportDialog = false
                                            pastedText = ""
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (candidates.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No pending transaction candidates.",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textSecondary
                        )
                    }
                }
            } else {
                items(candidates) { candidate ->
                    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
                    val formattedDate = dateFormat.format(Date(candidate.detectedTimestamp))

                    FSCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { selectedCandidateForReview = candidate }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.detectedMerchant ?: "Unknown Payee",
                                        style = FairShareTheme.typography.section,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$formattedDate • ${candidate.rawSource.name}",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                }
                                Text(
                                    text = candidate.amount.formatted(),
                                    style = FairShareTheme.typography.section,
                                    color = FairShareTheme.colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Duplicate warning badge if found
                            if (candidate.isDuplicate) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(FairShareTheme.colors.surfaceElevated)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Worth checking: ${candidate.duplicateReason}",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.accent
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                FSButton(
                                    text = "Discard",
                                    onClick = {
                                        scope.launch {
                                            candidateRepository.updateCandidateStatus(candidate.id, CandidateStatus.DISCARDED)
                                            refreshCandidates()
                                        }
                                    },
                                    variant = FSButtonVariant.Secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FSButton(
                                    text = "Review & Add",
                                    onClick = { selectedCandidateForReview = candidate },
                                    variant = FSButtonVariant.Primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Candidate Review Dialog
    selectedCandidateForReview?.let { cand ->
        var editableMerchant by remember { mutableStateOf(cand.detectedMerchant ?: "Expense") }
        var selectedPayerId by remember { mutableStateOf(members.firstOrNull { it.isCurrentUser }?.id ?: members.firstOrNull()?.id ?: "") }
        var selectedCategory by remember { mutableStateOf(cand.inferredCategory) }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { selectedCandidateForReview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            androidx.compose.material3.Surface(
                shape = FairShareTheme.shapes.dialog,
                color = FairShareTheme.colors.surfaceElevated,
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "REVIEW TRANSACTION",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.accent,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cand.amount.formatted(),
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editableMerchant,
                        onValueChange = { editableMerchant = it },
                        label = { Text("Description / Payee") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FairShareTheme.colors.accent,
                            unfocusedBorderColor = FairShareTheme.colors.border,
                            focusedTextColor = FairShareTheme.colors.textPrimary,
                            unfocusedTextColor = FairShareTheme.colors.textPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Payer", style = FairShareTheme.typography.metadata, color = FairShareTheme.colors.textSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        members.forEach { m ->
                            val isSelected = m.id == selectedPayerId
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.surface
                                    )
                                    .clickable { selectedPayerId = m.id }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = m.name,
                                    style = FairShareTheme.typography.metadata,
                                    color = if (isSelected) FairShareTheme.colors.surface else FairShareTheme.colors.textPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FSButton(
                            text = "Cancel",
                            onClick = { selectedCandidateForReview = null },
                            variant = FSButtonVariant.Secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FSButton(
                            text = "Add to Group",
                            onClick = {
                                val expense = Expense(
                                    id = UUID.randomUUID().toString(),
                                    description = editableMerchant.ifBlank { "Detected Expense" },
                                    totalAmount = cand.amount,
                                    payers = listOf(ExpensePayer(memberId = selectedPayerId, amount = cand.amount)),
                                    participants = members.map { ExpenseParticipant(memberId = it.id) },
                                    splitMethod = SplitMethod.EQUAL,
                                    mode = cand.inferredMode,
                                    category = selectedCategory,
                                    timestamp = cand.detectedTimestamp
                                )
                                scope.launch {
                                    candidateRepository.updateCandidateStatus(cand.id, CandidateStatus.CONFIRMED)
                                }
                                onConfirmExpense(expense)
                                selectedCandidateForReview = null
                            }
                        )
                    }
                }
            }
        }
    }
}
