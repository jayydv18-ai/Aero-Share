package com.example.aeroshare.data.repository

import com.example.aeroshare.data.db.TransferDao
import com.example.aeroshare.data.db.TransferItemEntity
import com.example.aeroshare.data.db.TransferSessionEntity
import com.example.aeroshare.data.model.TransferItem
import com.example.aeroshare.data.model.TransferSession
import kotlinx.coroutines.flow.Flow

class TransferRepository(private val dao: TransferDao) {

    val allSessions: Flow<List<TransferSessionEntity>> = dao.getAllSessions()

    fun getItemsForSession(sessionId: String): Flow<List<TransferItemEntity>> {
        return dao.getItemsForSession(sessionId)
    }

    suspend fun saveSession(session: TransferSession) {
        val sessionEntity = TransferSessionEntity(
            sessionId = session.sessionId,
            direction = session.direction.name,
            remoteDeviceName = session.remoteDeviceName,
            fileCount = session.items.size,
            totalBytes = session.totalBytes,
            bytesTransferred = session.bytesTransferred,
            status = session.status.name,
            startTime = session.startTime,
            endTime = session.endTime,
            durationMillis = if (session.endTime > session.startTime) session.endTime - session.startTime else 0L,
            speedBytesPerSec = session.speedBytesPerSec
        )
        dao.insertSession(sessionEntity)

        val itemEntities = session.items.map { item ->
            TransferItemEntity(
                id = item.id,
                sessionId = session.sessionId,
                fileName = item.fileName,
                fileSize = item.fileSize,
                mimeType = item.mimeType,
                uriOrPath = item.localFilePath ?: item.uriString,
                status = item.status.name,
                bytesTransferred = item.bytesTransferred
            )
        }
        dao.insertItems(itemEntities)
    }

    suspend fun updateSessionCompletion(
        sessionId: String,
        status: String,
        bytesTransferred: Long,
        endTime: Long,
        durationMillis: Long,
        speedBytesPerSec: Long
    ) {
        dao.updateSessionCompletion(
            sessionId = sessionId,
            status = status,
            bytesTransferred = bytesTransferred,
            endTime = endTime,
            durationMillis = durationMillis,
            speedBytesPerSec = speedBytesPerSec
        )
    }

    suspend fun deleteSession(sessionId: String) {
        dao.deleteSession(sessionId)
    }

    suspend fun clearAllHistory() {
        dao.clearAllHistory()
    }
}
