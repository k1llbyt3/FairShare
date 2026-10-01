package com.fairshare.android.feature.expenses

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.domain.natural.NaturalLanguageExpenseParser
import com.fairshare.android.core.domain.natural.ParsedExpenseDraft
import java.util.Locale
import java.util.UUID

@Composable
fun NaturalLanguageEntryDialog(
    members: List<Member>,
    currentMember: Member,
    currency: Currency = Currency.INR,
    onDismiss: () -> Unit,
    onConfirmExpense: (Expense) -> Unit
) {
    var rawInputText by remember { mutableStateOf("") }
    var parsedDraft by remember { mutableStateOf<ParsedExpenseDraft?>(null) }

    val voiceRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                rawInputText = spokenText
                parsedDraft = NaturalLanguageExpenseParser.parse(spokenText, members, currentMember, currency)
            }
        }
    }

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
                    text = "Smart Expense Input",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Type or speak natural phrases like 'paid 1200 for dinner with Arjun and Neha'",
                    style = FairShareTheme.typography.supporting,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = {
                            rawInputText = it
                            if (it.isNotBlank()) {
                                parsedDraft = NaturalLanguageExpenseParser.parse(it, members, currentMember, currency)
                            } else {
                                parsedDraft = null
                            }
                        },
                        placeholder = { Text("e.g. 500 taxi with Rahul") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FairShareTheme.colors.accent,
                            unfocusedBorderColor = FairShareTheme.colors.border,
                            focusedTextColor = FairShareTheme.colors.textPrimary,
                            unfocusedTextColor = FairShareTheme.colors.textPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(FairShareTheme.colors.surface)
                            .border(1.dp, FairShareTheme.colors.border)
                            .clickable {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say expense details...")
                                    }
                                    voiceRecognizerLauncher.launch(intent)
                                } catch (_: Exception) {
                                    // Voice intent not supported gracefully handled
                                }
                            }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Mic",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Structured draft preview
                parsedDraft?.let { draft ->
                    FSCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "I UNDERSTOOD",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.accent,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Amount", style = FairShareTheme.typography.supporting, color = FairShareTheme.colors.textSecondary)
                            Text(draft.amount.formatted(), style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Paid by", style = FairShareTheme.typography.supporting, color = FairShareTheme.colors.textSecondary)
                            Text(draft.payerName ?: "Unknown", style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Description", style = FairShareTheme.typography.supporting, color = FairShareTheme.colors.textSecondary)
                            Text(draft.description, style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Category", style = FairShareTheme.typography.supporting, color = FairShareTheme.colors.textSecondary)
                            Text(draft.category.displayName, style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Shared by", style = FairShareTheme.typography.supporting, color = FairShareTheme.colors.textSecondary)
                            val participantNames = draft.participantIds.mapNotNull { id -> members.find { it.id == id }?.name }.joinToString()
                            Text(participantNames.ifBlank { "All members" }, style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FSButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = FSButtonVariant.Secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FSButton(
                        text = "Confirm Expense",
                        onClick = {
                            val draft = parsedDraft
                            if (draft != null && draft.amount.amountMinor > 0) {
                                val expense = Expense(
                                    id = UUID.randomUUID().toString(),
                                    description = draft.description,
                                    totalAmount = draft.amount,
                                    payers = listOf(ExpensePayer(memberId = draft.payerId ?: currentMember.id, amount = draft.amount)),
                                    participants = draft.participantIds.map { ExpenseParticipant(memberId = it) },
                                    splitMethod = SplitMethod.EQUAL,
                                    mode = draft.mode,
                                    category = draft.category,
                                    timestamp = draft.dateEpochMillis
                                )
                                onConfirmExpense(expense)
                            }
                        },
                        variant = FSButtonVariant.Primary
                    )
                }
            }
        }
    }
}
