package com.fairshare.android.feature.expenses

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.ExpenseItem
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.domain.receipt.LocalReceiptOcrEngine
import com.fairshare.android.core.domain.receipt.ParsedReceipt
import com.fairshare.android.core.domain.receipt.ReceiptLineItem
import com.fairshare.android.core.domain.receipt.ReceiptOcrEngine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun ExpenseEntryDialog(
    initialMode: ExpenseEntryMode = ExpenseEntryMode.MANUAL,
    existingExpense: Expense? = null,
    members: List<Member>,
    currency: Currency = Currency.INR,
    ocrEngine: ReceiptOcrEngine = remember { LocalReceiptOcrEngine() },
    onDismiss: () -> Unit,
    onSaveExpense: (Expense) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEditMode = existingExpense != null

    // Form state
    var description by remember { mutableStateOf(existingExpense?.description ?: "") }
    var amountText by remember {
        val initialPaise = existingExpense?.totalAmount?.amountMinor
        val str = if (initialPaise != null && initialPaise > 0L) {
            val r = initialPaise / 100
            val p = initialPaise % 100
            if (p == 0L) "$r" else String.format(Locale.ROOT, "%.2f", initialPaise / 100.0)
        } else ""
        mutableStateOf(str)
    }

    // Payers state
    var payers by remember {
        val defaultPayerId = members.find { it.isCurrentUser }?.id ?: members.firstOrNull()?.id ?: ""
        val list = existingExpense?.payers ?: listOf(ExpensePayer(defaultPayerId, existingExpense?.totalAmount ?: Money.zero(currency)))
        mutableStateOf(list)
    }

    // Split state
    var splitMethod by remember { mutableStateOf(existingExpense?.splitMethod ?: SplitMethod.EQUAL) }
    var participants by remember {
        val list = existingExpense?.participants ?: members.map { ExpenseParticipant(it.id) }
        mutableStateOf(list)
    }
    var items by remember {
        mutableStateOf(existingExpense?.items ?: emptyList<ExpenseItem>())
    }

    // Metadata state
    var category by remember { mutableStateOf(existingExpense?.category ?: ExpenseCategory.FOOD) }
    var paymentMode by remember { mutableStateOf(existingExpense?.mode ?: PaymentMode.UPI) }
    var notes by remember { mutableStateOf(existingExpense?.notes ?: "") }
    var timestamp by remember { mutableStateOf(existingExpense?.timestamp ?: System.currentTimeMillis()) }

    // Dialog / Sub-sheet visibility
    var showPayerDialog by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showPaymentModeDialog by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var showReceiptScanModal by remember { mutableStateOf(false) }

    // Receipt OCR flow states
    var receiptOcrInProgress by remember { mutableStateOf(false) }
    var receiptOcrError by remember { mutableStateOf<String?>(null) }
    var parsedReceiptForReview by remember { mutableStateOf<ParsedReceipt?>(null) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }

    // Gallery Picker Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            receiptOcrInProgress = true
            receiptOcrError = null
            scope.launch {
                val res = ocrEngine.processReceiptImage(context, uri, currency)
                receiptOcrInProgress = false
                res.fold(
                    onSuccess = { receipt ->
                        parsedReceiptForReview = receipt
                    },
                    onFailure = { err ->
                        receiptOcrError = err.message ?: "Failed to extract text from receipt image"
                    }
                )
            }
        }
    }

    // Camera Capture Launcher
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            receiptOcrInProgress = true
            receiptOcrError = null
            scope.launch {
                val res = ocrEngine.processReceiptBitmap(bitmap, currency)
                receiptOcrInProgress = false
                res.fold(
                    onSuccess = { receipt ->
                        parsedReceiptForReview = receipt
                    },
                    onFailure = { err ->
                        receiptOcrError = err.message ?: "Failed to process captured camera image"
                    }
                )
            }
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraPermissionDenied = false
            cameraCaptureLauncher.launch(null)
        } else {
            cameraPermissionDenied = true
        }
    }

    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            cameraPermissionDenied = false
            cameraCaptureLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Computed total Money
    val parsedAmountMinor = remember(amountText) {
        val rupees = amountText.toDoubleOrNull() ?: 0.0
        (rupees * 100).toLong()
    }
    val currentTotalMoney = remember(parsedAmountMinor, currency) {
        Money(parsedAmountMinor, currency)
    }

    // Update payers amount when total changes in single-payer scenario
    val effectivePayers = remember(payers, currentTotalMoney) {
        if (payers.size <= 1) {
            val payerId = payers.firstOrNull()?.memberId ?: members.firstOrNull()?.id ?: ""
            listOf(ExpensePayer(payerId, currentTotalMoney))
        } else {
            payers
        }
    }

    val isAmountValid = parsedAmountMinor > 0L
    val isDescriptionValid = description.trim().isNotBlank()
    val isFormValid = isAmountValid && isDescriptionValid

    val expenseToSave = remember(
        description,
        currentTotalMoney,
        effectivePayers,
        participants,
        splitMethod,
        items,
        paymentMode,
        category,
        notes,
        timestamp
    ) {
        Expense(
            id = existingExpense?.id ?: UUID.randomUUID().toString(),
            groupId = existingExpense?.groupId ?: "",
            description = description.trim(),
            totalAmount = currentTotalMoney,
            payers = effectivePayers,
            participants = participants,
            splitMethod = splitMethod,
            items = items,
            mode = paymentMode,
            category = category,
            timestamp = timestamp,
            notes = notes.trim().ifEmpty { null },
            isDeleted = false
        )
    }

    val formattedDateStr = remember(timestamp) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FairShareTheme.colors.background)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FairShareTheme.colors.surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "‹ Back",
                        style = FairShareTheme.typography.body,
                        color = FairShareTheme.colors.accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = if (isEditMode) "Edit Expense" else "Add Expense",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )

                // Scan Receipt Icon Button in Header
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FairShareTheme.colors.accentSoft,
                    border = androidx.compose.foundation.BorderStroke(1.dp, FairShareTheme.colors.accent),
                    modifier = Modifier.clickable { showReceiptScanModal = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = "Scan Receipt",
                            tint = FairShareTheme.colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Scan",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                if (isEditMode) {
                    Text(
                        text = "Editing this expense will recalculate balances.",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.accent
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 1. Amount Input Box
                Text(
                    text = "Amount",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) {
                            amountText = input
                        }
                    },
                    placeholder = { Text("0.00", color = FairShareTheme.colors.disabled, fontSize = 24.sp) },
                    prefix = {
                        Text(
                            text = "${currency.symbol} ",
                            color = FairShareTheme.colors.accent,
                            style = FairShareTheme.typography.title,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = FairShareTheme.typography.display.copy(color = FairShareTheme.colors.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Description Input
                Text(
                    text = "For What?",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("e.g. Dinner at Punjab Grill, Uber to Airport, Groceries", color = FairShareTheme.colors.disabled) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Paid By Selector Row
                val payerSummaryText = remember(effectivePayers, members) {
                    if (effectivePayers.size == 1) {
                        val p = effectivePayers.first()
                        val m = members.find { it.id == p.memberId }
                        if (m?.isCurrentUser == true) "You" else m?.name ?: "1 Person"
                    } else {
                        "${effectivePayers.size} people split payment"
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FairShareTheme.colors.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, FairShareTheme.colors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPayerDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Paid By",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textSecondary
                            )
                            Text(
                                text = payerSummaryText,
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Change >",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Split Method & Participant Selector Row
                val activeParticipantsCount = participants.count { !it.excluded }
                val splitSummaryText = remember(splitMethod, activeParticipantsCount, currentTotalMoney, items) {
                    when (splitMethod) {
                        SplitMethod.EQUAL -> {
                            if (activeParticipantsCount > 0 && currentTotalMoney.amountMinor > 0L) {
                                val each = currentTotalMoney.amountMinor / activeParticipantsCount
                                "Split equally among $activeParticipantsCount · ${Money(each, currency).formatted()} each"
                            } else "Split equally ($activeParticipantsCount participants)"
                        }
                        SplitMethod.EXACT -> "Exact amounts split ($activeParticipantsCount participants)"
                        SplitMethod.PERCENTAGE -> "Percentage split ($activeParticipantsCount participants)"
                        SplitMethod.SHARES -> "Shares split ($activeParticipantsCount participants)"
                        SplitMethod.ITEMIZED -> "Itemized split (${items.size} items)"
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FairShareTheme.colors.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, FairShareTheme.colors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSplitDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Split Method (${splitMethod.name})",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textSecondary
                            )
                            Text(
                                text = splitSummaryText,
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Configure >",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. Category & Payment Mode Grid Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = FairShareTheme.colors.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, FairShareTheme.colors.border),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showCategoryDialog = true }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Category",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textTertiary
                            )
                            Text(
                                text = category.name.lowercase().replaceFirstChar { it.titlecase() },
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = FairShareTheme.colors.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, FairShareTheme.colors.border),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showPaymentModeDialog = true }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Payment Mode",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textTertiary
                            )
                            Text(
                                text = when (paymentMode) {
                                    PaymentMode.UPI -> "UPI"
                                    PaymentMode.ONLINE -> "Online"
                                    PaymentMode.CARD -> "Card"
                                    PaymentMode.CASH -> "Cash"
                                    PaymentMode.WALLET -> "Wallet"
                                    PaymentMode.OTHER -> "Other"
                                },
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 6. Date & Time Row
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FairShareTheme.colors.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, FairShareTheme.colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Date",
                                tint = FairShareTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Date & Time",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textTertiary
                                )
                                Text(
                                    text = formattedDateStr,
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Text(
                            text = "Now",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.accent,
                            modifier = Modifier.clickable { timestamp = System.currentTimeMillis() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 7. Notes (Optional)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Notes (optional, e.g. Bill #1234, tax details)", color = FairShareTheme.colors.disabled) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 8. Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FSButton(
                        text = "Review",
                        onClick = { showReviewDialog = true },
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                        enabled = isFormValid
                    )
                    FSButton(
                        text = if (isEditMode) "Save Changes" else "Save Expense",
                        onClick = {
                            onSaveExpense(expenseToSave)
                            onDismiss()
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f),
                        enabled = isFormValid
                    )
                }
            }
        }
    }

    // Receipt Scan Flow Modal (Camera / Gallery / OCR Review)
    if (showReceiptScanModal) {
        Dialog(
            onDismissRequest = {
                showReceiptScanModal = false
                parsedReceiptForReview = null
                receiptOcrError = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = FairShareTheme.shapes.dialog,
                color = FairShareTheme.colors.surfaceElevated,
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 540.dp)
                    .imePadding()
                    .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scan Physical Receipt",
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.textPrimary
                        )
                        IconButton(onClick = {
                            showReceiptScanModal = false
                            parsedReceiptForReview = null
                        }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = FairShareTheme.colors.textSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (receiptOcrInProgress) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = FairShareTheme.colors.accent)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Analyzing receipt with OCR...", style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary)
                            }
                        }
                    } else if (parsedReceiptForReview != null) {
                        val parsed = parsedReceiptForReview!!
                        var editableMerchant by remember { mutableStateOf(parsed.merchant ?: "") }
                        var editableTotalText by remember {
                            mutableStateOf(
                                if (parsed.totalAmount.amountMinor > 0L)
                                    (parsed.totalAmount.amountMinor / 100.0).toString()
                                else ""
                            )
                        }
                        var editableTaxText by remember {
                            mutableStateOf(
                                parsed.taxAmount?.let { (it.amountMinor / 100.0).toString() } ?: ""
                            )
                        }

                        FSCard(modifier = Modifier.fillMaxWidth()) {
                            Text("Extracted Receipt Information", style = FairShareTheme.typography.section, color = FairShareTheme.colors.accent, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = editableMerchant,
                                onValueChange = { editableMerchant = it },
                                label = { Text("Merchant / Description") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FairShareTheme.colors.accent,
                                    unfocusedBorderColor = FairShareTheme.colors.border,
                                    focusedTextColor = FairShareTheme.colors.textPrimary,
                                    unfocusedTextColor = FairShareTheme.colors.textPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = editableTotalText,
                                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) editableTotalText = it },
                                label = { Text("Final Total (${parsed.currency.symbol})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FairShareTheme.colors.accent,
                                    unfocusedBorderColor = FairShareTheme.colors.border,
                                    focusedTextColor = FairShareTheme.colors.textPrimary,
                                    unfocusedTextColor = FairShareTheme.colors.textPrimary
                                )
                            )

                            if (parsed.invoiceId != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Invoice / Bill #: ${parsed.invoiceId}", style = FairShareTheme.typography.metadata, color = FairShareTheme.colors.textSecondary)
                            }

                            if (parsed.items.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Line Items (${parsed.items.size}):", style = FairShareTheme.typography.metadata, color = FairShareTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                                parsed.items.take(4).forEach { itm ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("• ${itm.name}", style = FairShareTheme.typography.metadata, color = FairShareTheme.colors.textPrimary)
                                        Text(Money(itm.amountMinor, parsed.currency).formatted(), style = FairShareTheme.typography.metadata, color = FairShareTheme.colors.textSecondary)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        FSButton(
                            text = "Apply Extracted Details",
                            onClick = {
                                if (editableMerchant.isNotBlank()) {
                                    description = editableMerchant.trim()
                                }
                                if (editableTotalText.isNotBlank()) {
                                    amountText = editableTotalText.trim()
                                }
                                if (parsed.dateEpochMillis != null && parsed.dateEpochMillis > 0L) {
                                    timestamp = parsed.dateEpochMillis
                                }
                                if (parsed.items.isNotEmpty()) {
                                    items = parsed.items.map {
                                        ExpenseItem(
                                            id = it.id,
                                            name = it.name,
                                            amount = Money(it.amountMinor, parsed.currency),
                                            participantMemberIds = members.map { m -> m.id }
                                        )
                                    }
                                    splitMethod = SplitMethod.ITEMIZED
                                }
                                val invoiceNote = parsed.invoiceId?.let { "Bill #$it" }
                                val taxNote = parsed.taxAmount?.let { "Tax: ${it.formatted()}" }
                                val combinedNotes = listOfNotNull(invoiceNote, taxNote).joinToString(" | ")
                                if (combinedNotes.isNotBlank()) {
                                    notes = if (notes.isBlank()) combinedNotes else "$notes | $combinedNotes"
                                }
                                showReceiptScanModal = false
                                parsedReceiptForReview = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            variant = FSButtonVariant.Primary
                        )
                    } else {
                        Text(
                            text = "Capture a receipt using your camera or choose a photo from the gallery. On-device OCR extracts merchant, invoice ID, totals, tax, and line items.",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textSecondary
                        )

                        if (cameraPermissionDenied) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "⚠ Camera permission was denied. Please allow camera access or pick a receipt image from the gallery.",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.negative
                            )
                        }

                        if (receiptOcrError != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "⚠ $receiptOcrError",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.negative
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        FSButton(
                            text = "Capture with Camera",
                            onClick = { launchCamera() },
                            modifier = Modifier.fillMaxWidth(),
                            variant = FSButtonVariant.Primary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        FSButton(
                            text = "Select from Gallery",
                            onClick = { photoPickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            variant = FSButtonVariant.Secondary
                        )
                    }
                }
            }
        }
    }

    // Sub-dialogs
    if (showPayerDialog) {
        PayerSelectionDialog(
            totalAmount = currentTotalMoney,
            members = members,
            currentPayers = effectivePayers,
            onDismiss = { showPayerDialog = false },
            onConfirmPayers = { updatedPayers ->
                payers = updatedPayers
                showPayerDialog = false
            }
        )
    }

    if (showSplitDialog) {
        SplitConfigDialog(
            totalAmount = currentTotalMoney,
            members = members,
            initialSplitMethod = splitMethod,
            initialParticipants = participants,
            initialItems = items,
            onDismiss = { showSplitDialog = false },
            onConfirmSplit = { method, updatedParticipants, updatedItems ->
                splitMethod = method
                participants = updatedParticipants
                items = updatedItems
                showSplitDialog = false
            }
        )
    }

    if (showCategoryDialog) {
        CategorySelectionDialog(
            selectedCategory = category,
            onCategorySelected = { category = it },
            onDismiss = { showCategoryDialog = false }
        )
    }

    if (showPaymentModeDialog) {
        PaymentModeSelectionDialog(
            selectedMode = paymentMode,
            onModeSelected = { paymentMode = it },
            onDismiss = { showPaymentModeDialog = false }
        )
    }

    if (showReviewDialog) {
        ExpenseReviewDialog(
            expense = expenseToSave,
            members = members,
            onConfirm = {
                showReviewDialog = false
                onSaveExpense(expenseToSave)
                onDismiss()
            },
            onDismiss = { showReviewDialog = false }
        )
    }
}
