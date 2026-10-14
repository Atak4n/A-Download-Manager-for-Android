package com.atakan.adm.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.atakan.adm.core.model.DownloadStatus
import com.atakan.adm.data.db.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun find(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observe(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt ASC")
    suspend fun byStatus(status: DownloadStatus): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('RUNNING','PROBING','MERGING','CONVERTING','VERIFYING')")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun nextQueued(limit: Int): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE scheduledAt IS NOT NULL AND status = 'SCHEDULED' AND scheduledAt <= :now")
    suspend fun dueScheduled(now: Long): List<DownloadEntity>

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: DownloadStatus)

    @Query("UPDATE downloads SET status = :status, errorMessage = :message, finishedAt = :finishedAt WHERE id = :id")
    suspend fun setFailed(id: Long, status: DownloadStatus, message: String?, finishedAt: Long)

    @Query("UPDATE downloads SET downloadedBytes = :bytes WHERE id = :id")
    suspend fun setProgress(id: Long, bytes: Long)

    @Query("UPDATE downloads SET phase = :phase WHERE id = :id")
    suspend fun setPhase(id: Long, phase: String?)

    @Query("UPDATE downloads SET retryCount = :count WHERE id = :id")
    suspend fun setRetryCount(id: Long, count: Int)

    @Query("UPDATE downloads SET activeConnections = :count WHERE id = :id")
    suspend fun setActiveConnections(id: Long, count: Int)

    /** Anything that claimed to be running when the process died is resumable, not lost. */
    @Query("UPDATE downloads SET status = 'PAUSED', activeConnections = 0 WHERE status IN ('RUNNING','PROBING','MERGING','CONVERTING','VERIFYING')")
    suspend fun markInterruptedAsPaused()
}
