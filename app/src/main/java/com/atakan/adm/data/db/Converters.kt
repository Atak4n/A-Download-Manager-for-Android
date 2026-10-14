package com.atakan.adm.data.db

import androidx.room.TypeConverter
import com.atakan.adm.core.model.Category
import com.atakan.adm.core.model.DownloadKind
import com.atakan.adm.core.model.DownloadStatus
import com.atakan.adm.core.model.SegmentStatus

class Converters {
    @TypeConverter fun toStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)
    @TypeConverter fun fromStatus(value: DownloadStatus): String = value.name

    @TypeConverter fun toSegmentStatus(value: String): SegmentStatus = SegmentStatus.valueOf(value)
    @TypeConverter fun fromSegmentStatus(value: SegmentStatus): String = value.name

    @TypeConverter fun toCategory(value: String): Category = Category.valueOf(value)
    @TypeConverter fun fromCategory(value: Category): String = value.name

    @TypeConverter fun toKind(value: String): DownloadKind = DownloadKind.valueOf(value)
    @TypeConverter fun fromKind(value: DownloadKind): String = value.name
}
