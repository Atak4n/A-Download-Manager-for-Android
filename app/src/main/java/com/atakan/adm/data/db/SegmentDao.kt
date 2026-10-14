package com.atakan.adm.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.atakan.adm.core.model.SegmentStatus
import com.atakan.adm.data.db.entity.SegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentDao {

    @Insert
    suspend fun insertAll(segments: List<SegmentEntity>)

    @Insert
    suspend fun insert(segment: SegmentEntity): Long

    @Update
    suspend fun update(segment: SegmentEntity)

    @Query("SELECT * FROM segments WHERE downloadId = :downloadId ORDER BY `index` ASC")
    suspend fun forDownload(downloadId: Long): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE downloadId = :downloadId ORDER BY `index` ASC")
    fun observeForDownload(downloadId: Long): Flow<List<SegmentEntity>>

    @Query("UPDATE segments SET downloadedBytes = :bytes WHERE id = :id")
    suspend fun setProgress(id: Long, bytes: Long)

    @Query("UPDATE segments SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: SegmentStatus)

    @Query("UPDATE segments SET status = :status, attempts = :attempts, errorMessage = :message WHERE id = :id")
    suspend fun setResult(id: Long, status: SegmentStatus, attempts: Int, message: String?)

    @Query("UPDATE segments SET endByte = :endByte WHERE id = :id")
    suspend fun setEndByte(id: Long, endByte: Long)

    @Query("DELETE FROM segments WHERE downloadId = :downloadId")
    suspend fun deleteForDownload(downloadId: Long)

    @Query("SELECT COALESCE(SUM(downloadedBytes), 0) FROM segments WHERE downloadId = :downloadId")
    suspend fun totalDownloaded(downloadId: Long): Long
}
