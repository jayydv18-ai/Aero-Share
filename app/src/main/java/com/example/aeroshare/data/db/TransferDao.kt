package com.example.aeroshare.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {

    @Query("SELECT * FROM transfer_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<TransferSessionEntity>>

    @Query("SELECT * FROM transfer_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: String): TransferSessionEntity?

    @Query("SELECT * FROM transfer_items WHERE sessionId = :sessionId")
    fun getItemsForSession(sessionId: String): Flow<List<TransferItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TransferSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<TransferItemEntity>)

    @Query("""
        UPDATE transfer_sessions 
        SET status = :status, bytesTransferred = :bytesTransferred, endTime = :endTime, 
            durationMillis = :durationMillis, speedBytesPerSec = :speedBytesPerSec 
        WHERE sessionId = :sessionId
    """)
    suspend fun updateSessionCompletion(
        sessionId: String,
        status: String,
        bytesTransferred: Long,
        endTime: Long,
        durationMillis: Long,
        speedBytesPerSec: Long
    )

    @Query("DELETE FROM transfer_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM transfer_sessions")
    suspend fun clearAllHistory()
}
