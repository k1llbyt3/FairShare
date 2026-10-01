package com.fairshare.android.core.database.repository

import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.TripEntity
import com.fairshare.android.core.database.entity.TripItineraryItemEntity
import com.fairshare.android.core.database.entity.TripSegmentEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TripRepository(private val db: FairShareDatabase) {
    private val tripDao = db.tripDao()

    fun getAllTripsFlow(): Flow<List<TripEntity>> = tripDao.getAllTripsFlow()

    fun getTripByIdFlow(tripId: String): Flow<TripEntity?> = tripDao.getTripByIdFlow(tripId)

    suspend fun getTripById(tripId: String): TripEntity? = tripDao.getTripById(tripId)

    suspend fun createTrip(
        name: String,
        startDate: Long,
        endDate: Long,
        groupId: String? = null,
        notes: String = ""
    ): String {
        val tripId = UUID.randomUUID().toString()
        val trip = TripEntity(
            id = tripId,
            name = name,
            startDate = startDate,
            endDate = endDate,
            groupId = groupId,
            notes = notes,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        tripDao.insertTrip(trip)
        return tripId
    }

    suspend fun updateTrip(trip: TripEntity) {
        tripDao.updateTrip(trip.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTrip(tripId: String) {
        tripDao.deleteTrip(tripId)
    }

    // Itinerary items
    fun getItineraryItemsFlow(tripId: String): Flow<List<TripItineraryItemEntity>> =
        tripDao.getItineraryItemsFlow(tripId)

    suspend fun addItineraryItem(
        tripId: String,
        dayIndex: Int,
        title: String,
        destination: String = "",
        plannedDateEpochMs: Long = 0L,
        notes: String = "",
        status: String = "PLANNED",
        orderIndex: Int = 0
    ): String {
        val itemId = UUID.randomUUID().toString()
        val item = TripItineraryItemEntity(
            id = itemId,
            tripId = tripId,
            dayIndex = dayIndex,
            title = title,
            destination = destination,
            plannedDateEpochMs = plannedDateEpochMs,
            notes = notes,
            status = status,
            orderIndex = orderIndex,
            createdAt = System.currentTimeMillis()
        )
        tripDao.insertItineraryItem(item)
        return itemId
    }

    suspend fun updateItineraryItem(item: TripItineraryItemEntity) {
        tripDao.updateItineraryItem(item)
    }

    suspend fun toggleItineraryItemStatus(item: TripItineraryItemEntity) {
        val newStatus = if (item.status == "COMPLETED") "PLANNED" else "COMPLETED"
        tripDao.updateItineraryItem(item.copy(status = newStatus))
    }

    suspend fun deleteItineraryItem(itemId: String) {
        tripDao.deleteItineraryItem(itemId)
    }

    // Segments
    fun getSegmentsFlow(tripId: String): Flow<List<TripSegmentEntity>> =
        tripDao.getSegmentsFlow(tripId)

    suspend fun addSegment(
        tripId: String,
        origin: String,
        destination: String,
        travelMode: String = "DRIVE",
        plannedDistanceKm: Double = 0.0,
        coveredDistanceKm: Double = 0.0,
        travelDurationMinutes: Long = 0L,
        fromItemId: String? = null,
        toItemId: String? = null,
        orderIndex: Int = 0
    ): String {
        val segId = UUID.randomUUID().toString()
        val segment = TripSegmentEntity(
            id = segId,
            tripId = tripId,
            fromItineraryItemId = fromItemId,
            toItineraryItemId = toItemId,
            origin = origin,
            destination = destination,
            travelMode = travelMode,
            plannedDistanceKm = plannedDistanceKm,
            coveredDistanceKm = coveredDistanceKm,
            travelDurationMinutes = travelDurationMinutes,
            orderIndex = orderIndex,
            createdAt = System.currentTimeMillis()
        )
        tripDao.insertSegment(segment)
        return segId
    }

    suspend fun updateSegment(segment: TripSegmentEntity) {
        tripDao.updateSegment(segment)
    }

    suspend fun updateSegmentCoveredDistance(segmentId: String, currentSegment: TripSegmentEntity, addedDistanceKm: Double) {
        val updated = currentSegment.copy(
            coveredDistanceKm = (currentSegment.coveredDistanceKm + addedDistanceKm).coerceAtLeast(0.0)
        )
        tripDao.updateSegment(updated)
    }

    suspend fun deleteSegment(segmentId: String) {
        tripDao.deleteSegment(segmentId)
    }
}
