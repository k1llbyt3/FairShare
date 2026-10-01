package com.fairshare.android.feature.groups

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.model.GroupType
import com.fairshare.android.core.network.client.NetworkResult
import com.fairshare.android.core.network.repository.BackendGroupRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class NonAccountMemberDraft(
    val name: String,
    val phone: String = ""
)

private val COMMON_TIMEZONES = listOf(
    "Asia/Kolkata" to "India (IST, UTC+5:30)",
    "Asia/Dubai" to "Dubai / UAE (GST, UTC+4)",
    "Asia/Singapore" to "Singapore (SGT, UTC+8)",
    "Asia/Bangkok" to "Thailand (ICT, UTC+7)",
    "Europe/London" to "London (GMT/BST, UTC+0)",
    "Europe/Paris" to "Central Europe (CET, UTC+1)",
    "America/New_York" to "US Eastern (EST/EDT, UTC-5)",
    "America/Los_Angeles" to "US Pacific (PST/PDT, UTC-8)",
    "UTC" to "Universal Time (UTC)"
)

private val COMMON_CURRENCIES = listOf(
    Triple("INR", "₹", "Indian Rupee"),
    Triple("USD", "$", "US Dollar"),
    Triple("EUR", "€", "Euro"),
    Triple("GBP", "£", "British Pound"),
    Triple("AED", "د.إ", "UAE Dirham"),
    Triple("SGD", "S$", "Singapore Dollar"),
    Triple("THB", "฿", "Thai Baht")
)

private fun parseDateToMillis(dateStr: String): Long? {
    if (dateStr.isBlank()) return null
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(dateStr.trim())?.time
    } catch (_: Exception) {
        dateStr.toLongOrNull()
    }
}

/**
 * Dedicated Full-Screen Create Group Flow.
 */
