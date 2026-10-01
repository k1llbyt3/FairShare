package com.fairshare.android.feature.receipt

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
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
import com.fairshare.android.core.domain.receipt.ParsedReceipt
import com.fairshare.android.core.domain.receipt.ReceiptLineItem
import com.fairshare.android.core.domain.receipt.ReceiptOcrEngine
import kotlinx.coroutines.launch
import java.util.UUID

enum class ReceiptScanStage {
    CHOOSE_SOURCE,
    PROCESSING,
    REVIEW,
    ERROR
}

@Composable
fun ReceiptScannerScreen(
    members: List<Member>,
    ocrEngine: ReceiptOcrEngine,
    onBackClick: () -> Unit,
    onConfirmExpense: (Expense) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf(ReceiptScanStage.CHOOSE_SOURCE) }
    var errorMessage by remember { mutableStateOf("") }
    var parsedReceipt by remember { mutableStateOf<ParsedReceipt?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }

    // Editable review fields
    var merchantName by remember { mutableStateOf("") }
    var totalAmountInput by remember { mutableStateOf("") }
    var selectedPayerId by remember { mutableStateOf(members.firstOrNull { it.isCurrentUser }?.id ?: members.firstOrNull()?.id ?: "") }
    var selectedCategory by remember { mutableStateOf(ExpenseCategory.FOOD) }
    val editableItems = remember { mutableStateListOf<ReceiptLineItem>() }

    // Gallery Picker Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            capturedBitmap = null
            stage = ReceiptScanStage.PROCESSING
            scope.launch {
                val res = ocrEngine.processReceiptImage(context, uri)
                res.fold(
                    onSuccess = { receipt ->
                        parsedReceipt = receipt
                        merchantName = receipt.merchant ?: ""
                        totalAmountInput = if (receipt.totalAmount.amountMinor > 0L) {
                            (receipt.totalAmount.amountMinor / 100.0).toString()
                        } else ""
                        editableItems.clear()
                        editableItems.addAll(receipt.items)
                        stage = ReceiptScanStage.REVIEW
                    },
                    onFailure = { err ->
                        errorMessage = err.message ?: "Failed to process receipt image"
                        stage = ReceiptScanStage.ERROR
                    }
                )
            }
        }
    }

    // Camera Capture Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            capturedBitmap = bitmap
            stage = ReceiptScanStage.PROCESSING
            scope.launch {
                val res = ocrEngine.processReceiptBitmap(bitmap)
                res.fold(
                    onSuccess = { receipt ->
                        parsedReceipt = receipt
                        merchantName = receipt.merchant ?: ""
                        totalAmountInput = if (receipt.totalAmount.amountMinor > 0L) {
                            (receipt.totalAmount.amountMinor / 100.0).toString()
                        } else ""
                        editableItems.clear()
                        editableItems.addAll(receipt.items)
                        stage = ReceiptScanStage.REVIEW
                    },
                    onFailure = { err ->
                        errorMessage = err.message ?: "Failed to process captured camera image"
                        stage = ReceiptScanStage.ERROR
                    }
                )
            }
        }
    }

    // Camera Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            permissionDeniedMessage = null
            cameraLauncher.launch(null)
        } else {
            permissionDeniedMessage = "Camera permission is required to scan physical receipts directly. You can also pick an image from the gallery."
        }
    }

    fun startCameraCapture() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            permissionDeniedMessage = null
            cameraLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
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
                text = "Receipt Scanner",
                style = FairShareTheme.typography.title,
                color = FairShareTheme.colors.textPrimary
            )
        }

        when (stage) {
            ReceiptScanStage.CHOOSE_SOURCE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Scan Receipt",
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Capture a receipt using your device camera or pick a receipt photo from gallery. OCR will extract the merchant, totals, tax, and line items.",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Privacy Notice
                        FSCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Privacy & Local Processing",
                                style = FairShareTheme.typography.section,
                                color = FairShareTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Receipt images are processed on your device. Currency symbols (₹, $, €, £) and tax lines are parsed automatically without uploading your photo.",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textSecondary
                            )
                        }

                        if (permissionDeniedMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = permissionDeniedMessage!!,
                                style = FairShareTheme.typography.supporting,
                                color = FairShareTheme.colors.negative
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Primary Action: Camera Capture
                        FSButton(
                            text = "Scan with Camera",
                            onClick = { startCameraCapture() },
                            modifier = Modifier.fillMaxWidth(),
                            variant = FSButtonVariant.Primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Secondary Action: Gallery Picker
                        FSButton(
                            text = "Choose Photo from Gallery",
                            onClick = { photoPickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            variant = FSButtonVariant.Secondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Direct text paste fallback
                        var sampleTextInput by remember { mutableStateOf("") }
                        var showTextInput by remember { mutableStateOf(false) }

                        if (!showTextInput) {
                            Text(
                                text = "Or paste receipt text directly",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.accent,
                                modifier = Modifier
                                    .clickable { showTextInput = true }
                                    .padding(8.dp)
                            )
                        } else {
                            OutlinedTextField(
                                value = sampleTextInput,
                                onValueChange = { sampleTextInput = it },
                                label = { Text("Paste receipt text") },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 90.dp, max = 160.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FairShareTheme.colors.accent,
                                    unfocusedBorderColor = FairShareTheme.colors.border,
                                    focusedTextColor = FairShareTheme.colors.textPrimary,
                                    unfocusedTextColor = FairShareTheme.colors.textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FSButton(
                                text = "Parse Text",
                                onClick = {
                                    if (sampleTextInput.isNotBlank()) {
                                        stage = ReceiptScanStage.PROCESSING
                                        scope.launch {
                                            val parsed = ocrEngine.processReceiptText(sampleTextInput)
                                            parsedReceipt = parsed
                                            merchantName = parsed.merchant ?: ""
                                            totalAmountInput = if (parsed.totalAmount.amountMinor > 0L) {
                                                (parsed.totalAmount.amountMinor / 100.0).toString()
                                            } else ""
                                            editableItems.clear()
                                            editableItems.addAll(parsed.items)
                                            stage = ReceiptScanStage.REVIEW
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            ReceiptScanStage.PROCESSING -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = FairShareTheme.colors.accent)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Reading receipt…",
                            style = FairShareTheme.typography.section,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Extracting merchant, line items, and totals",
                            style = FairShareTheme.typography.supporting,
                            color = FairShareTheme.colors.textSecondary
                        )
                    }
                }
            }

            ReceiptScanStage.ERROR -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Could not parse receipt",
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.negative
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage.ifBlank { "The receipt image may be unreadable or blurry." },
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        FSButton(
                            text = "Try Camera Again",
                            onClick = {
                                stage = ReceiptScanStage.CHOOSE_SOURCE
                                startCameraCapture()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FSButton(
                            text = "Choose from Gallery",
                            onClick = {
                                stage = ReceiptScanStage.CHOOSE_SOURCE
                                photoPickerLauncher.launch("image/*")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            variant = FSButtonVariant.Secondary
                        )
                    }
                }
            }

            ReceiptScanStage.REVIEW -> {
                val receipt = parsedReceipt
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    item {
                        Text(
                            text = "REVIEW EXTRACTED RECEIPT",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.accent,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Preview captured photo if available
                        if (capturedBitmap != null) {
                            Image(
                                bitmap = capturedBitmap!!.asImageBitmap(),
                                contentDescription = "Captured Receipt Preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Merchant field
                        OutlinedTextField(
                            value = merchantName,
                            onValueChange = { merchantName = it },
                            label = { Text("Merchant / Store Name") },
                            placeholder = { Text("e.g. Starbucks, DMart") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FairShareTheme.colors.accent,
                                unfocusedBorderColor = FairShareTheme.colors.border,
                                focusedTextColor = FairShareTheme.colors.textPrimary,
                                unfocusedTextColor = FairShareTheme.colors.textPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Total amount field
                        OutlinedTextField(
                            value = totalAmountInput,
                            onValueChange = { totalAmountInput = it },
                            label = { Text("Total Amount") },
                            placeholder = { Text("0.00") },
                            prefix = {
                                Text(
                                    text = "${receipt?.currency?.symbol ?: "₹"} ",
                                    color = FairShareTheme.colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            },
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
                        Spacer(modifier = Modifier.height(12.dp))

                        // Invoice / Bill ID field
                        if (receipt?.invoiceId != null) {
                            OutlinedTextField(
                                value = receipt.invoiceId,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Receipt / Invoice ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FairShareTheme.colors.border,
                                    unfocusedBorderColor = FairShareTheme.colors.border,
                                    focusedTextColor = FairShareTheme.colors.textPrimary,
                                    unfocusedTextColor = FairShareTheme.colors.textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Date field
                        val dateFormatted = remember(receipt?.dateEpochMillis) {
                            receipt?.dateEpochMillis?.let {
                                java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it))
                            } ?: "Today"
                        }
                        OutlinedTextField(
                            value = dateFormatted,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Receipt Date") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FairShareTheme.colors.border,
                                unfocusedBorderColor = FairShareTheme.colors.border,
                                focusedTextColor = FairShareTheme.colors.textPrimary,
                                unfocusedTextColor = FairShareTheme.colors.textPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Arithmetic Validation Banner
                        if (receipt != null && !receipt.arithmeticValid && receipt.validationNotes.isNotEmpty()) {
                            FSCard(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = FairShareTheme.colors.surfaceElevated
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Needs Review",
                                        style = FairShareTheme.typography.section,
                                        color = FairShareTheme.colors.accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    receipt.validationNotes.forEach { note ->
                                        Text(
                                            text = "• $note",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Breakdown summary
                        if (receipt != null && (receipt.subtotalAmount != null || receipt.taxAmount != null || receipt.serviceChargeAmount != null)) {
                            FSCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Extracted Breakdown",
                                        style = FairShareTheme.typography.section,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    receipt.subtotalAmount?.let {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Subtotal", style = FairShareTheme.typography.supporting, color = FairShareTheme.colors.textSecondary)
                                            Text(it.formatted(), style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary, fontWeight = FontWeight.Medium)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    receipt.taxAmount?.let {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Tax / GST", style = FairShareTheme.typography.supporting, color = FairShareTheme.colors.textSecondary)
                                            Text(it.formatted(), style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary, fontWeight = FontWeight.Medium)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    receipt.serviceChargeAmount?.let {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Service Charge", style = FairShareTheme.typography.supporting, color = FairShareTheme.colors.textSecondary)
                                            Text(it.formatted(), style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Text(
                            text = "Detected Items (${editableItems.size})",
                            style = FairShareTheme.typography.section,
                            color = FairShareTheme.colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(editableItems) { item ->
                        FSCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = FairShareTheme.typography.body,
                                            color = FairShareTheme.colors.textPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Assigned: ${if (item.assignedMemberIds.isEmpty()) "All members" else item.assignedMemberIds.mapNotNull { id -> members.find { it.id == id }?.name }.joinToString()}",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = Money(item.amountMinor, receipt?.currency ?: Currency.INR).formatted(),
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        val isConfirmValid = totalAmountInput.toDoubleOrNull()?.let { it > 0.0 } == true
                        FSButton(
                            text = "Confirm & Save Expense",
                            onClick = {
                                val amountVal = totalAmountInput.toDoubleOrNull() ?: 0.0
                                val amountMinor = (amountVal * 100).toLong()
                                val currency = receipt?.currency ?: Currency.INR
                                val totalMoney = Money(amountMinor, currency)

                                val expenseItems = editableItems.map {
                                    ExpenseItem(
                                        id = it.id,
                                        name = it.name,
                                        amount = Money(it.amountMinor, currency),
                                        participantMemberIds = it.assignedMemberIds.ifEmpty { members.map { m -> m.id } }
                                    )
                                }

                                val splitMethod = if (expenseItems.isNotEmpty()) SplitMethod.ITEMIZED else SplitMethod.EQUAL
                                val notesList = mutableListOf<String>()
                                receipt?.invoiceId?.let { notesList.add("Invoice #$it") }
                                receipt?.taxAmount?.let { notesList.add("Tax: ${it.formatted()}") }

                                val newExpense = Expense(
                                    id = UUID.randomUUID().toString(),
                                    groupId = "",
                                    description = merchantName.ifBlank { "Receipt Expense" },
                                    totalAmount = totalMoney,
                                    payers = listOf(ExpensePayer(memberId = selectedPayerId, amount = totalMoney)),
                                    participants = members.map { ExpenseParticipant(memberId = it.id) },
                                    splitMethod = splitMethod,
                                    items = expenseItems,
                                    mode = PaymentMode.ONLINE,
                                    category = selectedCategory,
                                    timestamp = receipt?.dateEpochMillis ?: System.currentTimeMillis(),
                                    notes = notesList.joinToString(" | ").ifEmpty { null }
                                )

                                onConfirmExpense(newExpense)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isConfirmValid,
                            variant = FSButtonVariant.Primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        FSButton(
                            text = "Retake / Discard",
                            onClick = {
                                capturedBitmap = null
                                stage = ReceiptScanStage.CHOOSE_SOURCE
                            },
                            modifier = Modifier.fillMaxWidth(),
                            variant = FSButtonVariant.Secondary
                        )
                    }
                }
            }
        }
    }
}
