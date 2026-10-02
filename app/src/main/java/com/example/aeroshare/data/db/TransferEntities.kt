package com.example.aeroshare.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_sessions")
data class TransferSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val direction: String, // "SEND" or "RECEIVE"
    val remoteDeviceName: String,
    val fileCount: Int,
    val totalBytes: Long,
    val bytesTransferred: Long,
    val status: String,
    val startTime: Long,
    val endTime: Long,
    val durationMillis: Long,
    val speedBytesPerSec: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transfer_items",
    foreignKeys = [
        ForeignKey(
            entity = TransferSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class TransferItemEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val uriOrPath: String,
    val status: String,
    val bytesTransferred: Long
)
