package com.fairshare.android.core.network.repository

import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.GroupMemberEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.domain.model.GroupMember
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.network.backend.InvitationDetails
import com.fairshare.android.core.network.backend.model.BackendGroup
import com.fairshare.android.core.network.backend.model.BackendInvitation
import com.fairshare.android.core.network.backend.model.GroupRole
import com.fairshare.android.core.network.client.FairShareApiClient
import com.fairshare.android.core.network.client.NetworkResult
import java.util.UUID

/**
 * Backend Group Repository bridging cloud canonical groups to Room local storage.
 * Automatically synchronizes backend group & membership state to Room.
 */
class BackendGroupRepository(
    private val apiClient: FairShareApiClient,
    private val database: FairShareDatabase
) {
    suspend fun createGroup(
        name: String,
        description: String = "",
        groupType: String = "TRIP",
        currency: String = "INR",
        startDate: Long? = null,
        endDate: Long? = null,
        timezone: String = "Asia/Kolkata"
    ): NetworkResult<GroupEntity> {
        val currentUserId = apiClient.sessionStorage.currentUserId ?: ""
        return when (val result = apiClient.createGroup(name, description, groupType, currency, startDate, endDate, timezone)) {
            is NetworkResult.Success -> {
                val bg = result.data
                val entity = GroupEntity(
                    id = bg.id,
                    name = bg.name,
                    description = bg.description,
                    groupType = bg.groupType,
                    currency = bg.currency,
                    createdById = bg.createdById,
                    startDate = bg.startDate,
                    endDate = bg.endDate,
                    settlementDeadline = bg.settlementDeadline,
                    isArchived = bg.isArchived,
                    revision = bg.revision,
                    createdAt = bg.createdAt,
                    updatedAt = bg.updatedAt
                )
                // Cache into Room
                database.groupDao().insertGroup(entity)
                val effectiveUserId = currentUserId.ifBlank { bg.createdById }
                if (effectiveUserId.isNotBlank()) {
                    val existingUser = database.userDao().getUserById(effectiveUserId)
                    if (existingUser == null) {
                        val phone = apiClient.sessionStorage.userPhone ?: "+0000000000"
                        database.userDao().insertUser(
                            UserEntity(
                                id = effectiveUserId,
                                phoneNumber = phone,
                                displayName = "Owner",
                                defaultCurrency = bg.currency
                            )
                        )
                    }
                    database.groupDao().insertGroupMember(
                        GroupMemberEntity(
                            groupId = bg.id,
                            userId = effectiveUserId,
                            role = "OWNER",
                            joinedAt = System.currentTimeMillis()
                        )
                    )
                }
                NetworkResult.Success(entity)
            }
            is NetworkResult.Error -> {
                // Offline fallback: save to Room locally so user data is never lost
                val localId = UUID.randomUUID().toString()
                val effectiveUserId = currentUserId.ifBlank { "user_" + UUID.randomUUID().toString().take(8) }
                val entity = GroupEntity(
                    id = localId,
                    name = name.trim(),
                    description = description.trim(),
                    groupType = groupType,
                    currency = currency.uppercase(),
                    createdById = effectiveUserId,
                    startDate = startDate,
                    endDate = endDate,
                    isArchived = false,
                    revision = 1L,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                database.groupDao().insertGroup(entity)
                if (effectiveUserId.isNotBlank()) {
                    val existingUser = database.userDao().getUserById(effectiveUserId)
                    if (existingUser == null) {
                        val phone = apiClient.sessionStorage.userPhone ?: ("+0000" + UUID.randomUUID().toString().replace("-", "").take(8))
                        database.userDao().insertUser(
                            UserEntity(
                                id = effectiveUserId,
                                phoneNumber = phone,
                                displayName = "Owner",
                                defaultCurrency = currency.uppercase()
                            )
                        )
                    }
                    database.groupDao().insertGroupMember(
                        GroupMemberEntity(
                            groupId = localId,
                            userId = effectiveUserId,
                            role = "OWNER",
                            joinedAt = System.currentTimeMillis()
                        )
                    )
                }
                NetworkResult.Success(entity)
            }
        }
    }

    suspend fun updateGroup(
        groupId: String,
        name: String,
        description: String,
        currency: String,
        startDate: Long? = null,
        endDate: Long? = null,
        timezone: String? = null
    ): NetworkResult<GroupEntity> {
        return when (val result = apiClient.updateGroup(groupId, name, description, currency, startDate, endDate, timezone)) {
            is NetworkResult.Success -> {
                val bg = result.data
                val entity = GroupEntity(
                    id = bg.id,
                    name = bg.name,
                    description = bg.description,
                    groupType = bg.groupType,
                    currency = bg.currency,
                    createdById = bg.createdById,
                    startDate = bg.startDate,
                    endDate = bg.endDate,
                    settlementDeadline = bg.settlementDeadline,
                    isArchived = bg.isArchived,
                    revision = bg.revision,
                    createdAt = bg.createdAt,
                    updatedAt = bg.updatedAt
                )
                database.groupDao().insertGroup(entity)
                NetworkResult.Success(entity)
            }
            is NetworkResult.Error -> NetworkResult.Error(result.code, result.message, result.cause)
        }
    }

    suspend fun archiveGroup(groupId: String): NetworkResult<GroupEntity> {
        return when (val result = apiClient.archiveGroup(groupId)) {
            is NetworkResult.Success -> {
                val bg = result.data
                val existing = database.groupDao().getGroupById(groupId)
                if (existing != null) {
                    val updated = existing.copy(isArchived = true, updatedAt = System.currentTimeMillis())
                    database.groupDao().insertGroup(updated)
                    NetworkResult.Success(updated)
                } else {
                    val entity = GroupEntity(
                        id = bg.id,
                        name = bg.name,
                        description = bg.description,
                        groupType = bg.groupType,
                        currency = bg.currency,
                        createdById = bg.createdById,
                        isArchived = true,
                        revision = bg.revision
                    )
                    database.groupDao().insertGroup(entity)
                    NetworkResult.Success(entity)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.code, result.message, result.cause)
        }
    }

    suspend fun unarchiveGroup(groupId: String): NetworkResult<GroupEntity> {
        return when (val result = apiClient.unarchiveGroup(groupId)) {
            is NetworkResult.Success -> {
                val existing = database.groupDao().getGroupById(groupId)
                if (existing != null) {
                    val updated = existing.copy(isArchived = false, updatedAt = System.currentTimeMillis())
                    database.groupDao().insertGroup(updated)
                    NetworkResult.Success(updated)
                } else {
                    val bg = result.data
                    val entity = GroupEntity(
                        id = bg.id,
                        name = bg.name,
                        description = bg.description,
                        groupType = bg.groupType,
                        currency = bg.currency,
                        createdById = bg.createdById,
                        isArchived = false,
                        revision = bg.revision
                    )
                    database.groupDao().insertGroup(entity)
                    NetworkResult.Success(entity)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.code, result.message, result.cause)
        }
    }

    suspend fun joinGroupByInvite(inviteCode: String): NetworkResult<GroupEntity> {
        return when (val result = apiClient.joinGroupByInvite(inviteCode)) {
            is NetworkResult.Success -> {
                val bg = result.data
                val entity = GroupEntity(
                    id = bg.id,
                    name = bg.name,
                    description = bg.description,
                    groupType = bg.groupType,
                    currency = bg.currency,
                    createdById = bg.createdById,
                    startDate = bg.startDate,
                    endDate = bg.endDate,
                    settlementDeadline = bg.settlementDeadline,
                    isArchived = bg.isArchived,
                    revision = bg.revision,
                    createdAt = bg.createdAt,
                    updatedAt = bg.updatedAt
                )
                database.groupDao().insertGroup(entity)
                val currentUserId = apiClient.sessionStorage.currentUserId
                if (currentUserId != null) {
                    database.groupDao().insertGroupMember(
                        GroupMemberEntity(
                            groupId = bg.id,
                            userId = currentUserId,
                            role = "MEMBER",
                            joinedAt = System.currentTimeMillis()
                        )
                    )
                }
                // Refresh members from backend
                refreshGroupMembers(bg.id)
                NetworkResult.Success(entity)
            }
            is NetworkResult.Error -> NetworkResult.Error(result.code, result.message, result.cause)
        }
    }

    suspend fun fetchUserGroups(includeArchived: Boolean = false): NetworkResult<List<GroupEntity>> {
        val currentUserId = apiClient.sessionStorage.currentUserId ?: ""
        
        // Rehydrate all Room users and groups into backend service for continuous in-memory state synchronization
        try {
            val allUsers = database.userDao().getAllUsers()
            for (u in allUsers) {
                apiClient.backendService.restoreUser(
                    com.fairshare.android.core.network.backend.model.BackendUser(
                        id = u.id,
                        phoneNumber = u.phoneNumber,
                        displayName = u.displayName,
                        profileImageUrl = u.profileImageUrl,
                        defaultCurrency = u.defaultCurrency,
                        createdAt = u.createdAt,
                        updatedAt = u.updatedAt
                    )
                )
            }
            val allLocalGroups = if (includeArchived) {
                database.groupDao().getAllActiveGroups() + database.groupDao().getAllArchivedGroups()
            } else {
                database.groupDao().getAllActiveGroups()
            }
            for (lg in allLocalGroups) {
                val members = database.groupDao().getGroupMemberCrossRefs(lg.id).map {
                    com.fairshare.android.core.network.backend.model.BackendGroupMember(
                        groupId = it.groupId,
                        userId = it.userId,
                        role = try { com.fairshare.android.core.network.backend.model.GroupRole.valueOf(it.role) } catch (_: Exception) { com.fairshare.android.core.network.backend.model.GroupRole.MEMBER },
                        joinedAt = it.joinedAt
                    )
                }
                apiClient.backendService.restoreGroup(
                    com.fairshare.android.core.network.backend.model.BackendGroup(
                        id = lg.id,
                        name = lg.name,
                        description = lg.description,
                        groupType = lg.groupType,
                        currency = lg.currency,
                        createdById = lg.createdById,
                        startDate = lg.startDate,
                        endDate = lg.endDate,
                        settlementDeadline = lg.settlementDeadline,
                        isArchived = lg.isArchived,
                        revision = lg.revision,
                        createdAt = lg.createdAt,
                        updatedAt = lg.updatedAt
                    ),
                    members
                )
            }
        } catch (_: Exception) {}

        return when (val result = apiClient.listUserGroups(includeArchived)) {
            is NetworkResult.Success -> {
                val entities = result.data.map { bg ->
                    GroupEntity(
                        id = bg.id,
                        name = bg.name,
                        description = bg.description,
                        groupType = bg.groupType,
                        currency = bg.currency,
                        createdById = bg.createdById,
                        startDate = bg.startDate,
                        endDate = bg.endDate,
                        settlementDeadline = bg.settlementDeadline,
                        isArchived = bg.isArchived,
                        revision = bg.revision,
                        createdAt = bg.createdAt,
                        updatedAt = bg.updatedAt
                    )
                }
                // Mirror into Room
                for (entity in entities) {
                    database.groupDao().insertGroup(entity)
                    if (currentUserId.isNotBlank()) {
                        database.groupDao().insertGroupMember(
                            GroupMemberEntity(
                                groupId = entity.id,
                                userId = currentUserId,
                                role = if (entity.createdById == currentUserId) "OWNER" else "MEMBER",
                                joinedAt = entity.createdAt
                            )
                        )
                    }
                }
                val localGroups = if (currentUserId.isNotBlank()) {
                    if (includeArchived) {
                        database.groupDao().getUserActiveGroups(currentUserId) + database.groupDao().getUserArchivedGroups(currentUserId)
                    } else {
                        database.groupDao().getUserActiveGroups(currentUserId)
                    }
                } else {
                    if (includeArchived) {
                        database.groupDao().getAllActiveGroups() + database.groupDao().getAllArchivedGroups()
                    } else {
                        database.groupDao().getAllActiveGroups()
                    }
                }
                val fallbackGroups = if (localGroups.isEmpty()) {
                    if (includeArchived) database.groupDao().getAllActiveGroups() + database.groupDao().getAllArchivedGroups()
                    else database.groupDao().getAllActiveGroups()
                } else localGroups
                val merged = (entities + fallbackGroups).distinctBy { it.id }
                NetworkResult.Success(merged)
            }
            is NetworkResult.Error -> {
                // Offline fallback: load from Room
                val localGroups = if (currentUserId.isNotBlank()) {
                    if (includeArchived) {
                        database.groupDao().getUserActiveGroups(currentUserId) + database.groupDao().getUserArchivedGroups(currentUserId)
                    } else {
                        database.groupDao().getUserActiveGroups(currentUserId)
                    }
                } else {
                    if (includeArchived) {
                        database.groupDao().getAllActiveGroups() + database.groupDao().getAllArchivedGroups()
                    } else {
                        database.groupDao().getAllActiveGroups()
                    }
                }
                val fallbackGroups = if (localGroups.isEmpty()) {
                    if (includeArchived) database.groupDao().getAllActiveGroups() + database.groupDao().getAllArchivedGroups()
                    else database.groupDao().getAllActiveGroups()
                } else localGroups
                if (fallbackGroups.isNotEmpty()) {
                    NetworkResult.Success(fallbackGroups)
                } else {
                    NetworkResult.Error(result.code, result.message, result.cause)
                }
            }
        }
    }

    suspend fun addNonAccountMember(
        groupId: String,
        name: String,
        phoneNumber: String? = null
    ): NetworkResult<Member> {
        val nonAccountId = "non_acc_" + UUID.randomUUID().toString()
        val nonAccountPhone = phoneNumber?.trim()?.ifBlank { null }
            ?: ("+0000" + UUID.randomUUID().toString().replace("-", "").take(8))

        val localUser = UserEntity(
            id = nonAccountId,
            phoneNumber = nonAccountPhone,
            displayName = name.trim(),
            defaultCurrency = "INR"
        )
        database.userDao().insertUser(localUser)
        database.groupDao().insertGroupMember(
            GroupMemberEntity(
                groupId = groupId,
                userId = nonAccountId,
                role = "MEMBER",
                joinedAt = System.currentTimeMillis()
            )
        )
        // Also register in backend service
        apiClient.backendService.restoreUser(
            com.fairshare.android.core.network.backend.model.BackendUser(
                id = localUser.id,
                phoneNumber = localUser.phoneNumber,
                displayName = localUser.displayName,
                defaultCurrency = localUser.defaultCurrency
            )
        )
        val existingGroup = database.groupDao().getGroupById(groupId)
        if (existingGroup != null) {
            apiClient.backendService.restoreGroup(
                com.fairshare.android.core.network.backend.model.BackendGroup(
                    id = existingGroup.id,
                    name = existingGroup.name,
                    currency = existingGroup.currency,
                    createdById = existingGroup.createdById
                ),
                listOf(
                    com.fairshare.android.core.network.backend.model.BackendGroupMember(
                        groupId = groupId,
                        userId = nonAccountId,
                        role = com.fairshare.android.core.network.backend.model.GroupRole.MEMBER
                    )
                )
            )
        }
        return NetworkResult.Success(Member(nonAccountId, name.trim(), false))
    }

    suspend fun fetchArchivedGroups(): NetworkResult<List<GroupEntity>> {
        val local = database.groupDao().getAllArchivedGroups()
        return NetworkResult.Success(local)
    }

    suspend fun refreshGroupMembers(groupId: String): NetworkResult<List<Member>> {
        val currentUserId = apiClient.sessionStorage.currentUserId
        val localUsers = database.groupDao().getMembersForGroup(groupId)
        val localMembers = localUsers.map {
            Member(id = it.id, name = it.displayName, isCurrentUser = it.id == currentUserId)
        }

        return when (val result = apiClient.getGroupMembers(groupId, includeArchived = true)) {
            is NetworkResult.Success -> {
                val backendMembers = result.data.map { item ->
                    // Persist user in Room
                    database.userDao().insertUser(
                        UserEntity(
                            id = item.user.id,
                            phoneNumber = item.user.phoneNumber,
                            displayName = item.user.displayName,
                            profileImageUrl = item.user.profileImageUrl,
                            defaultCurrency = item.user.defaultCurrency,
                            createdAt = item.user.createdAt,
                            updatedAt = item.user.updatedAt
                        )
                    )
                    // Persist membership in Room
                    database.groupDao().insertGroupMember(
                        GroupMemberEntity(
                            groupId = groupId,
                            userId = item.user.id,
                            role = item.member.role.name,
                            joinedAt = item.member.joinedAt
                        )
                    )
                    Member(
                        id = item.user.id,
                        name = item.user.displayName,
                        isCurrentUser = item.user.id == currentUserId
                    )
                }
                val merged = (backendMembers + localMembers).distinctBy { it.id }
                NetworkResult.Success(merged)
            }
            is NetworkResult.Error -> {
                if (localMembers.isNotEmpty()) {
                    NetworkResult.Success(localMembers)
                } else {
                    NetworkResult.Error(result.code, result.message, result.cause)
                }
            }
        }
    }

    suspend fun getGroupMembersWithRoles(groupId: String): NetworkResult<List<GroupMember>> {
        val localUsers = database.groupDao().getMembersForGroup(groupId)
        val crossRefs = database.groupDao().getGroupMemberCrossRefs(groupId).associateBy { it.userId }
        val localList = localUsers.map { user ->
            val crossRef = crossRefs[user.id]
            val role = try {
                GroupRole.valueOf(crossRef?.role ?: "MEMBER")
            } catch (_: Exception) {
                GroupRole.MEMBER
            }
            GroupMember(
                groupId = groupId,
                userId = user.id,
                displayName = user.displayName,
                phoneNumber = user.phoneNumber,
                role = role,
                joinedAt = crossRef?.joinedAt ?: System.currentTimeMillis(),
                isArchived = false
            )
        }

        return when (val result = apiClient.getGroupMembers(groupId, includeArchived = true)) {
            is NetworkResult.Success -> {
                val backendList = result.data.map { item ->
                    GroupMember(
                        groupId = groupId,
                        userId = item.user.id,
                        displayName = item.user.displayName,
                        phoneNumber = item.user.phoneNumber,
                        role = item.member.role,
                        joinedAt = item.member.joinedAt,
                        isArchived = item.member.isArchived
                    )
                }
                val merged = (backendList + localList).distinctBy { it.userId }
                NetworkResult.Success(merged)
            }
            is NetworkResult.Error -> {
                if (localList.isNotEmpty()) {
                    NetworkResult.Success(localList)
                } else {
                    NetworkResult.Error(result.code, result.message, result.cause)
                }
            }
        }
    }

    suspend fun addMember(groupId: String, targetUserId: String, role: GroupRole = GroupRole.MEMBER): NetworkResult<Unit> {
        return apiClient.addMember(groupId, targetUserId, role)
    }

    suspend fun updateMemberRole(groupId: String, targetUserId: String, newRole: GroupRole): NetworkResult<Unit> {
        return apiClient.updateMemberRole(groupId, targetUserId, newRole)
    }

    suspend fun removeMember(groupId: String, targetUserId: String): NetworkResult<Unit> {
        return apiClient.removeMember(groupId, targetUserId)
    }

    suspend fun createInvitation(
        groupId: String,
        inviteType: String = "CODE",
        maxUses: Int = 0,
        targetPhoneNumber: String? = null,
        expiresInMs: Long = 7 * 24 * 60 * 60 * 1000L
    ): NetworkResult<BackendInvitation> {
        return apiClient.createInvitation(groupId, inviteType, maxUses, targetPhoneNumber, expiresInMs)
    }

    suspend fun getInvitationDetails(inviteCode: String): NetworkResult<InvitationDetails> {
        return apiClient.getInvitationDetails(inviteCode)
    }

    suspend fun revokeInvitation(groupId: String, invitationId: String): NetworkResult<Unit> {
        return apiClient.revokeInvitation(groupId, invitationId)
    }
}
