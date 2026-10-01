package com.fairshare.android.core.database.repository

import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.AttachmentEntity
import com.fairshare.android.core.domain.evidence.EvidenceAttachment
import java.util.UUID

class AttachmentRepository(private val db: FairShareDatabase) {
    private val attachmentDao = db.candidateAndAttachmentDao()
    private val expenseDao = db.expenseDao()

    suspend fun saveAttachment(attachment: EvidenceAttachment): EvidenceAttachment {
        val entity = AttachmentEntity(
            id = attachment.id.ifBlank { UUID.randomUUID().toString() },
            groupId = attachment.groupId,
            localUri = attachment.localUri,
            remoteUrl = attachment.remoteUrl,
            mimeType = attachment.mimeType,
            sizeBytes = attachment.sizeBytes,
            createdAt = attachment.createdAt
        )
        attachmentDao.insertAttachment(entity)
        return attachment.copy(id = entity.id)
    }

    suspend fun getAttachmentsForGroup(groupId: String): List<EvidenceAttachment> {
        return attachmentDao.getAttachmentsForGroup(groupId).map { entity ->
            EvidenceAttachment(
                id = entity.id,
                groupId = entity.groupId,
                localUri = entity.localUri,
                remoteUrl = entity.remoteUrl,
                mimeType = entity.mimeType,
                sizeBytes = entity.sizeBytes,
                fileName = entity.localUri.substringAfterLast("/").ifBlank { "evidence.jpg" },
                createdAt = entity.createdAt
            )
        }
    }

    suspend fun getAttachmentById(id: String): EvidenceAttachment? {
        val entity = attachmentDao.getAttachmentById(id) ?: return null
        return EvidenceAttachment(
            id = entity.id,
            groupId = entity.groupId,
            localUri = entity.localUri,
            remoteUrl = entity.remoteUrl,
            mimeType = entity.mimeType,
            sizeBytes = entity.sizeBytes,
            fileName = entity.localUri.substringAfterLast("/").ifBlank { "evidence.jpg" },
            createdAt = entity.createdAt
        )
    }

    suspend fun deleteAttachment(id: String) {
        attachmentDao.deleteAttachment(id)
    }
}
