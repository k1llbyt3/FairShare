package com.fairshare.android

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairshare.android.core.FairShareAppContainer
import com.fairshare.android.core.common.FinancialEngine
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.model.FinancialSummary
import com.fairshare.android.core.network.backend.model.BackendUser
import com.fairshare.android.core.network.client.NetworkResult
import com.fairshare.android.core.network.repository.AuthState
import com.fairshare.android.feature.auth.AuthScreen
import com.fairshare.android.feature.expenses.AddExpenseRouterDialog
import com.fairshare.android.feature.expenses.ExpenseDetailScreen
import com.fairshare.android.feature.expenses.ExpenseEntryDialog
import com.fairshare.android.feature.expenses.ExpenseEntryMode
import com.fairshare.android.feature.expenses.ExpenseListScreen
import com.fairshare.android.feature.expenses.RecordPaymentDialog
import com.fairshare.android.core.sync.SyncStatus
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.feature.groups.CreateGroupDialog
import com.fairshare.android.feature.groups.GroupInvitationDialog
import com.fairshare.android.feature.groups.GroupOverviewScreen
import com.fairshare.android.feature.groups.GroupSwitcherDialog
import com.fairshare.android.feature.groups.GroupsScreen
import com.fairshare.android.feature.groups.JoinGroupDialog
import com.fairshare.android.feature.groups.ManageMembersDialog
import com.fairshare.android.feature.home.HomeScreen
import com.fairshare.android.feature.profile.ProfileScreen
import com.fairshare.android.feature.chat.ChatScreen
import com.fairshare.android.feature.settlement.SettlementScreen
import com.fairshare.android.feature.budget.BudgetScreen
import com.fairshare.android.feature.budget.DailyBriefScreen
import com.fairshare.android.feature.budget.EditBudgetDialog
import com.fairshare.android.core.domain.budget.BudgetPlan
import kotlinx.coroutines.launch

enum class ScreenState {
    HOME,
    GROUPS,
    GROUP_OVERVIEW,
    TRIPS,
    SETTLEMENT,
    PROFILE,
    EXPENSE_LIST,
    EXPENSE_DETAIL,
    EXPENSE_ENTRY,
    QUICK_EXPENSE_ENTRY,
    BUDGET,
    DAILY_BRIEF,
    RECEIPT_SCANNER,
    TRANSACTION_DETECTION,
    VAULT,
    GROUP_CHAT,
    GROUP_ACTIVITY,
    SEARCH,
    CREATE_GROUP
}

class MainActivity : ComponentActivity() {

    private lateinit var container: FairShareAppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = FairShareAppContainer.getInstance(this)

        setContent {
            FairShareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FairShareTheme.colors.background
                ) {
                    FairShareAppRoot(container = container)
                }
            }
        }
    }
}

