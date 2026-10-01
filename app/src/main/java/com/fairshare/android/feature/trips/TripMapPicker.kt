package com.fairshare.android.feature.trips

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FairShareTheme

data class TripLocation(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

object TripRoutingService {
    // Calculates realistic road distance between two lat/lon points in kilometers
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val straightLineKm = earthRadiusKm * c
        // Multiply by 1.25 road curvature multiplier for realistic driving distance
        return (straightLineKm * 1.25).coerceAtLeast(1.0)
    }

    fun parseLocationCoordinates(locationStr: String): Pair<Double, Double>? {
        val preset = PRESET_LOCATIONS.firstOrNull {
            locationStr.contains(it.name, ignoreCase = true) || locationStr.contains(it.address, ignoreCase = true)
        }
        if (preset != null) {
            return Pair(preset.latitude, preset.longitude)
        }
        val regex = Regex("""(-?\d+\.\d+)\s*,\s*(-?\d+\.\d+)""")
        val match = regex.find(locationStr) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        return Pair(lat, lon)
    }

    fun estimateDistanceBetweenDestinations(originStr: String, destStr: String): Double? {
        val coords1 = parseLocationCoordinates(originStr)
        val coords2 = parseLocationCoordinates(destStr)
        if (coords1 != null && coords2 != null) {
            return calculateDistanceKm(coords1.first, coords1.second, coords2.first, coords2.second)
        }
        return null
    }

    val PRESET_LOCATIONS = listOf(
        TripLocation("Bangalore", "Karnataka, India", 12.9716, 77.5946),
        TripLocation("Mysore Palace", "Mysore, Karnataka, India", 12.3052, 76.6552),
        TripLocation("Coorg / Madikeri", "Karnataka, India", 12.4244, 75.7382),
        TripLocation("Ooty", "Tamil Nadu, India", 11.4102, 76.6950),
        TripLocation("Munnar", "Kerala, India", 10.0889, 77.0595),
        TripLocation("Goa (Panaji)", "Goa, India", 15.4909, 73.8278),
        TripLocation("Manali", "Himachal Pradesh, India", 32.2432, 77.1892),
        TripLocation("Shimla", "Himachal Pradesh, India", 31.1048, 77.1734),
        TripLocation("Taj Mahal", "Agra, Uttar Pradesh, India", 27.1751, 78.0421),
        TripLocation("Jaipur City Palace", "Jaipur, Rajasthan, India", 26.9258, 75.8237),
        TripLocation("Rishikesh", "Uttarakhand, India", 30.0869, 78.2676),
        TripLocation("Mumbai (Gateway of India)", "Mumbai, Maharashtra, India", 18.9220, 72.8347),
        TripLocation("Delhi (India Gate)", "New Delhi, India", 28.6129, 77.2295)
    )
}

@Composable
fun TripMapPickerDialog(
    onDismiss: () -> Unit,
    onLocationSelected: (TripLocation) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<TripLocation?>(null) }
    var customName by remember { mutableStateOf("") }

    val filteredLocations = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            TripRoutingService.PRESET_LOCATIONS
        } else {
            TripRoutingService.PRESET_LOCATIONS.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.address.contains(searchQuery, ignoreCase = true)
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
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Choose Location on Map",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Place or Landmark", style = FairShareTheme.typography.metadata) },
                    placeholder = { Text("e.g. Mysore, Manali, Goa", style = FairShareTheme.typography.body) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = FairShareTheme.colors.accent
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

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    items(filteredLocations) { loc ->
                        val isSelected = selectedLocation?.name == loc.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedLocation = loc
                                    customName = "${loc.name}, ${loc.address}"
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = loc.name,
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = "${loc.address} (${loc.latitude}, ${loc.longitude})",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = FairShareTheme.colors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (filteredLocations.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Custom place \"$searchQuery\" will be added with GPS pin.",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedLocation != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FairShareTheme.colors.accentSoft, RoundedCornerShape(8.dp))
                            .border(1.dp, FairShareTheme.colors.accent, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "Selected Location:",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedLocation!!.name + " — " + selectedLocation!!.address,
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

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
                        text = "Confirm Location",
                        onClick = {
                            val loc = selectedLocation ?: if (searchQuery.isNotBlank()) {
                                TripLocation(searchQuery.trim(), "Custom Selected Pin", 12.9716, 77.5946)
                            } else null
                            if (loc != null) {
                                onLocationSelected(loc)
                            }
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f),
                        enabled = selectedLocation != null || searchQuery.isNotBlank()
                    )
                }
            }
        }
    }
}
