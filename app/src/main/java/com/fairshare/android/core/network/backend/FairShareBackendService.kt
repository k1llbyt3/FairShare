package com.fairshare.android.core.network.backend

import com.fairshare.android.core.network.backend.auth.PhoneNormalizer
import com.fairshare.android.core.network.backend.auth.RealSmsVerificationGateway
import com.fairshare.android.core.network.backend.auth.SessionManager
import com.fairshare.android.core.network.backend.auth.UserSession
import com.fairshare.android.core.network.backend.auth.VerificationGateway
import com.fairshare.android.core.network.backend.auth.VerificationTicket
import com.fairshare.android.core.network.backend.guard.AuthenticationException
import com.fairshare.android.core.network.backend.guard.AuthorizationException
import com.fairshare.android.core.network.backend.guard.ConflictException
import com.fairshare.android.core.network.backend.guard.ResourceNotFoundException
import com.fairshare.android.core.network.backend.guard.ServerAuthorizationGuard
import com.fairshare.android.core.network.backend.guard.ServerFinancialValidator
import com.fairshare.android.core.network.backend.guard.ServerValidationException
import com.fairshare.android.core.network.backend.model.BackendActivityEvent
import com.fairshare.android.core.network.backend.model.BackendAuditEvent
import com.fairshare.android.core.network.backend.model.BackendExpense
import com.fairshare.android.core.network.backend.model.BackendGroup
import com.fairshare.android.core.network.backend.model.BackendGroupDelta
import com.fairshare.android.core.network.backend.model.BackendGroupMember
import com.fairshare.android.core.network.backend.model.BackendGroupMemberWithUser
import com.fairshare.android.core.network.backend.model.BackendInvitation
import com.fairshare.android.core.network.backend.model.BackendSettlementPayment
import com.fairshare.android.core.network.backend.model.BackendUser
import com.fairshare.android.core.network.backend.model.GroupRole
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class AuthResult(
    val session: UserSession,
    val user: BackendUser,
    val isNewUser: Boolean
)

data class InvitationDetails(
    val invitation: BackendInvitation,
    val group: BackendGroup,
    val invitedByUser: BackendUser
)

/**
 * Authoritative Cloud / Backend Service for FairShare.
 * Maintains canonical shared state for multi-device synchronization.
 * As defined in specs/Plan.md Phase 4 and specs/PRD.md Section 68.
 */
