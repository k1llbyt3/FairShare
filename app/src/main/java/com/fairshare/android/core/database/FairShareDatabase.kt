package com.fairshare.android.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fairshare.android.core.database.dao.ActivityDao
import com.fairshare.android.core.database.dao.BudgetDao
import com.fairshare.android.core.database.dao.CandidateAndAttachmentDao
import com.fairshare.android.core.database.dao.ExpenseDao
import com.fairshare.android.core.database.dao.GroupDao
import com.fairshare.android.core.database.dao.MessageDao
import com.fairshare.android.core.database.dao.SettlementDao
import com.fairshare.android.core.database.dao.SyncDao
import com.fairshare.android.core.database.dao.TripDao
import com.fairshare.android.core.database.dao.UserDao
import com.fairshare.android.core.database.entity.ActivityEventEntity
import com.fairshare.android.core.database.entity.AttachmentEntity
import com.fairshare.android.core.database.entity.BudgetCategoryEntity
import com.fairshare.android.core.database.entity.BudgetEntity
import com.fairshare.android.core.database.entity.ExpenseEntity
import com.fairshare.android.core.database.entity.ExpenseItemEntity
import com.fairshare.android.core.database.entity.ExpenseParticipantEntity
import com.fairshare.android.core.database.entity.ExpensePayerEntity
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.GroupMemberEntity
import com.fairshare.android.core.database.entity.MessageEntity
import com.fairshare.android.core.database.entity.SettlementPaymentEntity
import com.fairshare.android.core.database.entity.SyncMetadataEntity
import com.fairshare.android.core.database.entity.SyncOperationEntity
import com.fairshare.android.core.database.entity.TransactionCandidateEntity
import com.fairshare.android.core.database.entity.TripEntity
import com.fairshare.android.core.database.entity.TripItineraryItemEntity
import com.fairshare.android.core.database.entity.TripSegmentEntity
import com.fairshare.android.core.database.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        ExpenseEntity::class,
        ExpensePayerEntity::class,
        ExpenseParticipantEntity::class,
        ExpenseItemEntity::class,
        SettlementPaymentEntity::class,
        BudgetEntity::class,
        BudgetCategoryEntity::class,
        MessageEntity::class,
        ActivityEventEntity::class,
        TransactionCandidateEntity::class,
        AttachmentEntity::class,
        SyncOperationEntity::class,
        SyncMetadataEntity::class,
        TripEntity::class,
        TripItineraryItemEntity::class,
        TripSegmentEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class FairShareDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun groupDao(): GroupDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun settlementDao(): SettlementDao
    abstract fun budgetDao(): BudgetDao
    abstract fun activityDao(): ActivityDao
    abstract fun messageDao(): MessageDao
    abstract fun syncDao(): SyncDao
    abstract fun candidateAndAttachmentDao(): CandidateAndAttachmentDao
    abstract fun tripDao(): TripDao

    companion object {
        const val DATABASE_NAME = "fairshare.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` TEXT NOT NULL PRIMARY KEY, `name` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `groupId` TEXT, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_groupId` ON `trips` (`groupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_startDate` ON `trips` (`startDate`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `trip_itinerary_items` (`id` TEXT NOT NULL PRIMARY KEY, `tripId` TEXT NOT NULL, `dayIndex` INTEGER NOT NULL, `title` TEXT NOT NULL, `destination` TEXT NOT NULL, `plannedDateEpochMs` INTEGER NOT NULL, `notes` TEXT NOT NULL, `status` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_itinerary_items_tripId` ON `trip_itinerary_items` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_itinerary_items_dayIndex_orderIndex` ON `trip_itinerary_items` (`dayIndex`, `orderIndex`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `trip_segments` (`id` TEXT NOT NULL PRIMARY KEY, `tripId` TEXT NOT NULL, `fromItineraryItemId` TEXT, `toItineraryItemId` TEXT, `origin` TEXT NOT NULL, `destination` TEXT NOT NULL, `travelMode` TEXT NOT NULL, `plannedDistanceKm` REAL NOT NULL, `coveredDistanceKm` REAL NOT NULL, `travelDurationMinutes` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_segments_tripId` ON `trip_segments` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_segments_orderIndex` ON `trip_segments` (`orderIndex`)")
            }
        }

        @Volatile
        private var INSTANCE: FairShareDatabase? = null

        fun getInstance(context: Context): FairShareDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FairShareDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }

        /**
         * For unit and integration tests.
         */
        fun createInMemory(context: Context): FairShareDatabase {
            return Room.inMemoryDatabaseBuilder(
                context,
                FairShareDatabase::class.java
            )
            .allowMainThreadQueries()
            .build()
        }
    }
}
