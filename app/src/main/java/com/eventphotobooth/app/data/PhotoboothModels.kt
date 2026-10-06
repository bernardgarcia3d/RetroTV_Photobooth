package com.eventphotobooth.app.data

import java.time.Instant
import java.util.UUID

data class BrandSnapshot(
    val organizerName: String = "Your venue",
    val header: String = "PHOTO MOMENT",
    val footer: String = "Thanks for visiting",
    val primaryColor: Long = 0xFFB4F5D1,
    val backgroundColor: Long = 0xFF101719,
)

enum class PrintStatus {
    NOT_QUEUED,
    QUEUED,
    PRINTING,
    PRINTED,
    FAILED,
}

enum class UploadStatus {
    LOCAL_ONLY,
    PENDING,
    UPLOADED,
    FAILED,
}

data class LocalPhotoSession(
    val id: String = UUID.randomUUID().toString(),
    val capturedAtEpochMs: Long = Instant.now().toEpochMilli(),
    val sourcePhotoPaths: List<String>,
    val printStripPath: String,
    val layoutId: LayoutId = LayoutId.THREE_STRIP,
    val printQuantity: Int = 1,
    val stickerPlacements: List<StickerPlacement> = emptyList(),
    val guestPrintBrightness: Int = 50,
    val configuration: BoothConfiguration = BoothConfiguration(),
    val printStatus: PrintStatus = PrintStatus.NOT_QUEUED,
    val uploadStatus: UploadStatus = UploadStatus.LOCAL_ONLY,
    val digitalStripPath: String? = null,
    val animatedGifPath: String? = null,
)

data class PrintJob(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val stripPath: String,
    val quantity: Int = 1,
    val status: PrintStatus = PrintStatus.QUEUED,
    val attempts: Int = 0,
    val lastError: String? = null,
)
