package com.fairshare.android.core.domain.evidence

data class EvidenceAttachment(
    val id: String,
    val groupId: String,
    val expenseId: String? = null,
    val localUri: String,
    val remoteUrl: String? = null,
    val mimeType: String = "image/jpeg",
    val sizeBytes: Long = 0L,
    val fileName: String = "evidence.jpg",
    val createdAt: Long = System.currentTimeMillis()
)

enum class EvidenceSource {
    CAMERA,
    GALLERY,
    OCR_RECEIPT
}
