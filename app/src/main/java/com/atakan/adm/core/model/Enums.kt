package com.atakan.adm.core.model

/** Lifecycle state of a download row. */
enum class DownloadStatus {
    QUEUED,
    PROBING,
    RUNNING,
    PAUSED,
    MERGING,
    CONVERTING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
    SCHEDULED;

    val isActive: Boolean
        get() = this == PROBING || this == RUNNING || this == MERGING ||
            this == CONVERTING || this == VERIFYING

    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED
}

enum class SegmentStatus { PENDING, RUNNING, DONE, FAILED }

enum class DownloadKind { FILE, VIDEO, AUDIO }

/** Where a finished file is filed away, derived from its extension. */
enum class Category { IMAGE, MUSIC, VIDEO, APPLICATION, DOCUMENT, ARCHIVE, OTHER }

enum class SubtitleMode { NONE, EMBED, SEPARATE }

enum class VideoContainer { MP4, MKV, WEBM, NONE }

enum class SortOrder { DATE, NAME, SIZE, STATUS, SPEED }
