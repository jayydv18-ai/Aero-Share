package com.example.aeroshare.data.model

enum class TransferStatus {
    QUEUED,
    DISCOVERING,
    CONNECTING,
    AUTHENTICATING,
    WAITING_FOR_ACCEPT,
    ACCEPTED,
    TRANSFERRING,
    RETRYING,
    COMPLETED,
    FAILED,
    CANCELLED,
    CONNECTION_LOST,
    STORAGE_ERROR,
    PERMISSION_ERROR
}

enum class TransferDirection {
    SEND,
    RECEIVE
}

data class TransferItem(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val uriString: String,
    val bytesTransferred: Long = 0L,
    val status: TransferStatus = TransferStatus.QUEUED,
    val localFilePath: String? = null,
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (fileSize > 0) (bytesTransferred.toFloat() / fileSize).coerceIn(0f, 1f) else 0f
}

data class TransferSession(
    val sessionId: String,
    val direction: TransferDirection,
    val endpointId: String,
    val remoteDeviceName: String,
    val items: List<TransferItem>,
    val totalBytes: Long,
    val bytesTransferred: Long = 0L,
    val status: TransferStatus = TransferStatus.QUEUED,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = 0L,
    val authPin: String? = null,
    val speedBytesPerSec: Long = 0L,
    val currentItemIndex: Int = 0
) {
    val overallProgress: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val completedFilesCount: Int
        get() = items.count { it.status == TransferStatus.COMPLETED }

    val remainingFilesCount: Int
        get() = items.count { it.status != TransferStatus.COMPLETED && it.status != TransferStatus.FAILED }
}

data class NearbyDevice(
    val endpointId: String,
    val deviceName: String,
    val avatarId: String = "avatar_1",
    val serviceId: String = "",
    val isConnecting: Boolean = false
)

data class QrSessionPayload(
    val serviceId: String,
    val endpointId: String,
    val deviceName: String,
    val avatarId: String,
    val pin: String,
    val timestamp: Long
)
