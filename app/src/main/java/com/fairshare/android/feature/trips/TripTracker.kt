package com.fairshare.android.feature.trips

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Clean, lightweight GPS distance tracker.
 * Only tracks while explicitly active (Start Tracking -> Stop Tracking).
 * No background service or silent tracking.
 * Gracefully handles jitter, impossible jumps, and GPS loss.
 */
class TripTracker(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var lastLocation: Location? = null
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _accumulatedDistanceKm = MutableStateFlow(0.0)
    val accumulatedDistanceKm: StateFlow<Double> = _accumulatedDistanceKm.asStateFlow()

    private val _dailyDistanceKm = MutableStateFlow(0.0)
    val dailyDistanceKm: StateFlow<Double> = _dailyDistanceKm.asStateFlow()

    private val _totalTripDistanceKm = MutableStateFlow(0.0)
    val totalTripDistanceKm: StateFlow<Double> = _totalTripDistanceKm.asStateFlow()

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // Filter poor accuracy (> 50m)
            if (location.hasAccuracy() && location.accuracy > 50f) {
                return
            }

            val prev = lastLocation
            if (prev != null) {
                val distanceMeters = prev.distanceTo(location)
                val timeDiffSec = (location.time - prev.time).coerceAtLeast(1000L) / 1000.0

                // Filter micro-jitter (< 3m) and impossible speed jumps (> 150 km/h ≈ 41.67 m/s for ground travel)
                if (distanceMeters >= 3.0) {
                    val speedMps = distanceMeters / timeDiffSec
                    if (speedMps <= 41.67) { // 150 km/h max threshold
                        val distanceKm = distanceMeters / 1000.0
                        _accumulatedDistanceKm.value += distanceKm
                        _dailyDistanceKm.value += distanceKm
                        _totalTripDistanceKm.value += distanceKm
                    }
                }
            }
            lastLocation = location
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun isLocationServiceEnabled(): Boolean {
        if (locationManager == null) return false
        val isGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNet = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return isGps || isNet
    }

    @SuppressLint("MissingPermission")
    fun startTracking(): Boolean {
        if (locationManager == null) return false
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetEnabled) return false

        try {
            _accumulatedDistanceKm.value = 0.0
            lastLocation = null

            if (isGpsEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L, // 2s
                    3f,    // 3m
                    locationListener
                )
            } else if (isNetEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    3f,
                    locationListener
                )
            }
            _isTracking.value = true
            return true
        } catch (_: SecurityException) {
            return false
        }
    }

    fun stopTracking(): Double {
        val total = _accumulatedDistanceKm.value
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {}
        _isTracking.value = false
        lastLocation = null
        return total
    }

    fun resetDailyDistance() {
        _dailyDistanceKm.value = 0.0
    }

    fun addManualDistance(km: Double) {
        if (km > 0.0) {
            _accumulatedDistanceKm.value += km
            _dailyDistanceKm.value += km
            _totalTripDistanceKm.value += km
        }
    }
}
