package com.fairshare.android.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["startDate"])
    ]
)
data class TripEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val startDate: Long,
    val endDate: Long,
    val groupId: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "trip_itinerary_items",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["dayIndex", "orderIndex"])
    ]
)
data class TripItineraryItemEntity(
    @PrimaryKey
    val id: String,
    val tripId: String,
    val dayIndex: Int = 1,
    val title: String,
    val destination: String = "",
    val plannedDateEpochMs: Long = 0L,
    val notes: String = "",
    val status: String = "PLANNED", // PLANNED, COMPLETED, SKIPPED
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "trip_segments",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["orderIndex"])
    ]
)
data class TripSegmentEntity(
    @PrimaryKey
    val id: String,
    val tripId: String,
    val fromItineraryItemId: String? = null,
    val toItineraryItemId: String? = null,
    val origin: String,
    val destination: String,
    val travelMode: String = "DRIVE", // FLIGHT, TRAIN, BUS, DRIVE, WALK, OTHER
    val plannedDistanceKm: Double = 0.0,
    val coveredDistanceKm: Double = 0.0,
    val travelDurationMinutes: Long = 0L,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
