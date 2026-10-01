package com.fairshare.android.core.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * Synchronization Engine Contract.
 * Ensures offline actions survive and merge deterministically.
 * As defined in specs/Plan.md Phase 5 and specs/PRD.md Section 91.
 */
interface SyncContract {
    val pendingSyncCount: Int
        get() = 0

    val syncState: StateFlow<SyncEngineState>

    /**
     * Trigger immediate synchronization for a specific group.
     */
    suspend fun syncGroup(groupId: String): Boolean

    /**
     * Trigger immediate synchronization for all active user groups.
     */
    suspend fun syncAll(): Boolean

    /**
     * Retry all operations that previously failed due to network or retryable errors.
     */
    suspend fun retryFailedOperations(): Boolean

    /**
     * Conflict resolution: user chooses to keep their local modification.
     */
    suspend fun resolveConflictKeepLocal(entityId: String, entityType: SyncEntityType)

    /**
     * Conflict resolution: user chooses to accept the remote server modification.
     */
    suspend fun resolveConflictAcceptRemote(entityId: String, entityType: SyncEntityType)

    /**
     * Returns the current sync status for a specific entity (Expense, Payment, etc.).
     */
    suspend fun getEntitySyncStatus(entityId: String): SyncStatus

    /**
     * Toggle simulated offline mode for verification and testing.
     */
    fun setSimulatedOffline(isOffline: Boolean)
}