class FairShareBackendService(
    val verificationGateway: VerificationGateway = RealSmsVerificationGateway(),
    val sessionManager: SessionManager = SessionManager()
) {
    // Canonical data stores
    private val usersById = ConcurrentHashMap<String, BackendUser>()
    private val usersByPhone = ConcurrentHashMap<String, String>() // phone -> userId
    private val groupsById = ConcurrentHashMap<String, BackendGroup>()
    private val groupMembersByGroup = ConcurrentHashMap<String, MutableList<BackendGroupMember>>()
    private val invitationsByCode = ConcurrentHashMap<String, BackendInvitation>()
    private val expensesByGroup = ConcurrentHashMap<String, MutableList<BackendExpense>>()
    private val paymentsByGroup = ConcurrentHashMap<String, MutableList<BackendSettlementPayment>>()
    private val activityByGroup = ConcurrentHashMap<String, MutableList<BackendActivityEvent>>()
    private val auditByGroup = ConcurrentHashMap<String, MutableList<BackendAuditEvent>>()
    private val messagesByGroup = ConcurrentHashMap<String, MutableList<com.fairshare.android.core.network.backend.model.BackendMessage>>()
    private val attachmentsByGroup = ConcurrentHashMap<String, MutableList<com.fairshare.android.core.network.backend.model.BackendAttachment>>()

    private val random = SecureRandom()

    // -------------------------------------------------------------
    // Authentication & User Identity
    // -------------------------------------------------------------

    fun requestPhoneVerification(rawPhone: String): VerificationTicket {
        val normalized = PhoneNormalizer.normalize(rawPhone)
        return verificationGateway.sendVerificationCode(normalized)
    }

    fun verifyPhoneCode(
        rawPhone: String,
        code: String,
        displayName: String? = null,
        defaultCurrency: String? = null
    ): AuthResult {
        val normalized = PhoneNormalizer.normalize(rawPhone)
        val verified = verificationGateway.verifyCode(normalized, code)
        if (!verified) {
            throw AuthenticationException("Invalid or expired verification code")
        }

        var isNewUser = false
        val existingUserId = usersByPhone[normalized]
        val user = if (existingUserId != null && usersById.containsKey(existingUserId)) {
            val existing = usersById[existingUserId]!!
            if (displayName != null && displayName.isNotBlank() && displayName != existing.displayName) {
                val updated = existing.copy(
                    displayName = displayName.trim(),
                    defaultCurrency = defaultCurrency ?: existing.defaultCurrency,
                    updatedAt = System.currentTimeMillis()
                )
                usersById[existingUserId] = updated
                updated
            } else {
                existing
            }
        } else {
            isNewUser = true
            val newUserId = UUID.randomUUID().toString()
            val newUser = BackendUser(
                id = newUserId,
                phoneNumber = normalized,
                displayName = displayName?.trim()?.ifBlank { "User ${normalized.takeLast(4)}" } ?: "User ${normalized.takeLast(4)}",
                defaultCurrency = defaultCurrency ?: "INR",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            usersById[newUserId] = newUser
            usersByPhone[normalized] = newUserId
            newUser
        }

        val session = sessionManager.createSession(user.id)
        return AuthResult(session, user, isNewUser)
    }

    fun restoreSession(sessionToken: String, user: BackendUser): UserSession {
        usersById[user.id] = user
        usersByPhone[user.phoneNumber] = user.id
        return sessionManager.registerExistingSession(sessionToken, user.id)
    }

    fun restoreUser(user: BackendUser) {
        usersById[user.id] = user
        usersByPhone[user.phoneNumber] = user.id
    }

    fun restoreGroup(group: BackendGroup, members: List<BackendGroupMember> = emptyList()) {
        groupsById[group.id] = group
        val list = groupMembersByGroup.computeIfAbsent(group.id) { mutableListOf() }
        for (m in members) {
            if (list.none { it.userId == m.userId }) {
                list.add(m)
            }
        }
    }

    fun getCurrentUser(sessionToken: String): BackendUser {
        val session = authenticate(sessionToken)
        return usersById[session.userId]
            ?: throw ResourceNotFoundException("User not found for session")
    }

    fun updateProfile(
        sessionToken: String,
        displayName: String,
        defaultCurrency: String
    ): BackendUser {
        val session = authenticate(sessionToken)
        val existing = usersById[session.userId]
            ?: throw ResourceNotFoundException("User not found")

        if (displayName.isBlank()) {
            throw ServerValidationException("Display name cannot be blank")
        }

        val updated = existing.copy(
            displayName = displayName.trim(),
            defaultCurrency = defaultCurrency.trim().uppercase(),
            updatedAt = System.currentTimeMillis()
        )
        usersById[session.userId] = updated
        return updated
    }

    fun logout(sessionToken: String) {
        sessionManager.revokeSession(sessionToken)
    }

    fun deleteAccount(sessionToken: String) {
        val session = authenticate(sessionToken)
        val userId = session.userId
        val user = usersById[userId] ?: return

        // Per specs/PRD.md FR-AUTH-05: Anonymize personal identity to preserve group financial history
        val anonymized = user.copy(
            phoneNumber = "+0000000000",
            displayName = "Deleted Member",
            profileImageUrl = null,
            updatedAt = System.currentTimeMillis()
        )
        usersById[userId] = anonymized
        usersByPhone.remove(user.phoneNumber)
        sessionManager.revokeAllUserSessions(userId)
    }

    // -------------------------------------------------------------
    // Groups & Memberships
    // -------------------------------------------------------------

    fun createGroup(
        sessionToken: String,
        name: String,
        description: String = "",
        groupType: String = "TRIP",
        currency: String = "INR",
        startDate: Long? = null,
        endDate: Long? = null,
        timezone: String = "Asia/Kolkata"
    ): BackendGroup {
        val session = authenticate(sessionToken)
        val user = usersById[session.userId] ?: throw ResourceNotFoundException("User not found")

        if (name.isBlank()) {
            throw ServerValidationException("Group name cannot be blank")
        }

        val groupId = UUID.randomUUID().toString()
        val group = BackendGroup(
            id = groupId,
            name = name.trim(),
            description = description.trim(),
            groupType = groupType,
            currency = currency.uppercase(),
            createdById = user.id,
            startDate = startDate,
            endDate = endDate,
            timezone = timezone,
            isArchived = false,
            revision = 1L,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        groupsById[groupId] = group

        val members = groupMembersByGroup.computeIfAbsent(groupId) { mutableListOf() }
        members.add(
            BackendGroupMember(
                groupId = groupId,
                userId = user.id,
                role = GroupRole.OWNER,
                joinedAt = System.currentTimeMillis()
            )
        )

        logActivity(
            groupId = groupId,
            actorId = user.id,
            eventType = "GROUP_CREATED",
            summaryText = "${user.displayName} created group ${group.name}",
            entityType = "GROUP",
            entityId = groupId
        )

        return group
    }

    fun getGroup(sessionToken: String, groupId: String): BackendGroup {
        val session = authenticate(sessionToken)
        val group = groupsById[groupId] ?: throw ResourceNotFoundException("Group not found ($groupId)")
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)
        return group
    }

    fun listUserGroups(sessionToken: String, includeArchived: Boolean = false): List<BackendGroup> {
        val session = authenticate(sessionToken)
        val userId = session.userId

        val userGroupIds = groupMembersByGroup.entries
            .filter { entry -> entry.value.any { it.userId == userId && !it.isArchived } }
            .map { it.key }
            .toSet()

        return userGroupIds.mapNotNull { groupsById[it] }
            .filter { includeArchived || !it.isArchived }
            .sortedByDescending { it.createdAt }
    }

    fun updateGroup(
        sessionToken: String,
        groupId: String,
        name: String,
        description: String,
        currency: String,
        startDate: Long? = null,
        endDate: Long? = null,
        timezone: String? = null
    ): BackendGroup {
        val session = authenticate(sessionToken)
        val group = groupsById[groupId] ?: throw ResourceNotFoundException("Group not found ($groupId)")
        val members = groupMembersByGroup[groupId] ?: emptyList()
        val actorMember = ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)
        ServerAuthorizationGuard.requireAdminOrOwner(actorMember, "update group settings")

        if (name.isBlank()) {
            throw ServerValidationException("Group name cannot be blank")
        }

        val updated = group.copy(
            name = name.trim(),
            description = description.trim(),
            currency = currency.uppercase(),
            startDate = startDate ?: group.startDate,
            endDate = endDate ?: group.endDate,
            timezone = timezone ?: group.timezone,
            revision = group.revision + 1,
            updatedAt = System.currentTimeMillis()
        )
        groupsById[groupId] = updated

        logAudit(
            groupId = groupId,
            recordId = groupId,
            actorId = session.userId,
            operation = "UPDATE_GROUP",
            prevRev = group.revision,
            newRev = updated.revision,
            details = "Updated group settings"
        )

        return updated
    }

    fun archiveGroup(sessionToken: String, groupId: String): BackendGroup {
        val session = authenticate(sessionToken)
        val group = groupsById[groupId] ?: throw ResourceNotFoundException("Group not found ($groupId)")
        val members = groupMembersByGroup[groupId] ?: emptyList()
        val actorMember = ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)
        ServerAuthorizationGuard.requireAdminOrOwner(actorMember, "archive group")

        val updated = group.copy(
            isArchived = true,
            revision = group.revision + 1,
            updatedAt = System.currentTimeMillis()
        )
        groupsById[groupId] = updated
        return updated
    }

    fun unarchiveGroup(sessionToken: String, groupId: String): BackendGroup {
        val session = authenticate(sessionToken)
        val group = groupsById[groupId] ?: throw ResourceNotFoundException("Group not found ($groupId)")
        val members = groupMembersByGroup[groupId] ?: emptyList()
        val actorMember = ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)
        ServerAuthorizationGuard.requireAdminOrOwner(actorMember, "unarchive group")

        val updated = group.copy(
            isArchived = false,
            revision = group.revision + 1,
            updatedAt = System.currentTimeMillis()
        )
        groupsById[groupId] = updated
        return updated
    }

    fun getGroupMembers(
        sessionToken: String,
        groupId: String,
        includeArchived: Boolean = true
    ): List<BackendGroupMemberWithUser> {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        return members
            .filter { includeArchived || !it.isArchived }
            .mapNotNull { m ->
                usersById[m.userId]?.let { u ->
                    BackendGroupMemberWithUser(m, u)
                }
            }
    }

    fun addMember(
        sessionToken: String,
        groupId: String,
        targetUserId: String,
        role: GroupRole = GroupRole.MEMBER
    ) {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup.computeIfAbsent(groupId) { mutableListOf() }
        val actorMember = ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)
        ServerAuthorizationGuard.requireAdminOrOwner(actorMember, "add members")

        val targetUser = usersById[targetUserId]
            ?: throw ResourceNotFoundException("Target user not found ($targetUserId)")

        if (members.any { it.userId == targetUserId }) {
            throw ServerValidationException("User is already a member of this group")
        }

        members.add(
            BackendGroupMember(
                groupId = groupId,
                userId = targetUserId,
                role = role,
                joinedAt = System.currentTimeMillis()
            )
        )

        logActivity(
            groupId = groupId,
            actorId = session.userId,
            eventType = "MEMBER_JOINED",
            summaryText = "${targetUser.displayName} was added to the group",
            entityType = "MEMBER",
            entityId = targetUserId
        )
    }

    fun addNonAccountMember(
        sessionToken: String,
        groupId: String,
        name: String,
        phoneNumber: String? = null
    ): BackendUser {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup.computeIfAbsent(groupId) { mutableListOf() }
        val actorMember = ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)
        ServerAuthorizationGuard.requireAdminOrOwner(actorMember, "add non-account member")

        if (name.isBlank()) {
            throw ServerValidationException("Member name cannot be blank")
        }

        val nonAccountId = "non_acc_" + UUID.randomUUID().toString()
        val nonAccountPhone = if (!phoneNumber.isNullOrBlank()) {
            try {
                PhoneNormalizer.normalize(phoneNumber)
            } catch (_: Exception) {
                phoneNumber.trim()
            }
        } else {
            "+0000" + UUID.randomUUID().toString().replace("-", "").take(8)
        }

        val newUser = BackendUser(
            id = nonAccountId,
            phoneNumber = nonAccountPhone,
            displayName = name.trim(),
            profileImageUrl = null,
            defaultCurrency = "INR",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        usersById[nonAccountId] = newUser
        usersByPhone[nonAccountPhone] = nonAccountId

        members.add(
            BackendGroupMember(
                groupId = groupId,
                userId = nonAccountId,
                role = GroupRole.MEMBER,
                joinedAt = System.currentTimeMillis()
            )
        )

        logActivity(
            groupId = groupId,
            actorId = session.userId,
            eventType = "MEMBER_JOINED",
            summaryText = "${newUser.displayName} was added as a participant",
            entityType = "MEMBER",
            entityId = nonAccountId
        )

        return newUser
    }

    fun updateMemberRole(
        sessionToken: String,
        groupId: String,
        targetUserId: String,
        newRole: GroupRole
    ): BackendGroupMember {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup.computeIfAbsent(groupId) { mutableListOf() }
        val actorMember = ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        if (actorMember.role != GroupRole.OWNER) {
            throw ServerValidationException("Only group OWNER can change member roles")
        }

        val targetIndex = members.indexOfFirst { it.userId == targetUserId }
        if (targetIndex < 0) {
            throw ResourceNotFoundException("Target user is not a member of this group")
        }

        val updated = members[targetIndex].copy(role = newRole)
        members[targetIndex] = updated
        return updated
    }

    fun removeMember(sessionToken: String, groupId: String, targetUserId: String) {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup.computeIfAbsent(groupId) { mutableListOf() }
        val actorMember = ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)
        val targetMember = members.find { it.userId == targetUserId }
            ?: throw ResourceNotFoundException("Target member not found")

        ServerAuthorizationGuard.requireCanRemoveMember(actorMember, targetMember)

        // Historical preservation check (FR-GRP-05 / P6-T07):
        // If target member has historical transactions (expenses or payments), mark as archived
        val hasHistoricalRecords = expensesByGroup[groupId]?.any { exp ->
            !exp.isDeleted && (exp.createdById == targetUserId || exp.payerId == targetUserId ||
                exp.payers.any { it.memberId == targetUserId } || exp.participants.any { it.memberId == targetUserId })
        } == true || paymentsByGroup[groupId]?.any { pay ->
            !pay.isDeleted && (pay.fromUserId == targetUserId || pay.toUserId == targetUserId || pay.createdByUserId == targetUserId)
        } == true

        if (hasHistoricalRecords) {
            val idx = members.indexOfFirst { it.userId == targetUserId }
            if (idx >= 0) {
                members[idx] = members[idx].copy(isArchived = true)
            }
        } else {
            members.removeAll { it.userId == targetUserId }
        }

        val targetUser = usersById[targetUserId]

        logActivity(
            groupId = groupId,
            actorId = session.userId,
            eventType = "MEMBER_REMOVED",
            summaryText = "${targetUser?.displayName ?: targetUserId} left/was removed from the group",
            entityType = "MEMBER",
            entityId = targetUserId
        )
    }

    // -------------------------------------------------------------
    // Invitations & Joining
    // -------------------------------------------------------------

    fun createInvitation(
        sessionToken: String,
        groupId: String,
        inviteType: String = "CODE",
        maxUses: Int = 0,
        targetPhoneNumber: String? = null,
        expiresInMs: Long = 7 * 24 * 60 * 60 * 1000L
    ): BackendInvitation {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup.computeIfAbsent(groupId) { mutableListOf() }
        val actorMember = ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)
        ServerAuthorizationGuard.requireCanManageInvitations(actorMember)

        val codeChars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val codeStr = (1..8).map { codeChars[random.nextInt(codeChars.length)] }.joinToString("")
        val code = "${codeStr.substring(0, 4)}-${codeStr.substring(4)}"
        val now = System.currentTimeMillis()

        val invitation = BackendInvitation(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            invitedByUserId = session.userId,
            inviteCode = code,
            inviteType = inviteType,
            targetPhoneNumber = targetPhoneNumber?.let { PhoneNormalizer.normalize(it) },
            maxUses = maxUses,
            usedCount = 0,
            expiresAt = now + expiresInMs,
            isRevoked = false,
            createdAt = now
        )

        invitationsByCode[code] = invitation
        return invitation
    }

    fun getInvitationDetails(inviteCode: String): InvitationDetails {
        val cleanCode = inviteCode.trim().uppercase()
        val invitation = invitationsByCode[cleanCode]
            ?: throw ResourceNotFoundException("Invalid invitation code: $cleanCode")

        if (!invitation.isValid()) {
            throw ServerValidationException("This invitation has expired or reached its maximum usage limit.")
        }

        val group = groupsById[invitation.groupId]
            ?: throw ResourceNotFoundException("Group no longer exists")
        val creator = usersById[invitation.invitedByUserId]
            ?: BackendUser(invitation.invitedByUserId, "", "Group Member")

        return InvitationDetails(invitation, group, creator)
    }

    fun joinGroupByInvite(sessionToken: String, inviteCode: String): BackendGroup {
        val session = authenticate(sessionToken)
        val user = usersById[session.userId]
            ?: throw ResourceNotFoundException("User not found")

        val cleanCode = inviteCode.trim().uppercase()
        val invitation = invitationsByCode[cleanCode]
            ?: throw ResourceNotFoundException("Invalid invitation code: $cleanCode")

        if (!invitation.isValid()) {
            throw ServerValidationException("This invitation has expired or reached its maximum usage limit.")
        }

        // Validate targeted phone invitation
        if (!invitation.targetPhoneNumber.isNullOrBlank()) {
            val normalizedTarget = PhoneNormalizer.normalize(invitation.targetPhoneNumber)
            val normalizedUser = PhoneNormalizer.normalize(user.phoneNumber)
            if (normalizedTarget != normalizedUser) {
                throw ServerValidationException("This invitation was specifically created for a different phone number.")
            }
        }

        val group = groupsById[invitation.groupId]
            ?: throw ResourceNotFoundException("Group no longer exists")

        val members = groupMembersByGroup.computeIfAbsent(group.id) { mutableListOf() }
        val existingIndex = members.indexOfFirst { it.userId == user.id }
        if (existingIndex >= 0) {
            val existing = members[existingIndex]
            if (existing.isArchived) {
                // Reactivate previously archived member
                members[existingIndex] = existing.copy(isArchived = false, joinedAt = System.currentTimeMillis())
            } else {
                // Already active member
                return group
            }
        } else {
            members.add(
                BackendGroupMember(
                    groupId = group.id,
                    userId = user.id,
                    role = GroupRole.MEMBER,
                    joinedAt = System.currentTimeMillis(),
                    isArchived = false
                )
            )
        }

        val updatedInvitation = invitation.copy(usedCount = invitation.usedCount + 1)
        invitationsByCode[cleanCode] = updatedInvitation

        logActivity(
            groupId = group.id,
            actorId = user.id,
            eventType = "MEMBER_JOINED",
            summaryText = "${user.displayName} joined via invite code $cleanCode",
            entityType = "MEMBER",
            entityId = user.id
        )

        return group
    }

    fun revokeInvitation(sessionToken: String, groupId: String, invitationId: String) {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup[groupId] ?: emptyList()
        val actor = ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)
        ServerAuthorizationGuard.requireCanManageInvitations(actor)

        val entry = invitationsByCode.entries.find { it.value.id == invitationId && it.value.groupId == groupId }
            ?: throw ResourceNotFoundException("Invitation not found")

        invitationsByCode[entry.key] = entry.value.copy(isRevoked = true)
    }

    // -------------------------------------------------------------
    // Expenses (Deterministic Integer Minor Units)
    // -------------------------------------------------------------

    fun createExpense(
        sessionToken: String,
        groupId: String,
        expense: BackendExpense
    ): BackendExpense {
        val session = authenticate(sessionToken)
        val group = groupsById[groupId] ?: throw ResourceNotFoundException("Group not found ($groupId)")
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        // Server-side validation
        ServerFinancialValidator.validateExpense(expense, group, members)

        val expenseId = expense.id.ifBlank { UUID.randomUUID().toString() }
        val list = expensesByGroup.computeIfAbsent(groupId) { mutableListOf() }

        // Idempotency: repeated upload of the same operation must not create duplicates (P5-T03)
        val existingIndex = list.indexOfFirst { it.id == expenseId }
        if (existingIndex >= 0) {
            return list[existingIndex]
        }

        val canonicalExpense = expense.copy(
            id = expenseId,
            groupId = groupId,
            createdById = session.userId,
            revision = 1L,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        list.add(canonicalExpense)

        logActivity(
            groupId = groupId,
            actorId = session.userId,
            eventType = "EXPENSE_CREATED",
            summaryText = "Added expense ${canonicalExpense.description}: ${canonicalExpense.amountMinor / 100}.${(canonicalExpense.amountMinor % 100).toString().padStart(2, '0')} ${canonicalExpense.currency}",
            entityType = "EXPENSE",
            entityId = expenseId
        )

        return canonicalExpense
    }

    fun updateExpense(
        sessionToken: String,
        groupId: String,
        expense: BackendExpense
    ): BackendExpense {
        val session = authenticate(sessionToken)
        val group = groupsById[groupId] ?: throw ResourceNotFoundException("Group not found ($groupId)")
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        ServerFinancialValidator.validateExpense(expense, group, members)

        val list = expensesByGroup[groupId] ?: mutableListOf()
        val index = list.indexOfFirst { it.id == expense.id }
        if (index < 0) {
            throw ResourceNotFoundException("Expense not found (${expense.id})")
        }

        val prev = list[index]

        // Conflict detection: do not silently overwrite concurrent financial changes (P5-T07, Task D)
        if (expense.revision < prev.revision) {
            throw ConflictException("Concurrent modification conflict: server revision is ${prev.revision}, but operation was based on revision ${expense.revision}")
        }
        val updated = expense.copy(
            revision = prev.revision + 1,
            updatedAt = System.currentTimeMillis()
        )
        list[index] = updated

        logAudit(
            groupId = groupId,
            recordId = expense.id,
            actorId = session.userId,
            operation = "UPDATE_EXPENSE",
            prevRev = prev.revision,
            newRev = updated.revision,
            details = "Updated expense ${updated.description}"
        )

        return updated
    }

    fun deleteExpense(sessionToken: String, groupId: String, expenseId: String) {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        val list = expensesByGroup[groupId] ?: return
        val index = list.indexOfFirst { it.id == expenseId }
        if (index < 0) return

        val prev = list[index]
        val softDeleted = prev.copy(
            isDeleted = true,
            deletedAt = System.currentTimeMillis(),
            revision = prev.revision + 1,
            updatedAt = System.currentTimeMillis()
        )
        list[index] = softDeleted

        logActivity(
            groupId = groupId,
            actorId = session.userId,
            eventType = "EXPENSE_DELETED",
            summaryText = "Deleted expense ${prev.description}",
            entityType = "EXPENSE",
            entityId = expenseId
        )
    }

    fun listExpenses(sessionToken: String, groupId: String): List<BackendExpense> {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        return (expensesByGroup[groupId] ?: emptyList())
            .filter { !it.isDeleted }
            .sortedByDescending { it.timestamp }
    }

    // -------------------------------------------------------------
    // Settlement Payments
    // -------------------------------------------------------------

    fun recordPayment(
        sessionToken: String,
        groupId: String,
        payment: BackendSettlementPayment
    ): BackendSettlementPayment {
        val session = authenticate(sessionToken)
        val group = groupsById[groupId] ?: throw ResourceNotFoundException("Group not found ($groupId)")
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        ServerFinancialValidator.validateSettlementPayment(payment, group, members)

        val paymentId = payment.id.ifBlank { UUID.randomUUID().toString() }
        val list = paymentsByGroup.computeIfAbsent(groupId) { mutableListOf() }

        // Idempotency: repeated upload of the same payment must not create duplicates (P5-T03)
        val existingIndex = list.indexOfFirst { it.id == paymentId }
        if (existingIndex >= 0) {
            return list[existingIndex]
        }

        val canonicalPayment = payment.copy(
            id = paymentId,
            groupId = groupId,
            createdByUserId = session.userId,
            revision = 1L,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        list.add(canonicalPayment)

        val fromUser = usersById[payment.fromUserId]?.displayName ?: payment.fromUserId
        val toUser = usersById[payment.toUserId]?.displayName ?: payment.toUserId

        logActivity(
            groupId = groupId,
            actorId = session.userId,
            eventType = "PAYMENT_RECORDED",
            summaryText = "$fromUser paid $toUser ${payment.amountMinor / 100}.${(payment.amountMinor % 100).toString().padStart(2, '0')} ${payment.currency}",
            entityType = "PAYMENT",
            entityId = paymentId
        )

        return canonicalPayment
    }

    fun deletePayment(sessionToken: String, groupId: String, paymentId: String) {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        val list = paymentsByGroup[groupId] ?: return
        val index = list.indexOfFirst { it.id == paymentId }
        if (index < 0) return

        val prev = list[index]
        val deleted = prev.copy(
            isDeleted = true,
            deletedAt = System.currentTimeMillis(),
            revision = prev.revision + 1,
            updatedAt = System.currentTimeMillis()
        )
        list[index] = deleted
    }

    fun listPayments(sessionToken: String, groupId: String): List<BackendSettlementPayment> {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        return (paymentsByGroup[groupId] ?: emptyList())
            .filter { !it.isDeleted }
            .sortedByDescending { it.timestamp }
    }

    // -------------------------------------------------------------
    // Incremental Delta Synchronization (Phase 5)
    // -------------------------------------------------------------

    /**
     * Retrieves incremental group changes since the specified cursor timestamp (P5-T05).
     * Includes soft-deleted expenses and payments so that deletes/reversals are reconciled properly.
     */
    fun getGroupDelta(
        sessionToken: String,
        groupId: String,
        sinceTimestamp: Long
    ): BackendGroupDelta {
        val session = authenticate(sessionToken)
        val group = groupsById[groupId] ?: throw ResourceNotFoundException("Group not found ($groupId)")
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        val updatedExpenses = (expensesByGroup[groupId] ?: emptyList())
            .filter { it.updatedAt > sinceTimestamp }

        val updatedPayments = (paymentsByGroup[groupId] ?: emptyList())
            .filter { it.updatedAt > sinceTimestamp }

        val membersWithUser = members.mapNotNull { m ->
            usersById[m.userId]?.let { u -> BackendGroupMemberWithUser(m, u) }
        }

        return BackendGroupDelta(
            groupId = groupId,
            cursorTimestamp = System.currentTimeMillis(),
            expenses = updatedExpenses,
            payments = updatedPayments,
            group = if (group.updatedAt > sinceTimestamp) group else null,
            members = membersWithUser
        )
    }

    // -------------------------------------------------------------
    // Activity and Audit
    // -------------------------------------------------------------

    fun listActivityEvents(sessionToken: String, groupId: String): List<BackendActivityEvent> {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        return (activityByGroup[groupId] ?: emptyList())
            .sortedByDescending { it.timestamp }
    }

    fun listAuditEvents(sessionToken: String, groupId: String): List<BackendAuditEvent> {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup[groupId] ?: emptyList()
        val actor = ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)
        ServerAuthorizationGuard.requireAdminOrOwner(actor, "view audit events")

        return (auditByGroup[groupId] ?: emptyList())
            .sortedByDescending { it.timestamp }
    }

    // -------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------

    private fun authenticate(token: String): UserSession {
        return sessionManager.getSession(token)
            ?: throw AuthenticationException("Invalid or expired session token. Please authenticate.")
    }

    private fun logActivity(
        groupId: String,
        actorId: String,
        eventType: String,
        summaryText: String,
        entityType: String? = null,
        entityId: String? = null
    ) {
        val event = BackendActivityEvent(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            actorId = actorId,
            eventType = eventType,
            summaryText = summaryText,
            entityType = entityType,
            entityId = entityId,
            timestamp = System.currentTimeMillis()
        )
        activityByGroup.computeIfAbsent(groupId) { mutableListOf() }.add(event)
    }

    private fun logAudit(
        groupId: String,
        recordId: String,
        actorId: String,
        operation: String,
        prevRev: Long,
        newRev: Long,
        details: String
    ) {
        val audit = BackendAuditEvent(
            id = UUID.randomUUID().toString(),
            recordId = recordId,
            actorId = actorId,
            operation = operation,
            previousRevision = prevRev,
            newRevision = newRev,
            details = details,
            timestamp = System.currentTimeMillis()
        )
        auditByGroup.computeIfAbsent(groupId) { mutableListOf() }.add(audit)
    }

    // -------------------------------------------------------------
    // Group Chat & Comments (Phase 12)
    // -------------------------------------------------------------

    fun sendMessage(
        sessionToken: String,
        groupId: String,
        content: String,
        messageType: String = "TEXT",
        referencedEntityId: String? = null
    ): com.fairshare.android.core.network.backend.model.BackendMessage {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        if (content.isBlank() && referencedEntityId == null) {
            throw ServerValidationException("Message content cannot be blank")
        }

        val msg = com.fairshare.android.core.network.backend.model.BackendMessage(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            senderId = session.userId,
            content = content.trim(),
            messageType = messageType,
            referencedEntityId = referencedEntityId,
            timestamp = System.currentTimeMillis()
        )

        messagesByGroup.computeIfAbsent(groupId) { mutableListOf() }.add(msg)

        if (messageType == "EXPENSE_LINK" && referencedEntityId != null) {
            logActivity(
                groupId = groupId,
                actorId = session.userId,
                eventType = "COMMENT_ADDED",
                summaryText = "Commented on expense: ${content.take(30)}",
                entityType = "EXPENSE",
                entityId = referencedEntityId
            )
        }

        return msg
    }

    fun listMessages(sessionToken: String, groupId: String): List<com.fairshare.android.core.network.backend.model.BackendMessage> {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        return (messagesByGroup[groupId] ?: emptyList())
            .filter { !it.isDeleted }
            .sortedBy { it.timestamp }
    }

    fun listExpenseComments(sessionToken: String, groupId: String, expenseId: String): List<com.fairshare.android.core.network.backend.model.BackendMessage> {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        return (messagesByGroup[groupId] ?: emptyList())
            .filter { !it.isDeleted && it.referencedEntityId == expenseId }
            .sortedBy { it.timestamp }
    }

    fun deleteMessage(sessionToken: String, groupId: String, messageId: String) {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        val list = messagesByGroup[groupId] ?: return
        val index = list.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            val prev = list[index]
            if (prev.senderId != session.userId) {
                val actor = members.firstOrNull { it.userId == session.userId }
                    ?: throw AuthorizationException("User is not a member of this group")
                ServerAuthorizationGuard.requireAdminOrOwner(actor, "delete another member's message")
            }
            list[index] = prev.copy(isDeleted = true)
        }
    }

    // -------------------------------------------------------------
    // Evidence & Attachments (Phase 12)
    // -------------------------------------------------------------

    fun addAttachment(
        sessionToken: String,
        groupId: String,
        attachment: com.fairshare.android.core.network.backend.model.BackendAttachment
    ): com.fairshare.android.core.network.backend.model.BackendAttachment {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        attachmentsByGroup.computeIfAbsent(groupId) { mutableListOf() }.add(attachment)

        logActivity(
            groupId = groupId,
            actorId = session.userId,
            eventType = "ATTACHMENT_ADDED",
            summaryText = "Added evidence attachment (${attachment.mimeType})",
            entityType = if (attachment.expenseId != null) "EXPENSE" else "GROUP",
            entityId = attachment.expenseId ?: groupId
        )

        return attachment
    }

    fun listAttachments(
        sessionToken: String,
        groupId: String,
        expenseId: String? = null
    ): List<com.fairshare.android.core.network.backend.model.BackendAttachment> {
        val session = authenticate(sessionToken)
        val members = groupMembersByGroup[groupId] ?: emptyList()
        ServerAuthorizationGuard.requireMembership(session.userId, groupId, members)

        val list = attachmentsByGroup[groupId] ?: emptyList()
        return if (expenseId != null) {
            list.filter { it.expenseId == expenseId }
        } else {
            list
        }
    }

    /**
     * Hydrates local Room group and members into authoritative in-memory cloud state
     * so offline mutations can cleanly sync without 404 or foreign key validation failures.
     */
    fun hydrateGroupFromLocal(
        group: BackendGroup,
        members: List<BackendGroupMember>,
        users: List<BackendUser>
    ) {
        users.forEach { u ->
            usersById.putIfAbsent(u.id, u)
            if (u.phoneNumber.isNotBlank()) {
                usersByPhone.putIfAbsent(u.phoneNumber, u.id)
            }
        }
        groupsById.putIfAbsent(group.id, group)
        val currentMembers = groupMembersByGroup.computeIfAbsent(group.id) { mutableListOf() }
        members.forEach { m ->
            if (currentMembers.none { it.userId == m.userId }) {
                currentMembers.add(m)
            }
        }
    }
}