@Composable
fun CreateGroupScreen(
    repository: BackendGroupRepository,
    onBack: () -> Unit,
    onGroupCreated: (GroupEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(1) }

    // Step 1 State: Basic Info & Group Type & Personal Group Members
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var groupType by remember { mutableStateOf(GroupType.TRIP) }
    var customGroupType by remember { mutableStateOf("") }
    val manualParticipants = remember { mutableStateListOf<NonAccountMemberDraft>() }
    var newMemberName by remember { mutableStateOf("") }
    var newMemberPhone by remember { mutableStateOf("") }

    // Step 2 State: Schedule & Timezone
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var meetingTime by remember { mutableStateOf("") }
    val defaultTz = remember { TimeZone.getDefault().id.ifBlank { "Asia/Kolkata" } }
    var timezone by remember { mutableStateOf(defaultTz) }
    var showTzDialog by remember { mutableStateOf(false) }

    // Step 3 State: Currency & Defaults
    var currency by remember { mutableStateOf("INR") }
    var budgetTarget by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = FairShareTheme.colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Screen Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (step > 1) {
                            step--
                            errorMessage = null
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = FairShareTheme.colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Create Group",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Text(
                        text = "Step $step of 4",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 1..4) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (i <= step) FairShareTheme.colors.accent
                                else FairShareTheme.colors.surface
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (step) {
                1 -> {
                    // Step 1: Basic Info & Mode
                    Text(
                        text = "Group Details",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set up your group identity and choose how members join.",
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            errorMessage = null
                        },
                        label = { Text("Group Name *", color = FairShareTheme.colors.textSecondary) },
                        placeholder = { Text("e.g. Goa Trip 2026, Flat 402", color = FairShareTheme.colors.disabled) },
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

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (Optional)", color = FairShareTheme.colors.textSecondary) },
                        placeholder = { Text("e.g. Shared trip expenses & groceries", color = FairShareTheme.colors.disabled) },
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

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "GROUP TYPE *",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Group Type Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                GroupType.TRIP to "Trip",
                                GroupType.HOUSEHOLD to "Home",
                                GroupType.EVENT to "Event"
                            ).forEach { (type, label) ->
                                val isSelected = groupType == type
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(FairShareTheme.shapes.button)
                                        .background(if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface)
                                        .border(
                                            1.dp,
                                            if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border,
                                            FairShareTheme.shapes.button
                                        )
                                        .clickable {
                                            groupType = type
                                            customGroupType = ""
                                            errorMessage = null
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = FairShareTheme.typography.supporting,
                                        color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            if (groupType != GroupType.OTHER) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(FairShareTheme.shapes.button)
                                        .background(FairShareTheme.colors.surface)
                                        .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.button)
                                        .clickable {
                                            groupType = GroupType.OTHER
                                            customGroupType = ""
                                            errorMessage = null
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Other",
                                        style = FairShareTheme.typography.supporting,
                                        color = FairShareTheme.colors.textPrimary
                                    )
                                }
                            }
                        }

                        if (groupType == GroupType.OTHER) {
                            OutlinedTextField(
                                value = customGroupType,
                                onValueChange = {
                                    customGroupType = it
                                    errorMessage = null
                                },
                                label = { Text("Specify group type *", color = FairShareTheme.colors.textSecondary) },
                                placeholder = { Text("Specify group type *", color = FairShareTheme.colors.disabled) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FairShareTheme.colors.accent,
                                    unfocusedBorderColor = FairShareTheme.colors.accent,
                                    focusedTextColor = FairShareTheme.colors.textPrimary,
                                    unfocusedTextColor = FairShareTheme.colors.textPrimary,
                                    focusedContainerColor = FairShareTheme.colors.surface,
                                    unfocusedContainerColor = FairShareTheme.colors.surface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Direct manual participants without requiring accounts (supported for ANY group type)
                    Spacer(modifier = Modifier.height(16.dp))
                    FSCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = FairShareTheme.colors.surface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Add People Without App (Optional)",
                                style = FairShareTheme.typography.section,
                                color = FairShareTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add participants directly by name. They do not need a FairShare account to be included in expense calculations, splits, and settlements.",
                                style = FairShareTheme.typography.supporting,
                                color = FairShareTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newMemberName,
                                    onValueChange = { newMemberName = it },
                                    label = { Text("Participant Name", color = FairShareTheme.colors.textSecondary) },
                                    placeholder = { Text("e.g. Rahul, Priya", color = FairShareTheme.colors.disabled) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = FairShareTheme.colors.accent,
                                        unfocusedBorderColor = FairShareTheme.colors.border,
                                        focusedTextColor = FairShareTheme.colors.textPrimary,
                                        unfocusedTextColor = FairShareTheme.colors.textPrimary,
                                        focusedContainerColor = FairShareTheme.colors.background,
                                        unfocusedContainerColor = FairShareTheme.colors.background
                                    )
                                )
                                IconButton(
                                    onClick = {
                                        if (newMemberName.isNotBlank()) {
                                            manualParticipants.add(
                                                NonAccountMemberDraft(
                                                    name = newMemberName.trim(),
                                                    phone = newMemberPhone.trim()
                                                )
                                            )
                                            newMemberName = ""
                                            newMemberPhone = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(FairShareTheme.colors.accent)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Participant",
                                        tint = FairShareTheme.colors.surfaceElevated
                                    )
                                }
                            }

                            if (manualParticipants.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Added (${manualParticipants.size}):",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                manualParticipants.forEachIndexed { index, participant ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = FairShareTheme.colors.accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = participant.name,
                                                style = FairShareTheme.typography.body,
                                                color = FairShareTheme.colors.textPrimary
                                            )
                                        }
                                        IconButton(
                                            onClick = { manualParticipants.removeAt(index) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove",
                                                tint = FairShareTheme.colors.negative,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Step 2: Schedule & Timezone
                    Text(
                        text = "Schedule & Timezone",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Define the group timeframe and operating timezone.",
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Start Date Picker Field
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = FairShareTheme.colors.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FairShareTheme.colors.border),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val cal = Calendar.getInstance()
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            startDate = String.format(Locale.ROOT, "%04d-%02d-%02d", y, m + 1, d)
                                            errorMessage = null
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 14.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Start Date *",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    Text(
                                        text = startDate.ifBlank { "Select date" },
                                        style = FairShareTheme.typography.body,
                                        color = if (startDate.isNotBlank()) FairShareTheme.colors.textPrimary else FairShareTheme.colors.disabled
                                    )
                                }
                                if (startDate.isNotBlank()) {
                                    IconButton(
                                        onClick = { startDate = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = FairShareTheme.colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = FairShareTheme.colors.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // End Date Picker Field
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = FairShareTheme.colors.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FairShareTheme.colors.border),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val cal = Calendar.getInstance()
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            endDate = String.format(Locale.ROOT, "%04d-%02d-%02d", y, m + 1, d)
                                            errorMessage = null
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 14.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "End Date *",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    Text(
                                        text = endDate.ifBlank { "Select date" },
                                        style = FairShareTheme.typography.body,
                                        color = if (endDate.isNotBlank()) FairShareTheme.colors.textPrimary else FairShareTheme.colors.disabled
                                    )
                                }
                                if (endDate.isNotBlank()) {
                                    IconButton(
                                        onClick = { endDate = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = FairShareTheme.colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = FairShareTheme.colors.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Meeting / Settlement Time Picker Field
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = FairShareTheme.colors.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, FairShareTheme.colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cal = Calendar.getInstance()
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        meetingTime = String.format(Locale.ROOT, "%02d:%02d", hourOfDay, minute)
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    true
                                ).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Daily Settlement Time (Optional)",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                Text(
                                    text = meetingTime.ifBlank { "Select preferred time (e.g. 21:00)" },
                                    style = FairShareTheme.typography.body,
                                    color = if (meetingTime.isNotBlank()) FairShareTheme.colors.textPrimary else FairShareTheme.colors.disabled
                                )
                            }
                            if (meetingTime.isNotBlank()) {
                                IconButton(
                                    onClick = { meetingTime = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = FairShareTheme.colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = FairShareTheme.colors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "GROUP TIMEZONE *",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = FairShareTheme.colors.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, FairShareTheme.colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTzDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val tzDisplay = COMMON_TIMEZONES.find { it.first == timezone }?.second ?: timezone
                                Text(
                                    text = tzDisplay,
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary
                                )
                                Text(
                                    text = "Identifier: $timezone",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Change Timezone",
                                tint = FairShareTheme.colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                3 -> {
                    // Step 3: Currency & Defaults
                    Text(
                        text = "Currency & Budget Target",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set primary operating currency for calculations.",
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "PRIMARY CURRENCY *",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        COMMON_CURRENCIES.forEach { (code, symbol, label) ->
                            val isSelected = currency == code
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(FairShareTheme.shapes.card)
                                    .background(if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface)
                                    .border(
                                        1.dp,
                                        if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border,
                                        FairShareTheme.shapes.card
                                    )
                                    .clickable { currency = code }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = symbol,
                                            style = FairShareTheme.typography.title,
                                            color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textSecondary,
                                            modifier = Modifier.width(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = label,
                                            style = FairShareTheme.typography.body,
                                            color = FairShareTheme.colors.textPrimary
                                        )
                                    }
                                    Text(
                                        text = code,
                                        style = FairShareTheme.typography.metadata,
                                        color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = budgetTarget,
                        onValueChange = { budgetTarget = it },
                        label = { Text("Budget Note (Optional)", color = FairShareTheme.colors.textSecondary) },
                        placeholder = { Text("e.g. ₹50,000 estimated total trip budget", color = FairShareTheme.colors.disabled) },
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
                }

                4 -> {
                    // Step 4: Review & Confirmation
                    Text(
                        text = "Review & Create",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Confirm details before creating the shared ledger.",
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val displayType = if (groupType == GroupType.OTHER && customGroupType.isNotBlank()) {
                        customGroupType.trim()
                    } else when (groupType) {
                        GroupType.TRIP -> "Trip"
                        GroupType.HOUSEHOLD -> "Home"
                        GroupType.EVENT -> "Event"
                        else -> "Other"
                    }

                    FSCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = FairShareTheme.colors.surface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name.ifBlank { "Untitled Group" },
                                    style = FairShareTheme.typography.section,
                                    color = FairShareTheme.colors.textPrimary
                                )
                                FSStatusBadge(text = displayType)
                            }
                            if (description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = description,
                                    style = FairShareTheme.typography.supporting,
                                    color = FairShareTheme.colors.textSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = FairShareTheme.colors.border)
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Currency",
                                    style = FairShareTheme.typography.supporting,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                Text(
                                    text = currency,
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Timezone",
                                    style = FairShareTheme.typography.supporting,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                Text(
                                    text = timezone,
                                    style = FairShareTheme.typography.supporting,
                                    color = FairShareTheme.colors.textPrimary
                                )
                            }

                            if (startDate.isNotBlank() || endDate.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Duration",
                                        style = FairShareTheme.typography.supporting,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    Text(
                                        text = "${startDate.ifBlank { "Start" }} → ${endDate.ifBlank { "Ongoing" }}",
                                        style = FairShareTheme.typography.supporting,
                                        color = FairShareTheme.colors.textPrimary
                                    )
                                }
                            }

                            if (manualParticipants.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Non-account members",
                                        style = FairShareTheme.typography.supporting,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    Text(
                                        text = "${manualParticipants.size} people",
                                        style = FairShareTheme.typography.supporting,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = errorMessage!!,
                    style = FairShareTheme.typography.supporting,
                    color = FairShareTheme.colors.negative
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Navigation Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step > 1) {
                    FSButton(
                        text = "Back",
                        onClick = {
                            step--
                            errorMessage = null
                        },
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    )
                } else {
                    FSButton(
                        text = "Cancel",
                        onClick = onBack,
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    )
                }

                if (step < 4) {
                    val isStep1Valid = name.isNotBlank() && (groupType != GroupType.OTHER || customGroupType.isNotBlank())
                    // Dates optional for non-trip groups; if provided must be valid
                    val isStep2Valid = (startDate.isBlank() && endDate.isBlank()) ||
                        (startDate.isNotBlank() && endDate.isNotBlank() && startDate <= endDate)
                    val isCurrentStepValid = when (step) {
                        1 -> isStep1Valid
                        2 -> isStep2Valid
                        else -> true
                    }

                    FSButton(
                        text = "Next",
                        onClick = {
                            if (step == 1 && name.isBlank()) {
                                errorMessage = "Please enter a group name"
                            } else if (step == 1 && groupType == GroupType.OTHER && customGroupType.isBlank()) {
                                errorMessage = "Please specify custom group type"
                            } else if (step == 2 && startDate.isNotBlank() && endDate.isBlank()) {
                                errorMessage = "If start date is set, end date is also required"
                            } else if (step == 2 && endDate.isNotBlank() && startDate.isBlank()) {
                                errorMessage = "If end date is set, start date is also required"
                            } else if (step == 2 && startDate.isNotBlank() && endDate.isNotBlank() && startDate > endDate) {
                                errorMessage = "End date cannot be earlier than start date"
                            } else {
                                errorMessage = null
                                step++
                            }
                        },
                        enabled = isCurrentStepValid && !isLoading,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    FSButton(
                        text = if (isLoading) "Creating..." else "Create Group",
                        onClick = {
                            val effectiveType = if (groupType == GroupType.OTHER) {
                                val trimmed = customGroupType.trim()
                                if (trimmed.isBlank()) {
                                    errorMessage = "Specify group type *"
                                    return@FSButton
                                }
                                trimmed
                            } else {
                                groupType.name
                            }
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                when (val res = repository.createGroup(
                                    name = name.trim(),
                                    description = description.trim(),
                                    groupType = effectiveType,
                                    currency = currency,
                                    startDate = parseDateToMillis(startDate),
                                    endDate = parseDateToMillis(endDate),
                                    timezone = timezone
                                )) {
                                    is NetworkResult.Success -> {
                                        val created = res.data
                                        if (manualParticipants.isNotEmpty()) {
                                            manualParticipants.forEach { p ->
                                                repository.addNonAccountMember(
                                                    groupId = created.id,
                                                    name = p.name,
                                                    phoneNumber = p.phone.ifBlank { null }
                                                )
                                            }
                                        }
                                        isLoading = false
                                        onGroupCreated(created)
                                    }
                                    is NetworkResult.Error -> {
                                        isLoading = false
                                        errorMessage = res.message
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showTzDialog) {
        TimezonePickerDialog(
            selectedTimezone = timezone,
            onSelect = { tz ->
                timezone = tz
                showTzDialog = false
            },
            onDismiss = { showTzDialog = false }
        )
    }
}

/**
 * Retained for backwards compatibility dialog invocation,
 * wrapping the full-screen CreateGroupScreen or displaying inside dialog surface.
 */
@Composable
fun CreateGroupDialog(
    repository: BackendGroupRepository,
    onDismiss: () -> Unit,
    onGroupCreated: (GroupEntity) -> Unit
) {
    CreateGroupScreen(
        repository = repository,
        onBack = onDismiss,
        onGroupCreated = onGroupCreated
    )
}

@Composable
fun TimezonePickerDialog(
    selectedTimezone: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allTzs = remember {
        val available = TimeZone.getAvailableIDs().sorted()
        available
    }
    val filtered = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            COMMON_TIMEZONES.map { it.first }
        } else {
            allTzs.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Timezone",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = FairShareTheme.colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search timezone (e.g. Asia, London, EST)...", color = FairShareTheme.colors.disabled) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = FairShareTheme.colors.textSecondary
                        )
                    },
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

                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(filtered) { tzId ->
                        val commonLabel = COMMON_TIMEZONES.find { it.first == tzId }?.second
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelect(tzId) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = commonLabel ?: tzId,
                                    style = FairShareTheme.typography.body,
                                    color = if (tzId == selectedTimezone) FairShareTheme.colors.accent else FairShareTheme.colors.textPrimary,
                                    fontWeight = if (tzId == selectedTimezone) FontWeight.Bold else FontWeight.Normal
                                )
                                if (commonLabel != null) {
                                    Text(
                                        text = tzId,
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                }
                            }
                            if (tzId == selectedTimezone) {
                                Text(
                                    text = "Selected",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        HorizontalDivider(color = FairShareTheme.colors.border.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
