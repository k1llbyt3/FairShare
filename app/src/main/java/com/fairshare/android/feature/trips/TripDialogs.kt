package com.fairshare.android.feature.trips

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.TripSegmentEntity
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FairShareTheme

@Composable
fun CreateTripDialog(
    groups: List<GroupEntity>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, startDateEpochMs: Long, endDateEpochMs: Long, selectedGroupId: String?, notes: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var durationDaysStr by remember { mutableStateOf("3") }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "New Trip Plan",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Trip Name", style = FairShareTheme.typography.metadata) },
                    placeholder = { Text("e.g., Manali Road Trip", style = FairShareTheme.typography.body) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary,
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedLabelColor = FairShareTheme.colors.accent,
                        unfocusedLabelColor = FairShareTheme.colors.textSecondary,
                        cursorColor = FairShareTheme.colors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = durationDaysStr,
                    onValueChange = { durationDaysStr = it },
                    label = { Text("Duration (Days)", style = FairShareTheme.typography.metadata) },
                    placeholder = { Text("3", style = FairShareTheme.typography.body) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary,
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedLabelColor = FairShareTheme.colors.accent,
                        unfocusedLabelColor = FairShareTheme.colors.textSecondary,
                        cursorColor = FairShareTheme.colors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Link to Existing FairShare Group (Optional)",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (groups.isEmpty()) {
                    Text(
                        text = "No FairShare groups yet. You can still plan your trip independently.",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textTertiary
                    )
                } else {
                    groups.forEach { g ->
                        val isSelected = selectedGroupId == g.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(
                                    if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    selectedGroupId = if (isSelected) null else g.id
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = g.name,
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Text(
                                    text = "Linked",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Trip Notes (Optional)", style = FairShareTheme.typography.metadata) },
                    placeholder = { Text("e.g., Hotel bookings, packing list notes", style = FairShareTheme.typography.body) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary,
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedLabelColor = FairShareTheme.colors.accent,
                        unfocusedLabelColor = FairShareTheme.colors.textSecondary,
                        cursorColor = FairShareTheme.colors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.negative
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FSButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    FSButton(
                        text = "Create Trip",
                        onClick = {
                            if (name.trim().isBlank()) {
                                errorMessage = "Please enter a trip name."
                                return@FSButton
                            }
                            val days = durationDaysStr.toIntOrNull()?.coerceAtLeast(1) ?: 1
                            val startEpoch = System.currentTimeMillis()
                            val endEpoch = startEpoch + (days * 86_400_000L) - 1
                            onConfirm(name.trim(), startEpoch, endEpoch, selectedGroupId, notes.trim())
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun AddItineraryItemDialog(
    dayIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (title: String, destination: String, notes: String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Add Itinerary Item (Day $dayIndex)",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        error = null
                    },
                    label = { Text("Activity / Checkpoint Title", style = FairShareTheme.typography.metadata) },
                    placeholder = { Text("e.g., Morning Trek to Solang Valley", style = FairShareTheme.typography.body) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary,
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedLabelColor = FairShareTheme.colors.accent,
                        unfocusedLabelColor = FairShareTheme.colors.textSecondary,
                        cursorColor = FairShareTheme.colors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2-Option Destination Chooser
                Text(
                    text = "DESTINATION INPUT METHOD",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                var isMapsOption by remember { mutableStateOf(false) }
                var showMapPicker by remember { mutableStateOf(false) }

                if (showMapPicker) {
                    TripMapPickerDialog(
                        onDismiss = { showMapPicker = false },
                        onLocationSelected = { loc ->
                            destination = "${loc.name}, ${loc.address}"
                            showMapPicker = false
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isMapsOption) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface)
                            .border(1.dp, if (!isMapsOption) FairShareTheme.colors.accent else FairShareTheme.colors.border, RoundedCornerShape(8.dp))
                            .clickable { isMapsOption = false }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Option A: Manual Text",
                            style = FairShareTheme.typography.metadata,
                            color = if (!isMapsOption) FairShareTheme.colors.accent else FairShareTheme.colors.textPrimary,
                            fontWeight = if (!isMapsOption) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isMapsOption) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface)
                            .border(1.dp, if (isMapsOption) FairShareTheme.colors.accent else FairShareTheme.colors.border, RoundedCornerShape(8.dp))
                            .clickable { isMapsOption = true }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Option B: Select Map",
                            style = FairShareTheme.typography.metadata,
                            color = if (isMapsOption) FairShareTheme.colors.accent else FairShareTheme.colors.textPrimary,
                            fontWeight = if (isMapsOption) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = {
                        Text(
                            if (isMapsOption) "Location / Landmark Name *" else "Destination Name (Manual)",
                            style = FairShareTheme.typography.metadata
                        )
                    },
                    placeholder = {
                        Text(
                            if (isMapsOption) "e.g., Mysore Palace, Mysore" else "e.g., Grandma's House, Hilltop Campsite",
                            style = FairShareTheme.typography.body
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary,
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedLabelColor = FairShareTheme.colors.accent,
                        unfocusedLabelColor = FairShareTheme.colors.textSecondary,
                        cursorColor = FairShareTheme.colors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FSButton(
                        text = "Choose on Map",
                        onClick = { showMapPicker = true },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )

                    if (destination.isNotBlank()) {
                        FSButton(
                            text = "View in Maps App",
                            onClick = {
                                try {
                                    val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(destination.trim())}")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    try {
                                        context.startActivity(mapIntent)
                                    } catch (_: Exception) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open map: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            variant = FSButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Timings", style = FairShareTheme.typography.metadata) },
                    placeholder = { Text("e.g., Reach before 9 AM, carry jackets", style = FairShareTheme.typography.body) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary,
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedLabelColor = FairShareTheme.colors.accent,
                        unfocusedLabelColor = FairShareTheme.colors.textSecondary,
                        cursorColor = FairShareTheme.colors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.negative
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FSButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    FSButton(
                        text = "Add Item",
                        onClick = {
                            if (title.trim().isBlank()) {
                                error = "Title cannot be blank."
                                return@FSButton
                            }
                            onConfirm(title.trim(), destination.trim(), notes.trim())
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun AddSegmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (origin: String, destination: String, travelMode: String, plannedDistanceKm: Double, durationMinutes: Long) -> Unit
) {
    val context = LocalContext.current
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var travelMode by remember { mutableStateOf("DRIVE") }
    var plannedDistanceStr by remember { mutableStateOf("") }
    var durationMinutesStr by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val modes = listOf("DRIVE", "TRAIN", "FLIGHT", "BUS", "WALK", "OTHER")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Add Travel Segment",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = origin,
                    onValueChange = {
                        origin = it
                        error = null
                    },
                    label = { Text("Origin", style = FairShareTheme.typography.metadata) },
                    placeholder = { Text("e.g., Delhi", style = FairShareTheme.typography.body) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary,
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedLabelColor = FairShareTheme.colors.accent,
                        unfocusedLabelColor = FairShareTheme.colors.textSecondary,
                        cursorColor = FairShareTheme.colors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = destination,
                    onValueChange = {
                        destination = it
                        error = null
                    },
                    label = { Text("Destination", style = FairShareTheme.typography.metadata) },
                    placeholder = { Text("e.g., Manali", style = FairShareTheme.typography.body) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary,
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedLabelColor = FairShareTheme.colors.accent,
                        unfocusedLabelColor = FairShareTheme.colors.textSecondary,
                        cursorColor = FairShareTheme.colors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (origin.isNotBlank() && destination.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "🗺️ Preview route in Google Maps",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.accent,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                try {
                                    val modeParam = when (travelMode.uppercase()) {
                                        "WALK" -> "walking"
                                        "TRAIN", "BUS" -> "transit"
                                        "FLIGHT" -> "transit"
                                        else -> "driving"
                                    }
                                    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(origin.trim())}&destination=${Uri.encode(destination.trim())}&travelmode=$modeParam")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open map: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Travel Mode",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modes.take(3).forEach { mode ->
                        val isSelected = travelMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { travelMode = mode }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode,
                                style = FairShareTheme.typography.metadata,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textPrimary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modes.drop(3).forEach { mode ->
                        val isSelected = travelMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { travelMode = mode }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode,
                                style = FairShareTheme.typography.metadata,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = plannedDistanceStr,
                    onValueChange = { plannedDistanceStr = it },
                    label = { Text("Planned Distance (km)", style = FairShareTheme.typography.metadata) },
                    placeholder = { Text("e.g., 540", style = FairShareTheme.typography.body) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary,
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedLabelColor = FairShareTheme.colors.accent,
                        unfocusedLabelColor = FairShareTheme.colors.textSecondary,
                        cursorColor = FairShareTheme.colors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = durationMinutesStr,
                    onValueChange = { durationMinutesStr = it },
                    label = { Text("Estimated Duration (Minutes)", style = FairShareTheme.typography.metadata) },
                    placeholder = { Text("e.g., 480", style = FairShareTheme.typography.body) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary,
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedLabelColor = FairShareTheme.colors.accent,
                        unfocusedLabelColor = FairShareTheme.colors.textSecondary,
                        cursorColor = FairShareTheme.colors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.negative
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FSButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    FSButton(
                        text = "Save Segment",
                        onClick = {
                            if (origin.trim().isBlank() || destination.trim().isBlank()) {
                                error = "Please provide both origin and destination."
                                return@FSButton
                            }
                            val dist = plannedDistanceStr.toDoubleOrNull() ?: 0.0
                            val dur = durationMinutesStr.toLongOrNull() ?: 0L
                            onConfirm(origin.trim(), destination.trim(), travelMode, dist, dur)
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun UpdateSegmentDistanceDialog(
    segment: TripSegmentEntity,
    onDismiss: () -> Unit,
    onConfirm: (coveredDistanceKm: Double) -> Unit
) {
    var coveredStr by remember { mutableStateOf(segment.coveredDistanceKm.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Update Covered Distance",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${segment.origin} → ${segment.destination} (Planned: ${segment.plannedDistanceKm} km)",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = coveredStr,
                    onValueChange = { coveredStr = it },
                    label = { Text("Actual Covered Distance (km)", style = FairShareTheme.typography.metadata) },
                    placeholder = { Text("0.0", style = FairShareTheme.typography.body) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary,
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedLabelColor = FairShareTheme.colors.accent,
                        unfocusedLabelColor = FairShareTheme.colors.textSecondary,
                        cursorColor = FairShareTheme.colors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FSButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    FSButton(
                        text = "Save Distance",
                        onClick = {
                            val dist = coveredStr.toDoubleOrNull() ?: segment.coveredDistanceKm
                            onConfirm(dist)
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
