package com.fairshare.android.feature.trips

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.TripItineraryItemEntity
import com.fairshare.android.core.database.entity.TripSegmentEntity
import com.fairshare.android.core.database.repository.TripRepository
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun TripPlannerScreen(
    tripRepository: TripRepository,
    groups: List<GroupEntity>,
    expenses: List<Expense>,
    members: List<Member>,
    payments: List<SettlementPayment>,
    onBackClick: () -> Unit,
    onAddExpense: ((Expense) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allTrips by tripRepository.getAllTripsFlow().collectAsState(initial = emptyList())

    var selectedTripId by remember { mutableStateOf<String?>(null) }

    val activeTrip = remember(allTrips, selectedTripId) {
        if (selectedTripId != null) {
            allTrips.firstOrNull { it.id == selectedTripId }
        } else {
            allTrips.firstOrNull()
        }
    }

    // Dialog states
    var showCreateTripDialog by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf<Int?>(null) } // Day index
    var showAddSegmentDialog by remember { mutableStateOf(false) }
    var segmentToUpdateDistance by remember { mutableStateOf<TripSegmentEntity?>(null) }
    var showRecapDialog by remember { mutableStateOf(false) }

    // GPS tracker
    val tracker = remember { TripTracker(context) }
    val isTracking by tracker.isTracking.collectAsState()
    val trackedDistanceKm by tracker.accumulatedDistanceKm.collectAsState()
    val dailyDistanceKm by tracker.dailyDistanceKm.collectAsState()
    val totalTripDistanceKm by tracker.totalTripDistanceKm.collectAsState()

    // Location permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            if (!tracker.isLocationServiceEnabled()) {
                Toast.makeText(context, "GPS / Location services disabled on device. Enable GPS or enter distance manually.", Toast.LENGTH_LONG).show()
            } else {
                val started = tracker.startTracking()
                if (started) {
                    Toast.makeText(context, "GPS distance tracking started.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not start GPS tracking. Please check device location settings.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Location permission denied. You can record travel distance manually.", Toast.LENGTH_LONG).show()
        }
    }

    val openMapLocation: (String) -> Unit = { destination ->
        if (destination.isNotBlank()) {
            try {
                val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(destination)}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open map: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openMapRoute: (String, String, String) -> Unit = { origin, dest, mode ->
        if (origin.isNotBlank() && dest.isNotBlank()) {
            try {
                val travelModeParam = when (mode.uppercase()) {
                    "WALK" -> "walking"
                    "TRAIN", "BUS" -> "transit"
                    "FLIGHT" -> "transit"
                    else -> "driving"
                }
                val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(origin)}&destination=${Uri.encode(dest)}&travelmode=$travelModeParam")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open directions: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler {
        if (selectedTripId != null && allTrips.size > 1) {
            selectedTripId = null
        } else {
            onBackClick()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(FairShareTheme.colors.background)
    ) {
        val isWide = maxWidth > 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isWide) {
                        Modifier
                            .widthIn(max = 600.dp)
                            .align(Alignment.TopCenter)
                    } else {
                        Modifier
                    }
                )
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FairShareTheme.colors.surface)
                    .border(1.dp, FairShareTheme.colors.border)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FSButton(
                    text = "← Back",
                    onClick = {
                        if (selectedTripId != null && allTrips.size > 1) {
                            selectedTripId = null
                        } else {
                            onBackClick()
                        }
                    },
                    variant = FSButtonVariant.Secondary
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Trip Planner",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    if (activeTrip != null) {
                        Text(
                            text = activeTrip.name,
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.accent
                        )
                    }
                }
                FSButton(
                    text = "+ New Trip",
                    onClick = { showCreateTripDialog = true },
                    variant = FSButtonVariant.Primary
                )
            }

            if (allTrips.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Trips Planned Yet",
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Create a new trip itinerary to organize your schedule by day, track travel segments and distance, and view trip recaps.",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        FSButton(
                            text = "Plan Your First Trip",
                            onClick = { showCreateTripDialog = true },
                            variant = FSButtonVariant.Primary
                        )
                    }
                }
            } else if (activeTrip != null) {
                // Trip Detail & Itinerary View
                val itineraryItems by tripRepository.getItineraryItemsFlow(activeTrip.id).collectAsState(initial = emptyList())
                val segments by tripRepository.getSegmentsFlow(activeTrip.id).collectAsState(initial = emptyList())

                val durationDays = remember(activeTrip.startDate, activeTrip.endDate) {
                    val diff = max(0L, activeTrip.endDate - activeTrip.startDate)
                    (diff / 86_400_000L).toInt() + 1
                }

                val completedCount = itineraryItems.count { it.status == "COMPLETED" }
                val totalItemsCount = itineraryItems.size
                val itemProgress = if (totalItemsCount > 0) completedCount.toFloat() / totalItemsCount else 0f

                val totalPlannedDistance = segments.sumOf { it.plannedDistanceKm }
                val totalCoveredDistance = segments.sumOf { it.coveredDistanceKm }

                val dateFormat = remember { SimpleDateFormat("dd MMM", Locale.US) }
                val linkedGroup = groups.firstOrNull { it.id == activeTrip.groupId }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Trip Switcher Strip (if multiple trips)
                    if (allTrips.size > 1) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(FairShareTheme.colors.surface, RoundedCornerShape(8.dp))
                                    .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                allTrips.forEach { t ->
                                    val isSelected = t.id == activeTrip.id
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surfaceElevated,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { selectedTripId = t.id }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = t.name,
                                            style = FairShareTheme.typography.metadata,
                                            color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Trip Overview Card
                    item {
                        FSCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = FairShareTheme.colors.surfaceElevated
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = activeTrip.name,
                                            style = FairShareTheme.typography.title,
                                            color = FairShareTheme.colors.textPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${dateFormat.format(Date(activeTrip.startDate))} - ${dateFormat.format(Date(activeTrip.endDate))} • $durationDays Days",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                    }
                                    if (linkedGroup != null) {
                                        FSStatusBadge(
                                            text = "Linked: ${linkedGroup.name}",
                                            textColor = FairShareTheme.colors.accent,
                                            backgroundColor = FairShareTheme.colors.accentSoft
                                        )
                                    }
                                }

                                if (activeTrip.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = activeTrip.notes,
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textTertiary
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = FairShareTheme.colors.border)
                                Spacer(modifier = Modifier.height(12.dp))

                                // Progress row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Itinerary Progress",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$completedCount / $totalItemsCount items",
                                            style = FairShareTheme.typography.body,
                                            color = FairShareTheme.colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Covered / Planned",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "%.1f / %.1f km".format(totalCoveredDistance, totalPlannedDistance),
                                            style = FairShareTheme.typography.body,
                                            color = FairShareTheme.colors.positive,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { itemProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = FairShareTheme.colors.accent,
                                    trackColor = FairShareTheme.colors.surface
                                )
                            }
                        }
                    }

                    // 3. GPS Tracking & Daily Distance Card
                    item {
                        FSCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = FairShareTheme.colors.surfaceElevated
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "GPS TRACKING & DISTANCE",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.accent,
                                            letterSpacing = 1.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isTracking) "Live Recording Active (Filter: <=150 km/h, <=50m acc)" else "Tracking Paused",
                                            style = FairShareTheme.typography.metadata,
                                            color = if (isTracking) FairShareTheme.colors.positive else FairShareTheme.colors.textSecondary
                                        )
                                    }
                                    FSStatusBadge(
                                        text = if (isTracking) "GPS LIVE" else "IDLE",
                                        textColor = if (isTracking) FairShareTheme.colors.positive else FairShareTheme.colors.textSecondary,
                                        backgroundColor = if (isTracking) FairShareTheme.colors.positiveSoft else FairShareTheme.colors.surface
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Today's GPS Distance",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = "%.2f km".format(dailyDistanceKm),
                                            style = FairShareTheme.typography.title,
                                            color = FairShareTheme.colors.textPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Total Trip GPS Covered",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = "%.2f km".format(totalTripDistanceKm),
                                            style = FairShareTheme.typography.title,
                                            color = FairShareTheme.colors.accent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FSButton(
                                        text = if (isTracking) "Stop Tracking" else "Start Tracking",
                                        onClick = {
                                            if (isTracking) {
                                                val finalDist = tracker.stopTracking()
                                                Toast.makeText(context, "Tracking stopped. Session: %.2f km".format(finalDist), Toast.LENGTH_SHORT).show()
                                                val latestSeg = segments.lastOrNull()
                                                if (latestSeg != null && finalDist > 0.0) {
                                                    scope.launch {
                                                        tripRepository.updateSegmentCoveredDistance(latestSeg.id, latestSeg, finalDist)
                                                    }
                                                }
                                            } else {
                                                val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                                val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                                                if (!hasFine && !hasCoarse) {
                                                    permissionLauncher.launch(
                                                        arrayOf(
                                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                                        )
                                                    )
                                                } else {
                                                    if (!tracker.isLocationServiceEnabled()) {
                                                        Toast.makeText(context, "GPS / Location services disabled on device. Enable GPS or enter distance manually.", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        val started = tracker.startTracking()
                                                        if (started) {
                                                            Toast.makeText(context, "GPS distance tracking started.", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "Could not start GPS tracking.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        variant = if (isTracking) FSButtonVariant.Primary else FSButtonVariant.Secondary,
                                        modifier = Modifier.weight(1f)
                                    )

                                    FSButton(
                                        text = "Trip Recap",
                                        onClick = { showRecapDialog = true },
                                        variant = FSButtonVariant.Secondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                FSButton(
                                    text = "+ Add Travel Segment",
                                    onClick = { showAddSegmentDialog = true },
                                    variant = FSButtonVariant.Secondary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Estimated Fuel Cost Section
                    item {
                        FSCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = FairShareTheme.colors.surfaceElevated
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ESTIMATED FUEL COST",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.accent,
                                        letterSpacing = 1.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    FSStatusBadge(
                                        text = "Mileage Calculator",
                                        textColor = FairShareTheme.colors.textSecondary,
                                        backgroundColor = FairShareTheme.colors.surface
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))

                                var mileageInput by remember { mutableStateOf("15.0") }
                                var fuelPriceInput by remember { mutableStateOf("102.0") }
                                var useTrackedDist by remember { mutableStateOf(false) }

                                val effectiveDistance = if (useTrackedDist) {
                                    if (totalTripDistanceKm > 0.0) totalTripDistanceKm else totalCoveredDistance
                                } else {
                                    if (totalPlannedDistance > 0.0) totalPlannedDistance else (totalTripDistanceKm.takeIf { it > 0.0 } ?: 100.0)
                                }

                                val mileage = mileageInput.toDoubleOrNull() ?: 15.0
                                val price = fuelPriceInput.toDoubleOrNull() ?: 102.0
                                val litres = if (mileage > 0.0) effectiveDistance / mileage else 0.0
                                val totalFuelCost = litres * price

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = mileageInput,
                                        onValueChange = { mileageInput = it },
                                        label = { Text("Efficiency (km/L)", style = FairShareTheme.typography.metadata) },
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
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = fuelPriceInput,
                                        onValueChange = { fuelPriceInput = it },
                                        label = { Text("Fuel Price (₹/L)", style = FairShareTheme.typography.metadata) },
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
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Calculation Distance: %.1f km".format(effectiveDistance),
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = if (useTrackedDist) "Mode: GPS Tracked" else "Mode: Planned Route",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.accent,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clickable { useTrackedDist = !useTrackedDist }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = FairShareTheme.colors.border)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Fuel: %.1f L required".format(litres),
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = "Cost: ₹%.2f".format(totalFuelCost),
                                            style = FairShareTheme.typography.title,
                                            color = FairShareTheme.colors.accent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (onAddExpense != null && linkedGroup != null) {
                                        FSButton(
                                            text = "+ Add Fuel as Expense",
                                            onClick = {
                                                val currentUser = members.firstOrNull { it.isCurrentUser } ?: members.firstOrNull()
                                                if (currentUser != null && totalFuelCost > 0.0) {
                                                    val amountMinor = (totalFuelCost * 100).toLong()
                                                    val expense = Expense(
                                                        id = java.util.UUID.randomUUID().toString(),
                                                        groupId = linkedGroup.id,
                                                        description = "Fuel: ${activeTrip.name} (%.1f km)".format(effectiveDistance),
                                                        totalAmount = com.fairshare.android.core.domain.currency.Money(amountMinor, com.fairshare.android.core.domain.currency.Currency.INR),
                                                        payers = listOf(com.fairshare.android.core.domain.model.ExpensePayer(memberId = currentUser.id, amount = com.fairshare.android.core.domain.currency.Money(amountMinor, com.fairshare.android.core.domain.currency.Currency.INR))),
                                                        participants = members.map { com.fairshare.android.core.domain.model.ExpenseParticipant(memberId = it.id) },
                                                        splitMethod = com.fairshare.android.core.domain.model.SplitMethod.EQUAL,
                                                        mode = com.fairshare.android.core.domain.model.PaymentMode.UPI,
                                                        category = com.fairshare.android.core.domain.model.ExpenseCategory.FUEL,
                                                        timestamp = System.currentTimeMillis()
                                                    )
                                                    onAddExpense(expense)
                                                    Toast.makeText(context, "Fuel expense added to ${linkedGroup.name}!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Group has no members to split fuel expense.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            variant = FSButtonVariant.Primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Travel Segments (Routes between itinerary destinations)
                    if (segments.isNotEmpty()) {
                        item {
                            Text(
                                text = "Travel Segments (${segments.size})",
                                style = FairShareTheme.typography.section,
                                color = FairShareTheme.colors.textPrimary
                            )
                        }

                        items(segments) { seg ->
                            Surface(
                                shape = FairShareTheme.shapes.card,
                                color = FairShareTheme.colors.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.card)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            FSStatusBadge(
                                                text = seg.travelMode,
                                                textColor = FairShareTheme.colors.accent,
                                                backgroundColor = FairShareTheme.colors.accentSoft
                                            )
                                            Text(
                                                text = "${seg.origin} → ${seg.destination}",
                                                style = FairShareTheme.typography.body,
                                                color = FairShareTheme.colors.textPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            IconButton(
                                                onClick = { openMapRoute(seg.origin, seg.destination, seg.travelMode) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Directions,
                                                    contentDescription = "View Route on Google Maps",
                                                    tint = FairShareTheme.colors.accent
                                                )
                                            }
                                            FSButton(
                                                text = "Edit Dist",
                                                onClick = { segmentToUpdateDistance = seg },
                                                variant = FSButtonVariant.Secondary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Planned: ${seg.plannedDistanceKm} km",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = "Covered: ${seg.coveredDistanceKm} km",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.positive,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (seg.travelDurationMinutes > 0L) {
                                            Text(
                                                text = "${seg.travelDurationMinutes} min",
                                                style = FairShareTheme.typography.metadata,
                                                color = FairShareTheme.colors.textTertiary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Day-by-Day Itinerary Checklist
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily Itinerary & Checklist",
                                style = FairShareTheme.typography.section,
                                color = FairShareTheme.colors.textPrimary
                            )
                        }
                    }

                    // Group items by day
                    for (day in 1..durationDays) {
                        val dayItems = itineraryItems.filter { it.dayIndex == day }

                        item(key = "day_header_$day") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Day $day",
                                    style = FairShareTheme.typography.title,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                FSButton(
                                    text = "+ Add to Day $day",
                                    onClick = { showAddItemDialog = day },
                                    variant = FSButtonVariant.Secondary
                                )
                            }
                        }

                        if (dayItems.isEmpty()) {
                            item(key = "day_empty_$day") {
                                Surface(
                                    shape = FairShareTheme.shapes.card,
                                    color = FairShareTheme.colors.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.card)
                                ) {
                                    Text(
                                        text = "No activities planned for Day $day.",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textTertiary,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        } else {
                            items(dayItems, key = { it.id }) { item ->
                                val isDone = item.status == "COMPLETED"
                                Surface(
                                    shape = FairShareTheme.shapes.card,
                                    color = if (isDone) FairShareTheme.colors.surfaceElevated else FairShareTheme.colors.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            color = if (isDone) FairShareTheme.colors.positiveSoft else FairShareTheme.colors.border,
                                            shape = FairShareTheme.shapes.card
                                        )
                                        .clip(FairShareTheme.shapes.card)
                                        .clickable {
                                            scope.launch {
                                                tripRepository.toggleItineraryItemStatus(item)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Checkbox(
                                                checked = isDone,
                                                onCheckedChange = {
                                                    scope.launch {
                                                        tripRepository.toggleItineraryItemStatus(item)
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = FairShareTheme.colors.positive,
                                                    uncheckedColor = FairShareTheme.colors.textSecondary,
                                                    checkmarkColor = FairShareTheme.colors.surface
                                                )
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Column {
                                                Text(
                                                    text = item.title,
                                                    style = FairShareTheme.typography.body,
                                                    color = if (isDone) FairShareTheme.colors.textSecondary else FairShareTheme.colors.textPrimary,
                                                    fontWeight = if (isDone) FontWeight.Normal else FontWeight.Medium
                                                )
                                                if (item.destination.isNotBlank()) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .clickable { openMapLocation(item.destination) }
                                                            .padding(vertical = 2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.LocationOn,
                                                            contentDescription = "View on Maps",
                                                            tint = FairShareTheme.colors.accent,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = item.destination,
                                                            style = FairShareTheme.typography.metadata,
                                                            color = FairShareTheme.colors.accent,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                                if (item.notes.isNotBlank()) {
                                                    Text(
                                                        text = item.notes,
                                                        style = FairShareTheme.typography.metadata,
                                                        color = FairShareTheme.colors.textTertiary
                                                    )
                                                }
                                            }
                                        }

                                        FSStatusBadge(
                                            text = item.status,
                                            textColor = if (isDone) FairShareTheme.colors.positive else FairShareTheme.colors.textSecondary,
                                            backgroundColor = if (isDone) FairShareTheme.colors.positiveSoft else FairShareTheme.colors.surfaceElevated
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Modal: Add Itinerary Item
                val dayToAdd = showAddItemDialog
                if (dayToAdd != null) {
                    AddItineraryItemDialog(
                        dayIndex = dayToAdd,
                        onDismiss = { showAddItemDialog = null },
                        onConfirm = { title, dest, notes ->
                            showAddItemDialog = null
                            scope.launch {
                                val prevItem = itineraryItems.lastOrNull()
                                val newItemId = tripRepository.addItineraryItem(
                                    tripId = activeTrip.id,
                                    dayIndex = dayToAdd,
                                    title = title,
                                    destination = dest,
                                    notes = notes
                                )
                                if (prevItem != null && prevItem.destination.isNotBlank() && dest.isNotBlank()) {
                                    val calcKm = TripRoutingService.estimateDistanceBetweenDestinations(prevItem.destination, dest) ?: 0.0
                                    tripRepository.addSegment(
                                        tripId = activeTrip.id,
                                        origin = prevItem.destination,
                                        destination = dest,
                                        plannedDistanceKm = calcKm,
                                        fromItemId = prevItem.id,
                                        toItemId = newItemId
                                    )
                                }
                            }
                        }
                    )
                }

                // Modal: Add Segment Dialog
                if (showAddSegmentDialog) {
                    AddSegmentDialog(
                        onDismiss = { showAddSegmentDialog = false },
                        onConfirm = { origin, dest, mode, dist, dur ->
                            showAddSegmentDialog = false
                            scope.launch {
                                tripRepository.addSegment(
                                    tripId = activeTrip.id,
                                    origin = origin,
                                    destination = dest,
                                    travelMode = mode,
                                    plannedDistanceKm = dist,
                                    travelDurationMinutes = dur
                                )
                            }
                        }
                    )
                }

                // Modal: Update Distance Dialog
                val segToUpdate = segmentToUpdateDistance
                if (segToUpdate != null) {
                    UpdateSegmentDistanceDialog(
                        segment = segToUpdate,
                        onDismiss = { segmentToUpdateDistance = null },
                        onConfirm = { covered ->
                            segmentToUpdateDistance = null
                            scope.launch {
                                tripRepository.updateSegment(segToUpdate.copy(coveredDistanceKm = covered))
                            }
                        }
                    )
                }

                // Modal: Trip Recap Dialog
                if (showRecapDialog) {
                    TripRecapDialog(
                        trip = activeTrip,
                        itineraryItems = itineraryItems,
                        segments = segments,
                        expenses = expenses,
                        members = members,
                        payments = payments,
                        onDismiss = { showRecapDialog = false }
                    )
                }
            }
        }
    }

    // Modal: Create Trip Dialog
    if (showCreateTripDialog) {
        CreateTripDialog(
            groups = groups,
            onDismiss = { showCreateTripDialog = false },
            onConfirm = { name, start, end, groupId, notes ->
                showCreateTripDialog = false
                scope.launch {
                    val newTripId = tripRepository.createTrip(name, start, end, groupId, notes)
                    selectedTripId = newTripId
                }
            }
        )
    }
}
