package com.fairshare.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fairshare.android.core.database.entity.TripEntity
import com.fairshare.android.core.database.entity.TripItineraryItemEntity
import com.fairshare.android.core.database.entity.TripSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    // Trips
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: String)

    @Query("SELECT * FROM trips ORDER BY startDate DESC")
    fun getAllTripsFlow(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripById(tripId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :tripId")
    fun getTripByIdFlow(tripId: String): Flow<TripEntity?>

    // Itinerary Items
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItineraryItem(item: TripItineraryItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItineraryItems(items: List<TripItineraryItemEntity>)

    @Update
    suspend fun updateItineraryItem(item: TripItineraryItemEntity)

    @Query("DELETE FROM trip_itinerary_items WHERE id = :itemId")
    suspend fun deleteItineraryItem(itemId: String)

    @Query("SELECT * FROM trip_itinerary_items WHERE tripId = :tripId ORDER BY dayIndex ASC, orderIndex ASC")
    fun getItineraryItemsFlow(tripId: String): Flow<List<TripItineraryItemEntity>>

    @Query("SELECT * FROM trip_itinerary_items WHERE tripId = :tripId ORDER BY dayIndex ASC, orderIndex ASC")
    suspend fun getItineraryItems(tripId: String): List<TripItineraryItemEntity>

    // Trip Segments
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: TripSegmentEntity)

    @Update
    suspend fun updateSegment(segment: TripSegmentEntity)

    @Query("DELETE FROM trip_segments WHERE id = :segmentId")
    suspend fun deleteSegment(segmentId: String)

    @Query("SELECT * FROM trip_segments WHERE tripId = :tripId ORDER BY orderIndex ASC")
    fun getSegmentsFlow(tripId: String): Flow<List<TripSegmentEntity>>

    @Query("SELECT * FROM trip_segments WHERE tripId = :tripId ORDER BY orderIndex ASC")
    suspend fun getSegments(tripId: String): List<TripSegmentEntity>
}
