package com.atakan.adm.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.atakan.adm.data.db.entity.DownloadEntity
import com.atakan.adm.data.db.entity.SegmentEntity

@Database(
    entities = [DownloadEntity::class, SegmentEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AdmDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun segmentDao(): SegmentDao

    companion object {
        const val NAME = "adm.db"
    }
}
