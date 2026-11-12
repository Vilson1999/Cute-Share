package com.example.model

import java.util.Locale

enum class TransferStatus {
    TRANSFERRING,
    PAUSED,
    COMPLETED,
    CANCELLED
}

data class TransferFileItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val transferredBytes: Long = 0L,
    val progress: Float = 0f,
    val status: TransferStatus = TransferStatus.TRANSFERRING,
    val category: FileCategory = FileCategory.DOCUMENTS
) {
    val formattedSize: String
        get() = formatBytes(sizeBytes)

    val formattedTransferred: String
        get() = formatBytes(transferredBytes)

    val isFinished: Boolean
        get() = status == TransferStatus.COMPLETED || status == TransferStatus.CANCELLED
}

data class TransferSession(
    val id: String,
    val isOutgoing: Boolean,
    val peerName: String,
    val avatarRes: Int,
    val files: List<TransferFileItem>,
    val isPaused: Boolean = false,
    val isCancelled: Boolean = false,
    val isCompleted: Boolean = false,
    val currentSpeedBytesPerSec: Long = 34_500_000L, // 34.5 MB/s initial
    val remainingSeconds: Long = 12L,
    val wifiBand: String = "5 GHz",
    val statusMessage: String? = null
) {
    val totalSizeBytes: Long
        get() = files.sumOf { it.sizeBytes }

    val totalTransferredBytes: Long
        get() = files.sumOf { it.transferredBytes }

    val overallProgress: Float
        get() {
            val total = totalSizeBytes
            return if (total > 0) (totalTransferredBytes.toFloat() / total).coerceIn(0f, 1f) else 0f
        }

    val formattedTotalSize: String
        get() = formatBytes(totalSizeBytes)

    val formattedTransferredSize: String
        get() = formatBytes(totalTransferredBytes)

    val formattedSpeed: String
        get() {
            if (isPaused || isCompleted || isCancelled) return "0.0 MB/s"
            val mbPerSec = currentSpeedBytesPerSec.toDouble() / (1024 * 1024)
            return String.format(Locale.US, "%.1f MB/s", mbPerSec)
        }

    val formattedRemainingTime: String
        get() {
            if (isCompleted) return "Done"
            if (isCancelled) return "Cancelled"
            if (isPaused) return "Paused"
            val secs = remainingSeconds
            return if (secs >= 60) {
                val m = secs / 60
                val s = secs % 60
                "${m}m ${s}s"
            } else {
                "${secs}s"
            }
        }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}
