package com.fairshare.android.core.database.repository

import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.ActivityEventEntity
import com.fairshare.android.core.database.entity.MessageEntity
import com.fairshare.android.core.domain.collaboration.CollaborationEngine
import com.fairshare.android.core.domain.collaboration.ExpenseComment
import com.fairshare.android.core.domain.collaboration.GroupActivityItem
import com.fairshare.android.core.domain.collaboration.GroupMessage
import com.fairshare.android.core.network.client.FairShareApiClient
import com.fairshare.android.core.sync.SyncEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class CollaborationRepository(
    private val db: FairShareDatabase,
    private val syncEngine: SyncEngine? = null,
    private val apiClient: FairShareApiClient? = null
) {
    private val activityDao = db.activityDao()
    private val messageDao = db.messageDao()
    private val userDao = db.userDao()
    private val expenseDao = db.expenseDao()

    // -------------------------------------------------------------
    // Group Activity Events
    // -------------------------------------------------------------

    suspend fun recordActivity(
        groupId: String,
        actorId: String,
        eventType: String,
        summaryText: String,
        entityType: String? = null,
        entityId: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): GroupActivityItem {
        val event = ActivityEventEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            actorId = actorId,
            eventType = eventType,
            summaryText = summaryText,
            entityType = entityType,
            entityId = entityId,
            timestamp = timestamp
        )
        activityDao.insertEvent(event)
        val actor = userDao.getUserById(actorId)
        return GroupActivityItem(
            id = event.id,
            groupId = event.groupId,
            actorId = event.actorId,
            actorName = actor?.displayName ?: "Member",
            eventType = event.eventType,
            summaryText = event.summaryText,
            entityType = event.entityType,
            entityId = event.entityId,
            timestamp = event.timestamp
        )
    }

    suspend fun getActivities(groupId: String): List<GroupActivityItem> {
        val users = userDao.getAllUsers().associateBy { it.id }
        return activityDao.getEventsForGroup(groupId).map { event ->
            GroupActivityItem(
                id = event.id,
                groupId = event.groupId,
                actorId = event.actorId,
                actorName = users[event.actorId]?.displayName ?: "Member",
                eventType = event.eventType,
                summaryText = event.summaryText,
                entityType = event.entityType,
                entityId = event.entityId,
                timestamp = event.timestamp
            )
        }
    }

    fun getActivitiesFlow(groupId: String): Flow<List<GroupActivityItem>> {
        return activityDao.getEventsForGroupFlow(groupId).map { list ->
            val users = userDao.getAllUsers().associateBy { it.id }
            list.map { event ->
                GroupActivityItem(
                    id = event.id,
                    groupId = event.groupId,
                    actorId = event.actorId,
                    actorName = users[event.actorId]?.displayName ?: "Member",
                    eventType = event.eventType,
                    summaryText = event.summaryText,
                    entityType = event.entityType,
                    entityId = event.entityId,
                    timestamp = event.timestamp
                )
            }
        }
    }

    // -------------------------------------------------------------
    // Group Chat Messages
    // -------------------------------------------------------------

    suspend fun sendMessage(
        groupId: String,
        senderId: String,
        content: String,
        messageType: String = "TEXT",
        referencedEntityId: String? = null
    ): GroupMessage {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entity = MessageEntity(
            id = id,
            groupId = groupId,
            senderId = senderId,
            content = content.trim(),
            messageType = messageType,
            referencedEntityId = referencedEntityId,
            timestamp = now,
            isDeleted = false,
            createdAt = now
        )
        messageDao.insertMessage(entity)

        // If referencing an expense, generate an activity event
        if (referencedEntityId != null && messageType == "EXPENSE_LINK") {
            val user = userDao.getUserById(senderId)
            val actorName = user?.displayName ?: "Member"
            recordActivity(
                groupId = groupId,
                actorId = senderId,
                eventType = "COMMENT_ADDED",
                summaryText = "$actorName commented: \"${content.take(30)}\"",
                entityType = "EXPENSE",
                entityId = referencedEntityId
            )
        }

        val user = userDao.getUserById(senderId)
        var refSummary: String? = null
        if (referencedEntityId != null) {
            val exp = expenseDao.getExpenseWithDetails(referencedEntityId)
            if (exp != null) {
                refSummary = "${exp.expense.description} • ₹${exp.expense.amountMinor / 100}"
            }
        }

        return GroupMessage(
            id = id,
            groupId = groupId,
            senderId = senderId,
            senderName = user?.displayName ?: "Me",
            content = content,
            messageType = messageType,
            referencedEntityId = referencedEntityId,
            referencedEntitySummary = refSummary,
            timestamp = now,
            isDeleted = false,
            isFromCurrentUser = true,
            isSynced = true
        )
    }

    suspend fun getMessages(groupId: String, currentUserId: String): List<GroupMessage> {
        val users = userDao.getAllUsers().associateBy { it.id }
        val expenses = expenseDao.getExpensesForGroup(groupId).associateBy { it.expense.id }

        return messageDao.getMessagesForGroup(groupId).map { entity ->
            val refSummary = entity.referencedEntityId?.let { refId ->
                expenses[refId]?.let { "${it.expense.description} • ₹${it.expense.amountMinor / 100}" }
            }
            GroupMessage(
                id = entity.id,
                groupId = entity.groupId,
                senderId = entity.senderId,
                senderName = users[entity.senderId]?.displayName ?: "Member",
                content = entity.content,
                messageType = entity.messageType,
                referencedEntityId = entity.referencedEntityId,
                referencedEntitySummary = refSummary,
                timestamp = entity.timestamp,
                isDeleted = entity.isDeleted,
                isFromCurrentUser = entity.senderId == currentUserId,
                isSynced = true
            )
        }
    }

    fun getMessagesFlow(groupId: String, currentUserId: String): Flow<List<GroupMessage>> {
        return messageDao.getMessagesForGroupFlow(groupId).map { list ->
            val users = userDao.getAllUsers().associateBy { it.id }
            val expenses = expenseDao.getExpensesForGroup(groupId).associateBy { it.expense.id }

            list.map { entity ->
                val refSummary = entity.referencedEntityId?.let { refId ->
                    expenses[refId]?.let { "${it.expense.description} • ₹${it.expense.amountMinor / 100}" }
                }
                GroupMessage(
                    id = entity.id,
                    groupId = entity.groupId,
                    senderId = entity.senderId,
                    senderName = users[entity.senderId]?.displayName ?: "Member",
                    content = entity.content,
                    messageType = entity.messageType,
                    referencedEntityId = entity.referencedEntityId,
                    referencedEntitySummary = refSummary,
                    timestamp = entity.timestamp,
                    isDeleted = entity.isDeleted,
                    isFromCurrentUser = entity.senderId == currentUserId,
                    isSynced = true
                )
            }
        }
    }

    // -------------------------------------------------------------
    // Expense Comments & Discussions
    // -------------------------------------------------------------

    suspend fun addExpenseComment(
        expenseId: String,
        groupId: String,
        authorId: String,
        text: String
    ): ExpenseComment {
        val tag = CollaborationEngine.detectDisputeTag(text)
        val formattedText = if (tag != null && !text.startsWith("[$tag]")) {
            "[$tag] $text"
        } else {
            text
        }

        sendMessage(
            groupId = groupId,
            senderId = authorId,
            content = formattedText,
            messageType = "EXPENSE_LINK",
            referencedEntityId = expenseId
        )

        val user = userDao.getUserById(authorId)
        return ExpenseComment(
            id = UUID.randomUUID().toString(),
            expenseId = expenseId,
            groupId = groupId,
            authorId = authorId,
            authorName = user?.displayName ?: "Me",
            text = formattedText,
            timestamp = System.currentTimeMillis(),
            isDisputeOrClarification = tag != null,
            disputeTag = tag
        )
    }

    suspend fun getExpenseComments(expenseId: String): List<ExpenseComment> {
        val users = userDao.getAllUsers().associateBy { it.id }
        val exp = expenseDao.getExpenseWithDetails(expenseId)
        val allMessages = if (exp != null) {
            messageDao.getMessagesForGroup(exp.expense.groupId)
        } else {
            messageDao.getMessagesForReferencedEntity(expenseId)
        }

        return allMessages
            .filter { it.referencedEntityId == expenseId && !it.isDeleted }
            .map { m ->
                val tag = CollaborationEngine.detectDisputeTag(m.content)
                ExpenseComment(
                    id = m.id,
                    expenseId = expenseId,
                    groupId = m.groupId,
                    authorId = m.senderId,
                    authorName = users[m.senderId]?.displayName ?: "Member",
                    text = m.content,
                    timestamp = m.timestamp,
                    isDisputeOrClarification = tag != null || m.content.startsWith("["),
                    disputeTag = tag
                )
            }
    }

    fun getExpenseCommentsFlow(expenseId: String): Flow<List<ExpenseComment>> {
        return messageDao.getMessagesForGroupFlow("").map {
            getExpenseComments(expenseId)
        }
    }
}
