package com.fairshare.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fairshare.android.core.common.FinancialEngine
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.model.Expense
import com.fairshare.android.core.model.Member
import com.fairshare.android.core.model.Payment
import com.fairshare.android.feature.expenses.AddExpenseDialog
import com.fairshare.android.feature.expenses.RecordPaymentDialog
import com.fairshare.android.feature.groups.ManageMembersDialog
import com.fairshare.android.feature.home.HomeScreen
import com.fairshare.android.feature.settlement.SettlementScreen

enum class ScreenState {
    HOME,
    SETTLEMENT
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FairShareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FairShareTheme.colors.background
                ) {
                    FairShareAppRoot()
                }
            }
        }
    }
}

@Composable
fun FairShareAppRoot() {
    val tripName by remember { mutableStateOf("Goa Trip 2026") }
    val members = remember {
        mutableStateListOf(
            Member("1", "Alice", isCurrentUser = true),
            Member("2", "Bob"),
            Member("3", "Charlie")
        )
    }
    val expenses = remember { mutableStateListOf<Expense>() }
    val payments = remember { mutableStateListOf<Payment>() }

    var currentScreen by remember { mutableStateOf(ScreenState.HOME) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var showManageMembersDialog by remember { mutableStateOf(false) }

    val financialSummary = remember(members.toList(), expenses.toList(), payments.toList()) {
        FinancialEngine.calculateSummary(members, expenses, payments)
    }

    val currentMember = members.firstOrNull { it.isCurrentUser } ?: members.first()

    when (currentScreen) {
        ScreenState.HOME -> {
            HomeScreen(
                currentMember = currentMember,
                tripName = tripName,
                summary = financialSummary,
                expenses = expenses,
                payments = payments,
                onAddExpenseClick = { showAddExpenseDialog = true },
                onRecordPaymentClick = { showRecordPaymentDialog = true },
                onViewSettlementClick = { currentScreen = ScreenState.SETTLEMENT },
                onManageMembersClick = { showManageMembersDialog = true }
            )
        }
        ScreenState.SETTLEMENT -> {
            SettlementScreen(
                tripName = tripName,
                summary = financialSummary,
                onBackClick = { currentScreen = ScreenState.HOME }
            )
        }
    }

    if (showAddExpenseDialog) {
        AddExpenseDialog(
            members = members,
            onDismiss = { showAddExpenseDialog = false },
            onSaveExpense = { newExpense ->
                expenses.add(0, newExpense)
                showAddExpenseDialog = false
            }
        )
    }

    if (showRecordPaymentDialog) {
        RecordPaymentDialog(
            members = members,
            onDismiss = { showRecordPaymentDialog = false },
            onSavePayment = { newPayment ->
                payments.add(0, newPayment)
                showRecordPaymentDialog = false
            }
        )
    }

    if (showManageMembersDialog) {
        ManageMembersDialog(
            members = members,
            onDismiss = { showManageMembersDialog = false },
            onAddMember = { newMember ->
                members.add(newMember)
            }
        )
    }
}
