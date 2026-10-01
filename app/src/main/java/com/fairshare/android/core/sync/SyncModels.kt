package com.fairshare.android.core.sync

/**
 * Synchronization States for FairShare offline-first architecture.
 * Required by specs/Plan.md P5-T08 and specs/PRD.md Section 91.
 */
enum class SyncStatus {
    LOCAL_ONLY,
    PENDING_SYNC,
    SYNCING,
    SYNCED,
    SYNC_FAILED,
    CONFLICT;

    fun isPending(): Boolean = this == PENDING_SYNC || this == SYNCING || this == LOCAL_ONLY
    fun isFailureOrConflict(): Boolean = this == SYNC_FAILED || this == CONFLICT
}

enum class SyncEntityType {
    EXPENSE,
    PAYMENT,
    GROUP
}

enum class SyncOperationType {
    INSERT,
    UPDATE,
    DELETE
}

/**
 * Sync operation representation in memory / queue.
 * Required by specs/Plan.md P5-T01.
 */
data class SyncOperation(
    val operationId: String,
    val entityId: String,
    val entityType: SyncEntityType,
    val operationType: SyncOperationType,
    val groupId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val baseRevision: Long = 1L,
    val payloadJson: String = "",
    val status: String = "PENDING", // PENDING, IN_FLIGHT, SUCCESS, FAILED
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Represents a detected concurrent modification conflict.
 * Required by specs/Plan.md P5-T07 & Task D.
 * Never silently overwrites financial changes.
 */
data class SyncConflictRecord(
    val entityId: String,
    val entityType: SyncEntityType,
    val groupId: String,
    val localRevision: Long,
    val remoteRevision: Long,
    val localSummary: String,
    val remoteSummary: String,
    val localPayloadJson: String,
    val remotePayloadJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Reactive state exposed to the UI layer for real sync indicators.
 * Required by specs/Plan.md Task E.
 */
data class SyncEngineState(
    val isSyncing: Boolean = false,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val conflictCount: Int = 0,
    val lastSyncTimestamp: Long? = null,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val activeConflicts: List<SyncConflictRecord> = emptyList()
) {
    val isAllSynced: Boolean
        get() = !isSyncing && pendingCount == 0 && failedCount == 0 && conflictCount == 0
}