@Composable
fun FairShareAppRoot(container: FairShareAppContainer) {
    val authState by container.authRepository.authState.collectAsState()

    when (val state = authState) {
        is AuthState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FairShareTheme.colors.background)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = com.fairshare.android.R.drawable.icon),
                        contentDescription = "FairShare Logo",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "FAIRSHARE",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.accent,
                        letterSpacing = 3.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(
                        color = FairShareTheme.colors.accent,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
        is AuthState.Unauthenticated -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FairShareTheme.colors.background)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                AuthScreen(
                    authRepository = container.authRepository,
                    onAuthSuccess = {
                        container.authRepository.checkExistingSession()
                    }
                )
            }
        }
        is AuthState.Authenticated -> {
            AuthenticatedAppScaffold(
                user = state.user,
                container = container
            )
        }
        is AuthState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FairShareTheme.colors.background)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Authentication Error",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.negative
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = FairShareTheme.typography.body,
                        color = FairShareTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    FSButton(
                        text = "Retry",
                        onClick = { container.authRepository.checkExistingSession() }
                    )
                }
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AuthenticatedAppScaffold(
    user: BackendUser,
    container: FairShareAppContainer
) {
    val scope = rememberCoroutineScope()
    val syncState by container.syncEngine.syncState.collectAsState()

    var currentScreen by remember { mutableStateOf(ScreenState.HOME) }
    var createGroupReturnScreen by remember { mutableStateOf(ScreenState.GROUPS) }
    var settlementReturnScreen by remember { mutableStateOf(ScreenState.HOME) }
    var groups by remember { mutableStateOf<List<GroupEntity>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<GroupEntity?>(null) }
    var overviewGroup by remember { mutableStateOf<GroupEntity?>(null) }
    var isLoadingGroups by remember { mutableStateOf(true) }

    val members = remember { mutableStateListOf<Member>() }
    val expenses = remember { mutableStateListOf<Expense>() }
    val payments = remember { mutableStateListOf<SettlementPayment>() }

    // Dialog state
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var showGroupSwitcherDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteCodeToShow by remember { mutableStateOf("") }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var showManageMembersDialog by remember { mutableStateOf(false) }

    // Phase 7 Expense State
    var showAddExpenseRouterDialog by remember { mutableStateOf(false) }
    var selectedExpenseForDetail by remember { mutableStateOf<Expense?>(null) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var expenseEntryReturnScreen by remember { mutableStateOf(ScreenState.HOME) }
    var activeExpenseEntryMode by remember { mutableStateOf<ExpenseEntryMode?>(null) }

    // Phase 9 Budget State
    var activeBudgetPlan by remember { mutableStateOf<BudgetPlan?>(null) }
    var showEditBudgetDialog by remember { mutableStateOf(false) }

    // Phase 11 Intelligence State
    var showNaturalLanguageDialog by remember { mutableStateOf(false) }
    var showWhatIfSimulatorDialog by remember { mutableStateOf(false) }
    var showBundlesDialog by remember { mutableStateOf(false) }

    // Phase 12 Collaboration, Search & Evidence State
    var showExportDialog by remember { mutableStateOf(false) }
    var viewingAttachment by remember { mutableStateOf<com.fairshare.android.core.domain.evidence.EvidenceAttachment?>(null) }
    var expenseComments by remember { mutableStateOf<List<com.fairshare.android.core.domain.collaboration.ExpenseComment>>(emptyList()) }

    LaunchedEffect(selectedExpenseForDetail?.id) {
        val expId = selectedExpenseForDetail?.id
        if (expId != null) {
            expenseComments = container.collaborationRepository.getExpenseComments(expId)
        }
    }

    fun refreshMembersAndLedger(groupId: String) {
        scope.launch {
            when (val res = container.backendGroupRepository.refreshGroupMembers(groupId)) {
                is NetworkResult.Success -> {
                    members.clear()
                    members.addAll(res.data)
                }
                is NetworkResult.Error -> {
                    val localUsers = container.database.groupDao().getMembersForGroup(groupId)
                    members.clear()
                    members.addAll(localUsers.map { Member(it.id, it.displayName, it.id == user.id) })
                }
            }

            if (members.none { it.id == user.id }) {
                members.add(0, Member(user.id, user.displayName, isCurrentUser = true))
            }

            val localExpenses = container.expenseRepository.getExpenses(groupId)
            expenses.clear()
            expenses.addAll(localExpenses)

            val localPayments = container.settlementRepository.getPayments(groupId)
            payments.clear()
            payments.addAll(localPayments)

            val localBudget = container.budgetRepository.getActiveBudget(groupId)
            activeBudgetPlan = localBudget
        }
    }

    // Load User's Groups on launch or change
    // Strategy: observe Room flow as single authoritative source of truth, and fetch & sync from backend
    LaunchedEffect(user.id) {
        isLoadingGroups = true
        val localGroups = container.database.groupDao().getUserActiveGroups(user.id)
        if (localGroups.isNotEmpty()) {
            groups = localGroups
            if (selectedGroup == null) {
                selectedGroup = localGroups.first()
            }
            isLoadingGroups = false
        }
        scope.launch {
            when (val res = container.backendGroupRepository.fetchUserGroups()) {
                is NetworkResult.Success -> {
                    val merged = (localGroups + res.data).distinctBy { it.id }
                    groups = merged
                    if (selectedGroup == null && merged.isNotEmpty()) {
                        selectedGroup = merged.first()
                    } else if (selectedGroup != null) {
                        selectedGroup = merged.find { it.id == selectedGroup?.id } ?: selectedGroup
                    }
                }
                is NetworkResult.Error -> {}
            }
            isLoadingGroups = false
        }
    }

    LaunchedEffect(user.id) {
        container.database.groupDao().getUserActiveGroupsFlow(user.id).collect { roomGroups ->
            if (roomGroups.isNotEmpty()) {
                groups = roomGroups
                if (selectedGroup == null) {
                    selectedGroup = roomGroups.first()
                } else {
                    selectedGroup = roomGroups.find { it.id == selectedGroup?.id } ?: roomGroups.firstOrNull()
                }
                isLoadingGroups = false
            }
        }
    }


    // When selected group changes, load its members and ledger from Room/Backend
    LaunchedEffect(selectedGroup?.id) {
        val group = selectedGroup ?: return@LaunchedEffect
        refreshMembersAndLedger(group.id)
    }

    val financialSummary = remember(members.toList(), expenses.toList(), payments.toList()) {
        FinancialEngine.calculateSummary(members, expenses, payments)
    }

    val currentMember = members.firstOrNull { it.isCurrentUser }
        ?: Member(user.id, user.displayName, isCurrentUser = true)

    val anyDialogOpen = showCreateGroupDialog || showJoinGroupDialog || showGroupSwitcherDialog ||
            showInviteDialog || showRecordPaymentDialog ||
            showManageMembersDialog || showAddExpenseRouterDialog || activeExpenseEntryMode != null ||
            showEditBudgetDialog || showNaturalLanguageDialog || showWhatIfSimulatorDialog || showBundlesDialog ||
            showExportDialog || viewingAttachment != null

    BackHandler(enabled = anyDialogOpen) {
        when {
            viewingAttachment != null -> viewingAttachment = null
            showExportDialog -> showExportDialog = false
            showNaturalLanguageDialog -> showNaturalLanguageDialog = false
            showWhatIfSimulatorDialog -> showWhatIfSimulatorDialog = false
            showBundlesDialog -> showBundlesDialog = false
            showAddExpenseRouterDialog -> showAddExpenseRouterDialog = false
            activeExpenseEntryMode != null -> activeExpenseEntryMode = null
            showEditBudgetDialog -> showEditBudgetDialog = false
            showInviteDialog -> showInviteDialog = false
            showGroupSwitcherDialog -> showGroupSwitcherDialog = false
            showRecordPaymentDialog -> showRecordPaymentDialog = false
            showManageMembersDialog -> showManageMembersDialog = false
            showJoinGroupDialog -> showJoinGroupDialog = false
            showCreateGroupDialog -> showCreateGroupDialog = false
        }
    }

    BackHandler(enabled = !anyDialogOpen && currentScreen != ScreenState.HOME) {
        currentScreen = when (currentScreen) {
            ScreenState.EXPENSE_DETAIL -> ScreenState.EXPENSE_LIST
            ScreenState.GROUP_OVERVIEW -> ScreenState.GROUPS
            ScreenState.CREATE_GROUP -> createGroupReturnScreen
            ScreenState.EXPENSE_ENTRY -> expenseEntryReturnScreen
            ScreenState.QUICK_EXPENSE_ENTRY -> expenseEntryReturnScreen
            ScreenState.RECEIPT_SCANNER -> expenseEntryReturnScreen
            ScreenState.SETTLEMENT -> settlementReturnScreen
            else -> ScreenState.HOME
        }
    }

    val showBottomBar = currentScreen in listOf(
        ScreenState.HOME,
        ScreenState.GROUPS,
        ScreenState.TRIPS,
        ScreenState.GROUP_CHAT,
        ScreenState.PROFILE
    )

    val contentComposable: @Composable () -> Unit = {
        androidx.compose.animation.Crossfade(
            targetState = currentScreen,
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
        ScreenState.EXPENSE_LIST -> {
            ExpenseListScreen(
                tripName = selectedGroup?.name ?: "Group",
                expenses = expenses,
                members = members,
                expenseSyncStatuses = emptyMap(),
                onExpenseClick = { exp ->
                    selectedExpenseForDetail = exp
                    currentScreen = ScreenState.EXPENSE_DETAIL
                },
                onAddExpenseClick = {
                    expenseEntryReturnScreen = ScreenState.EXPENSE_LIST
                    expenseToEdit = null
                    showAddExpenseRouterDialog = true
                },
                onBackClick = { currentScreen = ScreenState.HOME }
            )
        }
        ScreenState.EXPENSE_DETAIL -> {
            val exp = selectedExpenseForDetail
            if (exp != null) {
                ExpenseDetailScreen(
                    expense = exp,
                    members = members,
                    allExpenses = expenses,
                    comments = expenseComments,
                    syncStatus = SyncStatus.SYNCED,
                    onBackClick = { currentScreen = ScreenState.EXPENSE_LIST },
                    onEditClick = {
                        expenseToEdit = exp
                        expenseEntryReturnScreen = ScreenState.EXPENSE_DETAIL
                        activeExpenseEntryMode = ExpenseEntryMode.MANUAL
                        currentScreen = ScreenState.EXPENSE_ENTRY
                    },
                    onReverseExpense = { reason ->
                        val targetGroup = selectedGroup ?: return@ExpenseDetailScreen
                        scope.launch {
                            container.expenseRepository.reverseExpense(
                                id = exp.id,
                                groupId = targetGroup.id,
                                reason = reason,
                                actorId = user.id
                            )
                            refreshMembersAndLedger(targetGroup.id)
                            currentScreen = ScreenState.EXPENSE_LIST
                        }
                    },
                    onAddComment = { text ->
                        val targetGroup = selectedGroup ?: return@ExpenseDetailScreen
                        scope.launch {
                            val newComment = container.collaborationRepository.addExpenseComment(
                                expenseId = exp.id,
                                groupId = targetGroup.id,
                                authorId = user.id,
                                text = text
                            )
                            expenseComments = expenseComments + newComment
                        }
                    },
                    onViewEvidence = { attachmentId ->
                        scope.launch {
                            val att = container.attachmentRepository.getAttachmentById(attachmentId)
                            if (att != null) {
                                viewingAttachment = att
                            }
                        }
                    }
                )
            } else {
                currentScreen = ScreenState.EXPENSE_LIST
            }
        }
        ScreenState.EXPENSE_ENTRY -> {
            val targetGroup = selectedGroup
            if (targetGroup != null) {
                ExpenseEntryDialog(
                    initialMode = activeExpenseEntryMode ?: ExpenseEntryMode.MANUAL,
                    existingExpense = expenseToEdit,
                    members = members,
                    currency = Currency.INR,
                    ocrEngine = container.receiptOcrEngine,
                    onDismiss = {
                        activeExpenseEntryMode = null
                        expenseToEdit = null
                        currentScreen = expenseEntryReturnScreen
                    },
                    onSaveExpense = { savedExp ->
                        scope.launch {
                            if (expenseToEdit != null) {
                                try {
                                    container.expenseRepository.updateExpense(
                                        expense = savedExp,
                                        groupId = targetGroup.id,
                                        actorId = user.id
                                    )
                                } catch (_: Exception) {}
                                selectedExpenseForDetail = savedExp
                            } else {
                                try {
                                    container.expenseRepository.saveExpense(
                                        expense = savedExp,
                                        groupId = targetGroup.id,
                                        actorId = user.id
                                    )
                                } catch (_: Exception) {}
                            }
                            refreshMembersAndLedger(targetGroup.id)
                        }
                        activeExpenseEntryMode = null
                        expenseToEdit = null
                        currentScreen = ScreenState.EXPENSE_LIST
                    }
                )
            } else {
                currentScreen = ScreenState.HOME
            }
        }
        ScreenState.QUICK_EXPENSE_ENTRY -> {
            val targetGroup = selectedGroup
            if (targetGroup != null) {
                com.fairshare.android.feature.expenses.QuickExpenseEntryScreen(
                    groupName = targetGroup.name,
                    groupId = targetGroup.id,
                    members = members,
                    currency = Currency.INR,
                    onBackClick = {
                        currentScreen = expenseEntryReturnScreen
                    },
                    onSaveExpense = { savedExp ->
                        scope.launch {
                            try {
                                container.expenseRepository.saveExpense(
                                    expense = savedExp,
                                    groupId = targetGroup.id,
                                    actorId = user.id
                                )
                            } catch (_: Exception) {}
                            refreshMembersAndLedger(targetGroup.id)
                        }
                        currentScreen = ScreenState.EXPENSE_LIST
                    }
                )
            } else {
                currentScreen = ScreenState.HOME
            }
        }
        ScreenState.BUDGET -> {
            val g = selectedGroup
            if (g != null) {
                BudgetScreen(
                    tripName = g.name,
                    budgetPlan = activeBudgetPlan,
                    expenses = expenses,
                    onBackClick = { currentScreen = ScreenState.HOME },
                    onSaveBudget = { updatedPlan ->
                        scope.launch {
                            container.budgetRepository.setBudget(
                                groupId = g.id,
                                plan = updatedPlan,
                                actorId = user.id
                            )
                            activeBudgetPlan = container.budgetRepository.getActiveBudget(g.id)
                        }
                    },
                    onOpenDailyBrief = { currentScreen = ScreenState.DAILY_BRIEF }
                )
            } else {
                currentScreen = ScreenState.HOME
            }
        }
        ScreenState.DAILY_BRIEF -> {
            val g = selectedGroup
            if (g != null) {
                DailyBriefScreen(
                    tripName = g.name,
                    budgetPlan = activeBudgetPlan,
                    allExpenses = expenses,
                    payments = payments,
                    members = members,
                    onBackClick = { currentScreen = ScreenState.HOME },
                    onExpenseClick = { exp ->
                        selectedExpenseForDetail = exp
                        currentScreen = ScreenState.EXPENSE_DETAIL
                    },
                    onOpenBudgetClick = { currentScreen = ScreenState.BUDGET }
                )
            } else {
                currentScreen = ScreenState.HOME
            }
        }
        ScreenState.TRIPS -> {
            com.fairshare.android.feature.trips.TripPlannerScreen(
                tripRepository = container.tripRepository,
                groups = groups,
                expenses = expenses,
                members = members,
                payments = payments,
                onBackClick = { currentScreen = ScreenState.HOME },
                onAddExpense = { fuelExp ->
                    val gId = fuelExp.groupId.ifBlank { selectedGroup?.id ?: groups.firstOrNull()?.id ?: "" }
                    if (gId.isNotBlank()) {
                        scope.launch {
                            container.expenseRepository.saveExpense(
                                expense = fuelExp.copy(groupId = gId),
                                groupId = gId,
                                actorId = user.id
                            )
                            refreshMembersAndLedger(gId)
                        }
                    }
                }
            )
        }
        ScreenState.RECEIPT_SCANNER -> {
            val targetGroup = selectedGroup
            if (targetGroup != null) {
                com.fairshare.android.feature.receipt.ReceiptScannerScreen(
                    members = members,
                    ocrEngine = container.receiptOcrEngine,
                    onBackClick = { currentScreen = ScreenState.HOME },
                    onConfirmExpense = { newExp ->
                        scope.launch {
                            container.expenseRepository.saveExpense(
                                expense = newExp.copy(groupId = targetGroup.id),
                                groupId = targetGroup.id,
                                actorId = user.id
                            )
                            refreshMembersAndLedger(targetGroup.id)
                            currentScreen = ScreenState.EXPENSE_LIST
                        }
                    }
                )
            } else {
                currentScreen = ScreenState.HOME
            }
        }
        ScreenState.TRANSACTION_DETECTION -> {
            val targetGroup = selectedGroup
            if (targetGroup != null) {
                com.fairshare.android.feature.transactions.TransactionDetectionScreen(
                    candidateRepository = container.candidateRepository,
                    existingExpenses = expenses,
                    members = members,
                    onBackClick = { currentScreen = ScreenState.HOME },
                    onConfirmExpense = { newExp ->
                        scope.launch {
                            container.expenseRepository.saveExpense(
                                expense = newExp.copy(groupId = targetGroup.id),
                                groupId = targetGroup.id,
                                actorId = user.id
                            )
                            refreshMembersAndLedger(targetGroup.id)
                            currentScreen = ScreenState.EXPENSE_LIST
                        }
                    }
                )
            } else {
                currentScreen = ScreenState.HOME
            }
        }
        ScreenState.VAULT -> {
            val targetGroup = selectedGroup
            if (targetGroup != null) {
                com.fairshare.android.feature.vault.VaultScreen(
                    groupName = targetGroup.name,
                    groupId = targetGroup.id,
                    members = members,
                    expenses = expenses,
                    onBackClick = { currentScreen = ScreenState.HOME }
                )
            } else {
                currentScreen = ScreenState.HOME
            }
        }
        ScreenState.GROUP_CHAT -> {
            BackHandler { currentScreen = ScreenState.HOME }
            ChatScreen(
                groups = groups,
                selectedGroupId = selectedGroup?.id,
                onSelectGroup = { g -> selectedGroup = g },
                container = container,
                currentUserId = user.id,
                onNavigateBack = { currentScreen = ScreenState.HOME },
                onOpenExpense = { expId ->
                    val exp = expenses.firstOrNull { it.id == expId }
                    if (exp != null) {
                        selectedExpenseForDetail = exp
                        currentScreen = ScreenState.EXPENSE_DETAIL
                    }
                },
                onMemberAdded = {
                    val gId = selectedGroup?.id ?: groups.firstOrNull()?.id
                    if (gId != null) {
                        refreshMembersAndLedger(gId)
                    }
                }
            )
        }
        ScreenState.GROUP_ACTIVITY -> {
            val g = selectedGroup ?: groups.firstOrNull()
            if (g != null) {
                BackHandler { currentScreen = ScreenState.HOME }
                com.fairshare.android.feature.collaboration.GroupActivityScreen(
                    groupId = g.id,
                    groupName = g.name,
                    container = container,
                    onNavigateBack = { currentScreen = ScreenState.HOME },
                    onNavigateToEntity = { entityType, entityId ->
                        if (entityType == "EXPENSE" && entityId != null) {
                            val exp = expenses.firstOrNull { it.id == entityId }
                            if (exp != null) {
                                selectedExpenseForDetail = exp
                                currentScreen = ScreenState.EXPENSE_DETAIL
                            }
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No group activity yet.\nCreate or join a group to see activity.",
                        style = FairShareTheme.typography.body,
                        color = FairShareTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        ScreenState.SEARCH -> {
            BackHandler { currentScreen = ScreenState.HOME }
            com.fairshare.android.feature.search.SearchScreen(
                groupId = selectedGroup?.id,
                container = container,
                onNavigateBack = { currentScreen = ScreenState.HOME },
                onNavigateToResult = { type, entityId ->
                    if (type == "EXPENSE") {
                        val exp = expenses.firstOrNull { it.id == entityId }
                        if (exp != null) {
                            selectedExpenseForDetail = exp
                            currentScreen = ScreenState.EXPENSE_DETAIL
                        }
                    }
                }
            )
        }
        ScreenState.PROFILE -> {
            ProfileScreen(
                user = user,
                authRepository = container.authRepository,
                onBackClick = { currentScreen = ScreenState.HOME },
                onLogoutSuccess = {
                    groups = emptyList()
                    selectedGroup = null
                    overviewGroup = null
                    members.clear()
                    expenses.clear()
                    payments.clear()
                    currentScreen = ScreenState.HOME
                }
            )
        }
        ScreenState.SETTLEMENT -> {
            SettlementScreen(
                tripName = selectedGroup?.name ?: "Group Settlement",
                members = members,
                expenses = expenses,
                payments = payments,
                groupId = selectedGroup?.id ?: "",
                currentUserMemberId = user.id,
                onBackClick = { currentScreen = settlementReturnScreen },
                onSavePayment = { newPayment ->
                    val targetGroup = selectedGroup ?: throw IllegalStateException("No group selected")
                    container.settlementRepository.recordPayment(
                        payment = newPayment,
                        groupId = targetGroup.id,
                        actorId = user.id
                    )
                    refreshMembersAndLedger(targetGroup.id)
                }
            )
        }
        ScreenState.CREATE_GROUP -> {
            com.fairshare.android.feature.groups.CreateGroupScreen(
                repository = container.backendGroupRepository,
                onBack = { currentScreen = createGroupReturnScreen },
                onGroupCreated = { newGroup ->
                    groups = (groups + newGroup).distinctBy { it.id }
                    selectedGroup = newGroup
                    overviewGroup = newGroup
                    currentScreen = ScreenState.GROUP_OVERVIEW
                }
            )
        }
        ScreenState.GROUPS -> {
            GroupsScreen(
                currentUser = user,
                activeGroupId = selectedGroup?.id,
                repository = container.backendGroupRepository,
                expenseRepository = container.expenseRepository,
                settlementRepository = container.settlementRepository,
                onSelectGroup = { g ->
                    selectedGroup = g
                    currentScreen = ScreenState.HOME
                },
                onOpenGroupOverview = { g ->
                    overviewGroup = g
                    currentScreen = ScreenState.GROUP_OVERVIEW
                },
                onCreateGroupClick = {
                    createGroupReturnScreen = ScreenState.GROUPS
                    currentScreen = ScreenState.CREATE_GROUP
                },
                onJoinGroupClick = { showJoinGroupDialog = true },
                onBackClick = { currentScreen = ScreenState.HOME },
                initialGroups = groups
            )
        }
        ScreenState.GROUP_OVERVIEW -> {
            val g = overviewGroup ?: selectedGroup
            if (g != null) {
                GroupOverviewScreen(
                    group = g,
                    currentUser = user,
                    repository = container.backendGroupRepository,
                    expenseRepository = container.expenseRepository,
                    settlementRepository = container.settlementRepository,
                    onBackClick = { currentScreen = ScreenState.GROUPS },
                    onAddExpenseClick = {
                        selectedGroup = g
                        expenseEntryReturnScreen = ScreenState.GROUP_OVERVIEW
                        expenseToEdit = null
                        showAddExpenseRouterDialog = true
                    },
                    onSettleUpClick = {
                        selectedGroup = g
                        settlementReturnScreen = ScreenState.GROUP_OVERVIEW
                        currentScreen = ScreenState.SETTLEMENT
                    },
                    onManageMembersClick = {
                        selectedGroup = g
                        showManageMembersDialog = true
                    },
                    onInviteClick = {
                        selectedGroup = g
                        scope.launch {
                            when (val inv = container.apiClient.createInvitation(g.id)) {
                                is NetworkResult.Success -> {
                                    inviteCodeToShow = inv.data.inviteCode
                                    showInviteDialog = true
                                }
                                is NetworkResult.Error -> {
                                    inviteCodeToShow = "FAIR-${g.id.take(4).uppercase()}"
                                    showInviteDialog = true
                                }
                            }
                        }
                    },
                    onGroupUpdated = { updatedGroup ->
                        overviewGroup = updatedGroup
                        groups = groups.map { if (it.id == updatedGroup.id) updatedGroup else it }
                        if (selectedGroup?.id == updatedGroup.id) {
                            selectedGroup = updatedGroup
                        }
                    },
                    onChatClick = {
                        selectedGroup = g
                        currentScreen = ScreenState.GROUP_CHAT
                    },
                    onActivityClick = {
                        selectedGroup = g
                        currentScreen = ScreenState.GROUP_ACTIVITY
                    },
                    onTripPlannerClick = {
                        selectedGroup = g
                        currentScreen = ScreenState.TRIPS
                    },
                    onBudgetClick = {
                        selectedGroup = g
                        currentScreen = ScreenState.BUDGET
                    }
                )
            } else {
                currentScreen = ScreenState.HOME
            }
        }
        ScreenState.HOME -> {
            if (isLoadingGroups) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = FairShareTheme.colors.accent,
                        strokeWidth = 2.dp
                    )
                }
            } else {
                HomeScreen(
                    currentMember = currentMember,
                    tripName = selectedGroup?.name ?: "Group",
                    summary = financialSummary,
                    expenses = expenses,
                    payments = payments,
                    budgetPlan = activeBudgetPlan,
                    onAddExpenseClick = {
                        if (selectedGroup == null && groups.isNotEmpty()) {
                            selectedGroup = groups.first()
                        }
                        expenseEntryReturnScreen = ScreenState.HOME
                        expenseToEdit = null
                        showAddExpenseRouterDialog = true
                    },
                    onRecordPaymentClick = { showRecordPaymentDialog = true },
                    onViewSettlementClick = {
                        settlementReturnScreen = ScreenState.HOME
                        currentScreen = ScreenState.SETTLEMENT
                    },
                    onManageMembersClick = { showManageMembersDialog = true },
                    onSetBudgetClick = { showEditBudgetDialog = true },
                    onEditBudgetClick = { showEditBudgetDialog = true },
                    onViewBudgetClick = { currentScreen = ScreenState.BUDGET },
                    onViewDailyBriefClick = { currentScreen = ScreenState.DAILY_BRIEF },
                    onViewVaultClick = { currentScreen = ScreenState.VAULT },
                    onViewBundlesClick = { showBundlesDialog = true },
                    onViewChatClick = { currentScreen = ScreenState.GROUP_CHAT },
                    onViewActivityClick = { currentScreen = ScreenState.GROUP_ACTIVITY },
                    onViewSearchClick = { currentScreen = ScreenState.SEARCH },
                    onViewExportClick = { showExportDialog = true },
                    onProfileClick = { currentScreen = ScreenState.PROFILE },
                    onExpenseClick = { exp ->
                        selectedExpenseForDetail = exp
                        currentScreen = ScreenState.EXPENSE_DETAIL
                    },
                    onViewAllExpensesClick = { currentScreen = ScreenState.EXPENSE_LIST },
                    onInviteClick = {
                        val g = selectedGroup
                        if (g != null) {
                            scope.launch {
                                when (val inv = container.apiClient.createInvitation(g.id)) {
                                    is NetworkResult.Success -> {
                                        inviteCodeToShow = inv.data.inviteCode
                                        showInviteDialog = true
                                    }
                                    is NetworkResult.Error -> {
                                        inviteCodeToShow = "FAIR-${g.id.take(4).uppercase()}"
                                        showInviteDialog = true
                                    }
                                }
                            }
                        }
                    },
                    onSwitchGroupClick = { showGroupSwitcherDialog = true },
                    syncState = syncState,
                    onSyncClick = { scope.launch { container.syncEngine.syncAll() } },
                    onRetrySyncClick = { scope.launch { container.syncEngine.retryFailedOperations() } },
                    onResolveConflictKeepLocal = { id, type -> scope.launch { container.syncEngine.resolveConflictKeepLocal(id, type) } },
                    onResolveConflictAcceptRemote = { id, type -> scope.launch { container.syncEngine.resolveConflictAcceptRemote(id, type) } },
                    hasGroups = groups.isNotEmpty(),
                    onCreateGroupClick = {
                        createGroupReturnScreen = ScreenState.HOME
                        currentScreen = ScreenState.CREATE_GROUP
                    },
                    onJoinGroupClick = { showJoinGroupDialog = true }
                )
            }
        }
    }
    }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(FairShareTheme.colors.background)
    ) {
        val isTablet = this.maxWidth >= 600.dp
        if (isTablet) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                NavigationRail(
                    containerColor = FairShareTheme.colors.surface,
                    contentColor = FairShareTheme.colors.textPrimary
                ) {
                    val userAccountLabel = remember(user.displayName) {
                        val trimmed = user.displayName.trim()
                        if (trimmed.isNotBlank()) trimmed else "Account"
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    NavigationRailItem(
                        selected = currentScreen == ScreenState.HOME,
                        onClick = { currentScreen = ScreenState.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home", style = FairShareTheme.typography.metadata, maxLines = 1) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = FairShareTheme.colors.accent,
                            selectedTextColor = FairShareTheme.colors.accent,
                            unselectedIconColor = FairShareTheme.colors.textSecondary,
                            unselectedTextColor = FairShareTheme.colors.textSecondary,
                            indicatorColor = FairShareTheme.colors.surfaceElevated
                        )
                    )
                    NavigationRailItem(
                        selected = currentScreen == ScreenState.GROUPS || currentScreen == ScreenState.GROUP_OVERVIEW,
                        onClick = { currentScreen = ScreenState.GROUPS },
                        icon = { Icon(Icons.Default.Group, contentDescription = "Groups") },
                        label = { Text("Groups", style = FairShareTheme.typography.metadata, maxLines = 1) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = FairShareTheme.colors.accent,
                            selectedTextColor = FairShareTheme.colors.accent,
                            unselectedIconColor = FairShareTheme.colors.textSecondary,
                            unselectedTextColor = FairShareTheme.colors.textSecondary,
                            indicatorColor = FairShareTheme.colors.surfaceElevated
                        )
                    )
                    NavigationRailItem(
                        selected = currentScreen == ScreenState.TRIPS,
                        onClick = { currentScreen = ScreenState.TRIPS },
                        icon = { Icon(Icons.Default.Place, contentDescription = "Trips") },
                        label = { Text("Trips", style = FairShareTheme.typography.metadata, maxLines = 1) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = FairShareTheme.colors.accent,
                            selectedTextColor = FairShareTheme.colors.accent,
                            unselectedIconColor = FairShareTheme.colors.textSecondary,
                            unselectedTextColor = FairShareTheme.colors.textSecondary,
                            indicatorColor = FairShareTheme.colors.surfaceElevated
                        )
                    )
                    NavigationRailItem(
                        selected = currentScreen == ScreenState.GROUP_CHAT,
                        onClick = { currentScreen = ScreenState.GROUP_CHAT },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                        label = { Text("Chat", style = FairShareTheme.typography.metadata, maxLines = 1) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = FairShareTheme.colors.accent,
                            selectedTextColor = FairShareTheme.colors.accent,
                            unselectedIconColor = FairShareTheme.colors.textSecondary,
                            unselectedTextColor = FairShareTheme.colors.textSecondary,
                            indicatorColor = FairShareTheme.colors.surfaceElevated
                        )
                    )
                    NavigationRailItem(
                        selected = currentScreen == ScreenState.PROFILE,
                        onClick = { currentScreen = ScreenState.PROFILE },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Account") },
                        label = { Text(userAccountLabel, style = FairShareTheme.typography.metadata, maxLines = 1) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = FairShareTheme.colors.accent,
                            selectedTextColor = FairShareTheme.colors.accent,
                            unselectedIconColor = FairShareTheme.colors.textSecondary,
                            unselectedTextColor = FairShareTheme.colors.textSecondary,
                            indicatorColor = FairShareTheme.colors.surfaceElevated
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    contentComposable()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .then(if (!showBottomBar) Modifier.navigationBarsPadding() else Modifier)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    contentComposable()
                }
                if (showBottomBar) {
                    val userAccountLabel = remember(user.displayName) {
                        val trimmed = user.displayName.trim()
                        if (trimmed.isNotBlank()) trimmed else "Account"
                    }

                    NavigationBar(
                        containerColor = FairShareTheme.colors.surface,
                        contentColor = FairShareTheme.colors.textPrimary,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == ScreenState.HOME,
                            onClick = { currentScreen = ScreenState.HOME },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home", style = FairShareTheme.typography.metadata, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FairShareTheme.colors.accent,
                                selectedTextColor = FairShareTheme.colors.accent,
                                unselectedIconColor = FairShareTheme.colors.textSecondary,
                                unselectedTextColor = FairShareTheme.colors.textSecondary,
                                indicatorColor = FairShareTheme.colors.surfaceElevated
                            )
                        )
                        NavigationBarItem(
                            selected = currentScreen == ScreenState.GROUPS || currentScreen == ScreenState.GROUP_OVERVIEW,
                            onClick = { currentScreen = ScreenState.GROUPS },
                            icon = { Icon(Icons.Default.Group, contentDescription = "Groups") },
                            label = { Text("Groups", style = FairShareTheme.typography.metadata, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FairShareTheme.colors.accent,
                                selectedTextColor = FairShareTheme.colors.accent,
                                unselectedIconColor = FairShareTheme.colors.textSecondary,
                                unselectedTextColor = FairShareTheme.colors.textSecondary,
                                indicatorColor = FairShareTheme.colors.surfaceElevated
                            )
                        )
                        NavigationBarItem(
                            selected = currentScreen == ScreenState.TRIPS,
                            onClick = { currentScreen = ScreenState.TRIPS },
                            icon = { Icon(Icons.Default.Place, contentDescription = "Trips") },
                            label = { Text("Trips", style = FairShareTheme.typography.metadata, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FairShareTheme.colors.accent,
                                selectedTextColor = FairShareTheme.colors.accent,
                                unselectedIconColor = FairShareTheme.colors.textSecondary,
                                unselectedTextColor = FairShareTheme.colors.textSecondary,
                                indicatorColor = FairShareTheme.colors.surfaceElevated
                            )
                        )
                        NavigationBarItem(
                            selected = currentScreen == ScreenState.GROUP_CHAT,
                            onClick = { currentScreen = ScreenState.GROUP_CHAT },
                            icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                            label = { Text("Chat", style = FairShareTheme.typography.metadata, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FairShareTheme.colors.accent,
                                selectedTextColor = FairShareTheme.colors.accent,
                                unselectedIconColor = FairShareTheme.colors.textSecondary,
                                unselectedTextColor = FairShareTheme.colors.textSecondary,
                                indicatorColor = FairShareTheme.colors.surfaceElevated
                            )
                        )
                        NavigationBarItem(
                            selected = currentScreen == ScreenState.PROFILE,
                            onClick = { currentScreen = ScreenState.PROFILE },
                            icon = { Icon(Icons.Default.Person, contentDescription = "Account") },
                            label = { Text(userAccountLabel, style = FairShareTheme.typography.metadata, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FairShareTheme.colors.accent,
                                selectedTextColor = FairShareTheme.colors.accent,
                                unselectedIconColor = FairShareTheme.colors.textSecondary,
                                unselectedTextColor = FairShareTheme.colors.textSecondary,
                                indicatorColor = FairShareTheme.colors.surfaceElevated
                            )
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showCreateGroupDialog) {
        CreateGroupDialog(
            repository = container.backendGroupRepository,
            onDismiss = { showCreateGroupDialog = false },
            onGroupCreated = { newGroup: GroupEntity ->
                groups = groups + newGroup
                selectedGroup = newGroup
                showCreateGroupDialog = false
            }
        )
    }

    if (showJoinGroupDialog) {
        JoinGroupDialog(
            repository = container.backendGroupRepository,
            onDismiss = { showJoinGroupDialog = false },
            onGroupJoined = { joinedGroup: GroupEntity ->
                if (groups.none { it.id == joinedGroup.id }) {
                    groups = groups + joinedGroup
                }
                selectedGroup = joinedGroup
                showJoinGroupDialog = false
            }
        )
    }

    if (showGroupSwitcherDialog) {
        GroupSwitcherDialog(
            groups = groups,
            selectedGroupId = selectedGroup?.id ?: "",
            onSelectGroup = { g -> selectedGroup = g },
            onCreateNewGroupClick = {
                createGroupReturnScreen = currentScreen
                showGroupSwitcherDialog = false
                currentScreen = ScreenState.CREATE_GROUP
            },
            onJoinGroupClick = { showJoinGroupDialog = true },
            onManageAllGroupsClick = { currentScreen = ScreenState.GROUPS },
            onDismiss = { showGroupSwitcherDialog = false }
        )
    }

    if (showInviteDialog && selectedGroup != null) {
        GroupInvitationDialog(
            groupId = selectedGroup!!.id,
            groupName = selectedGroup!!.name,
            inviteCode = inviteCodeToShow,
            repository = container.backendGroupRepository,
            onDismiss = { showInviteDialog = false }
        )
    }

    if (showRecordPaymentDialog && selectedGroup != null) {
        RecordPaymentDialog(
            members = members,
            groupId = selectedGroup!!.id,
            onDismiss = { showRecordPaymentDialog = false },
            onSavePayment = { newPayment ->
                val targetGroup = selectedGroup ?: throw IllegalStateException("No active group selected")
                container.settlementRepository.recordPayment(
                    payment = newPayment,
                    groupId = targetGroup.id,
                    actorId = user.id
                )
                refreshMembersAndLedger(targetGroup.id)
            }
        )
    }

    if (showManageMembersDialog && selectedGroup != null) {
        ManageMembersDialog(
            groupId = selectedGroup!!.id,
            groupName = selectedGroup!!.name,
            currentUserId = user.id,
            repository = container.backendGroupRepository,
            onDismiss = { showManageMembersDialog = false },
            onInviteClick = {
                showManageMembersDialog = false
                scope.launch {
                    when (val inv = container.apiClient.createInvitation(selectedGroup!!.id)) {
                        is NetworkResult.Success -> {
                            inviteCodeToShow = inv.data.inviteCode
                            showInviteDialog = true
                        }
                        is NetworkResult.Error -> {
                            inviteCodeToShow = "FAIR-${selectedGroup!!.id.take(4).uppercase()}"
                            showInviteDialog = true
                        }
                    }
                }
            },
            onMemberChanged = {
                refreshMembersAndLedger(selectedGroup!!.id)
            }
        )
    }

    if (showEditBudgetDialog && selectedGroup != null) {
        EditBudgetDialog(
            initialPlan = activeBudgetPlan,
            onDismiss = { showEditBudgetDialog = false },
            onSaveBudget = { updatedPlan ->
                val targetGroup = selectedGroup ?: return@EditBudgetDialog
                scope.launch {
                    container.budgetRepository.setBudget(
                        groupId = targetGroup.id,
                        plan = updatedPlan,
                        actorId = user.id
                    )
                    activeBudgetPlan = container.budgetRepository.getActiveBudget(targetGroup.id)
                    showEditBudgetDialog = false
                }
            }
        )
    }

    if (showNaturalLanguageDialog && selectedGroup != null) {
        val targetGroup = selectedGroup!!
        com.fairshare.android.feature.expenses.NaturalLanguageEntryDialog(
            members = members,
            currentMember = currentMember,
            onDismiss = { showNaturalLanguageDialog = false },
            onConfirmExpense = { newExp ->
                scope.launch {
                    container.expenseRepository.saveExpense(
                        expense = newExp.copy(groupId = targetGroup.id),
                        groupId = targetGroup.id,
                        actorId = user.id
                    )
                    refreshMembersAndLedger(targetGroup.id)
                    showNaturalLanguageDialog = false
                    currentScreen = ScreenState.EXPENSE_LIST
                }
            }
        )
    }

    if (showWhatIfSimulatorDialog && selectedGroup != null) {
        com.fairshare.android.feature.expenses.WhatIfSimulatorDialog(
            members = members,
            currentExpenses = expenses,
            currentPayments = payments,
            budgetPlan = activeBudgetPlan,
            onDismiss = { showWhatIfSimulatorDialog = false }
        )
    }

    if (showBundlesDialog && selectedGroup != null) {
        com.fairshare.android.feature.expenses.ExpenseBundlesDialog(
            expenses = expenses,
            onExpenseClick = { exp ->
                selectedExpenseForDetail = exp
                showBundlesDialog = false
                currentScreen = ScreenState.EXPENSE_DETAIL
            },
            onDismiss = { showBundlesDialog = false }
        )
    }

    if (showExportDialog && selectedGroup != null) {
        val targetGroup = selectedGroup!!
        val summary = remember(expenses, payments, members) {
            FinancialEngine.calculateSummary(
                members = members,
                expenses = expenses,
                payments = payments
            )
        }
        com.fairshare.android.feature.export.ExportShareDialog(
            groupName = targetGroup.name,
            expenses = expenses,
            payments = payments,
            members = members,
            balances = summary.balances,
            onDismiss = { showExportDialog = false }
        )
    }

    if (viewingAttachment != null) {
        com.fairshare.android.feature.evidence.EvidenceViewerDialog(
            attachment = viewingAttachment!!,
            onDismiss = { viewingAttachment = null },
            onDelete = {
                val att = viewingAttachment!!
                scope.launch {
                    container.attachmentRepository.deleteAttachment(att.id)
                    viewingAttachment = null
                }
            }
        )
    }

    if (showAddExpenseRouterDialog) {
        AddExpenseRouterDialog(
            onSelectMode = { mode ->
                showAddExpenseRouterDialog = false
                if (selectedGroup == null && groups.isNotEmpty()) {
                    selectedGroup = groups.first()
                }
                when (mode) {
                    ExpenseEntryMode.MANUAL -> {
                        activeExpenseEntryMode = ExpenseEntryMode.MANUAL
                        currentScreen = ScreenState.EXPENSE_ENTRY
                    }
                    ExpenseEntryMode.QUICK_ENTRY -> {
                        currentScreen = ScreenState.QUICK_EXPENSE_ENTRY
                    }
                    ExpenseEntryMode.NATURAL_LANGUAGE -> {
                        showNaturalLanguageDialog = true
                    }
                    ExpenseEntryMode.RECEIPT_SCAN -> {
                        currentScreen = ScreenState.RECEIPT_SCANNER
                    }
                    ExpenseEntryMode.TRANSACTION_IMPORT -> {
                        currentScreen = ScreenState.TRANSACTION_DETECTION
                    }
                    ExpenseEntryMode.SIMULATOR -> {
                        showWhatIfSimulatorDialog = true
                    }
                }
            },
            onDismiss = { showAddExpenseRouterDialog = false }
        )
    }
}
