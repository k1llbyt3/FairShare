package com.fairshare.android.feature.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.balance.MemberBalance
import com.fairshare.android.core.domain.export.ExportEngine
import com.fairshare.android.core.domain.export.ExportFormat
import com.fairshare.android.core.domain.export.ExportScope
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportShareDialog(
    groupName: String,
    expenses: List<Expense>,
    payments: List<SettlementPayment>,
    members: List<Member>,
    balances: List<MemberBalance> = emptyList(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedScope by remember { mutableStateOf(ExportScope.FULL_LEDGER) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.CSV) }

    val exportData = remember(selectedScope, selectedFormat, expenses, payments, members, balances) {
        ExportEngine.generateExport(
            groupName = groupName,
            scope = selectedScope,
            format = selectedFormat,
            expenses = expenses,
            payments = payments,
            members = members,
            balances = balances
        )
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(FairShareTheme.colors.surface, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Export & Share Group Ledger",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Generate a verified report or spreadsheet of group expenses.",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Export Scope",
                    style = FairShareTheme.typography.body,
                    fontWeight = FontWeight.Bold,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                ExportScope.values().forEach { scope ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedScope = scope }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedScope == scope,
                            onClick = { selectedScope = scope },
                            colors = RadioButtonDefaults.colors(selectedColor = FairShareTheme.colors.accent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = scope.displayName,
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Format",
                    style = FairShareTheme.typography.body,
                    fontWeight = FontWeight.Bold,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ExportFormat.values().forEach { fmt ->
                        Row(
                            modifier = Modifier
                                .clickable { selectedFormat = fmt }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFormat == fmt,
                                onClick = { selectedFormat = fmt },
                                colors = RadioButtonDefaults.colors(selectedColor = FairShareTheme.colors.accent)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = fmt.displayName,
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FairShareTheme.colors.surfaceElevated, RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "Output: ${exportData.fileName}",
                            style = FairShareTheme.typography.metadata,
                            fontWeight = FontWeight.Bold,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Text(
                            text = "${exportData.recordCount} records included",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FSButton(
                        text = "Cancel",
                        onClick = onDismiss
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FSButton(
                        text = "Copy Text",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(exportData.fileName, exportData.content))
                            Toast.makeText(context, "Copied report to clipboard", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FSButton(
                        text = "Share",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, exportData.fileName)
                                putExtra(Intent.EXTRA_TEXT, exportData.content)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Report via"))
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}
