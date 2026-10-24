package com.atakan.adm.download

import com.atakan.adm.core.model.DownloadStatus
import com.atakan.adm.core.model.SegmentStatus
import com.atakan.adm.core.util.Checksum
import com.atakan.adm.core.util.FileNames
import com.atakan.adm.core.util.StorageSpace
import com.atakan.adm.data.db.DownloadDao
import com.atakan.adm.data.db.SegmentDao
import com.atakan.adm.data.db.entity.DownloadEntity
import com.atakan.adm.data.db.entity.SegmentEntity
import com.atakan.adm.data.settings.SettingsRepository
import com.atakan.adm.data.storage.FileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/** Server asked us to slow down; the connection count is halved in response. */
private class ServerBusyException(val code: Int, val retryAfterMillis: Long?) :
    IOException("HTTP $code")

/** Server returned HTTP 416 or ETag mismatch; download needs clean restart from byte 0. */
class RangeNotSatisfiableException(message: String = "HTTP 416 Range Not Satisfiable") :
    IOException(message)

/** Runs one download: probe, split, transfer, verify, publish. */
class DownloadTask(
    private val downloadId: Long,
    private val client: OkHttpClient,
    private val prober: HttpProber,
    private val downloadDao: DownloadDao,
    private val segmentDao: SegmentDao,
    private val fileStore: FileStore,
    private val limiter: GlobalRateLimiter,
    private val settings: SettingsRepository,
    private val onProgress: suspend (ProgressSnapshot) -> Unit,
) {
    sealed interface Outcome {
        data object Completed : Outcome
        data object Paused : Outcome
        data class Failed(val message: String) : Outcome
        data class ChecksumMismatch(val expected: String, val actual: String) : Outcome
    }

    private val segmentCounters = mutableMapOf<Int, AtomicLong>()
    private val segmentSpeed = mutableMapOf<Int, Long>()
    private val speedHistory = ArrayDeque<Long>()

    suspend fun run(): Outcome {
        var entity = downloadDao.find(downloadId) ?: return Outcome.Failed("Download not found")
        return try {
            entity = prepare(entity)
            try {
                transfer(entity)
            } catch (rns: RangeNotSatisfiableException) {
                // HTTP 416 or ETag mismatch: wipe segments and scratch file, restart from byte 0 cleanly
                entity = resetAndPrepareClean(entity)
                transfer(entity)
            }
        } catch (cancellation: CancellationException) {
            persistOutcome(DownloadStatus.PAUSED)
            Outcome.Paused
        } catch (error: Exception) {
            val message = error.readableMessage()
            withContext(NonCancellable) {
                downloadDao.setFailed(downloadId, DownloadStatus.FAILED, message, System.currentTimeMillis())
            }
            Outcome.Failed(message)
        }
    }

    // ---------------------------------------------------------------- prepare

    /** Probes the server once and persists the segment plan. */
    private suspend fun prepare(initial: DownloadEntity): DownloadEntity {
        var entity = initial
        val existingSegments = segmentDao.forDownload(downloadId)
        if (existingSegments.isNotEmpty() && entity.resolvedUrl != null) {
            return entity // already planned, this is a resume
        }

        downloadDao.setStatus(downloadId, DownloadStatus.PROBING)
        val credentials = HttpProber.Credentials(
            cookieHeader = entity.cookieHeader,
            cookieHost = entity.cookieHost,
            referer = entity.referer,
            userAgent = entity.userAgent,
        )
        val probe = prober.probe(entity.url, credentials)

        val partFile = if (probe.totalBytes > 0) {
            fileStore.preallocatePartFile(downloadId, probe.totalBytes)
        } else {
            fileStore.partFile(downloadId)
        }

        val appSettings = settings.current()
        val nameFromProbe = if (entity.fileName.isNotBlank()) entity.fileName else probe.fileName
        val category = FileNames.categoryFor(nameFromProbe)
        val displayName = fileStore.uniqueDisplayName(
            desired = nameFromProbe,
            treeUri = entity.targetDirUri ?: appSettings.targetDirUri,
            category = category,
            useSubfolder = appSettings.useCategorySubfolders,
        )

        entity = entity.copy(
            resolvedUrl = probe.resolvedUrl,
            fileName = displayName,
            mimeType = entity.mimeType ?: probe.mimeType,
            category = category,
            totalBytes = probe.totalBytes,
            supportsRange = probe.supportsRange,
            etag = probe.etag,
            lastModified = probe.lastModified,
            partFilePath = partFile.absolutePath,
            cookieHeader = if (probe.credentialsKept) entity.cookieHeader else null,
            connections = entity.connections.coerceIn(1, SegmentPlanner.MAX_CONNECTIONS),
        )
        downloadDao.update(entity)

        segmentDao.deleteForDownload(downloadId)
        val segments = if (probe.supportsRange && probe.totalBytes > 0) {
            SegmentPlanner.toEntities(
                downloadId,
                SegmentPlanner.plan(probe.totalBytes, entity.connections),
            )
        } else {
            SegmentPlanner.singleSegment(downloadId, probe.totalBytes)
        }
        segmentDao.insertAll(segments)
        return entity
    }

    /** Cleans up stale part file and segments when HTTP 416 or ETag mismatch occurs, resetting progress. */
    private suspend fun resetAndPrepareClean(current: DownloadEntity): DownloadEntity {
        segmentDao.deleteForDownload(downloadId)
        val partFile = File(current.partFilePath)
        if (partFile.exists()) partFile.delete()

        val resetEntity = current.copy(
            downloadedBytes = 0L,
            etag = null,
            lastModified = null,
            resolvedUrl = null,
        )
        downloadDao.update(resetEntity)
        return prepare(resetEntity)
    }

    // --------------------------------------------------------------- transfer

    private suspend fun transfer(entity: DownloadEntity): Outcome {
        val partFile = File(entity.partFilePath)
        partFile.parentFile?.mkdirs()

        var segments = segmentDao.forDownload(downloadId)
        if (!entity.supportsRange) {
            // Without Range support a restart is the only correct resume.
            segments = segments.map { it.copy(downloadedBytes = 0L, status = SegmentStatus.PENDING) }
            segments.forEach { segmentDao.setProgress(it.id, 0L) }
            if (partFile.exists()) partFile.delete()
        }

        segments.forEach { segmentCounters[it.index] = AtomicLong(it.downloadedBytes) }

        downloadDao.update(
            entity.copy(
                status = DownloadStatus.RUNNING,
                startedAt = entity.startedAt ?: System.currentTimeMillis(),
                errorMessage = null,
            ),
        )

        val gate = ConnectionGate(
            maxConnections = entity.connections,
            initialConnections = entity.connections,
        )

        RandomAccessFile(partFile, "rw").use { raf ->
            if (entity.totalBytes > 0) raf.setLength(entity.totalBytes)
            val channel = raf.channel

            coroutineScope {
                val reporter = launch { reportProgress(entity, gate) }
                try {
                    val pending = SegmentPlanner.pending(segments)
                    val activeJobs = mutableListOf<kotlinx.coroutines.Job>()
                    pending.forEach { segment ->
                        activeJobs += launch(Dispatchers.IO) {
                            runSegment(entity, segment, channel, gate)
                        }
                    }

                    // Dynamic re-segmentation watcher
                    launch {
                        while (coroutineContext.isActive) {
                            delay(RESEGMENT_CHECK_INTERVAL_MILLIS)
                            if (!entity.supportsRange || entity.totalBytes <= 0) continue

                            val currentSegments = segmentDao.forDownload(downloadId)
                            val nextIndex = currentSegments.maxOfOrNull { it.index }?.plus(1) ?: currentSegments.size
                            val splitResult = SegmentPlanner.splitLargestPending(currentSegments, nextIndex)

                            if (splitResult != null && gate.current < entity.connections) {
                                val (donorNewEnd, newSegment) = splitResult
                                val donor = currentSegments.firstOrNull { it.endByte == newSegment.endByte }
                                if (donor != null && donor.status == SegmentStatus.RUNNING) {
                                    segmentDao.setEndByte(donor.id, donorNewEnd)
                                    segmentDao.insertAll(listOf(newSegment))
                                    segmentCounters[newSegment.index] = AtomicLong(0L)
                                    activeJobs += launch(Dispatchers.IO) {
                                        runSegment(entity, newSegment, channel, gate)
                                    }
                                }
                            }
                        }
                    }

                    activeJobs.forEach { it.join() }
                } finally {
                    reporter.cancel()
                }
            }
            channel.force(true)
        }

        val finalSegments = segmentDao.forDownload(downloadId)
        val failed = finalSegments.filter { it.status == SegmentStatus.FAILED }
        if (failed.isNotEmpty()) {
            val message = failed.firstNotNullOfOrNull { it.errorMessage } ?: "Segment failed"
            withContext(NonCancellable) {
                downloadDao.setFailed(downloadId, DownloadStatus.FAILED, message, System.currentTimeMillis())
            }
            return Outcome.Failed(message)
        }

        val downloaded = SegmentPlanner.completedBytes(finalSegments)
        if (entity.totalBytes > 0 && downloaded < entity.totalBytes) {
            persistOutcome(DownloadStatus.PAUSED)
            return Outcome.Paused
        }

        return finish(downloadDao.find(downloadId) ?: entity, partFile, downloaded)
    }

    private suspend fun runSegment(
        entity: DownloadEntity,
        initial: SegmentEntity,
        channel: FileChannel,
        gate: ConnectionGate,
    ) {
        var attempts = initial.attempts
        var backoff = INITIAL_BACKOFF_MILLIS
        val counter = segmentCounters.getOrPut(initial.index) { AtomicLong(initial.downloadedBytes) }

        while (coroutineContext.isActive) {
            val downloaded = counter.get()
            if (initial.endByte >= 0 && downloaded >= initial.endByte - initial.startByte + 1) {
                segmentDao.setResult(initial.id, SegmentStatus.DONE, attempts, null)
                return
            }
            try {
                segmentDao.setStatus(initial.id, SegmentStatus.RUNNING)
                gate.withConnection {
                    streamSegment(entity, initial, counter, channel)
                }
                segmentDao.setProgress(initial.id, counter.get())
                segmentDao.setResult(initial.id, SegmentStatus.DONE, attempts, null)
                gate.onSuccess()
                return
            } catch (cancellation: CancellationException) {
                withContext(NonCancellable) { segmentDao.setProgress(initial.id, counter.get()) }
                throw cancellation
            } catch (busy: ServerBusyException) {
                attempts++
                val widthNow = gate.onThrottled()
                withContext(NonCancellable) {
                    segmentDao.setProgress(initial.id, counter.get())
                    downloadDao.setActiveConnections(downloadId, widthNow)
                }
                if (attempts >= MAX_ATTEMPTS_PER_SEGMENT) {
                    segmentDao.setResult(initial.id, SegmentStatus.FAILED, attempts, "HTTP ${busy.code}")
                    return
                }
                delay(busy.retryAfterMillis ?: backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
            } catch (error: Exception) {
                attempts++
                withContext(NonCancellable) { segmentDao.setProgress(initial.id, counter.get()) }
                if (attempts >= MAX_ATTEMPTS_PER_SEGMENT) {
                    segmentDao.setResult(
                        initial.id, SegmentStatus.FAILED, attempts, error.readableMessage(),
                    )
                    return
                }
                segmentDao.setResult(initial.id, SegmentStatus.PENDING, attempts, error.readableMessage())
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
            }
        }
    }

    /** One HTTP exchange for one segment, resuming from the stored offset. */
    private suspend fun streamSegment(
        entity: DownloadEntity,
        segment: SegmentEntity,
        counter: AtomicLong,
        channel: FileChannel,
    ) = withContext(Dispatchers.IO) {
        val credentials = HttpProber.Credentials(
            cookieHeader = entity.cookieHeader,
            cookieHost = entity.cookieHost,
            referer = entity.referer,
            userAgent = entity.userAgent,
        )
        val url = entity.resolvedUrl ?: entity.url
        val from = segment.startByte + counter.get()
        val range = when {
            !entity.supportsRange -> null
            segment.endByte >= 0 -> "bytes=$from-${segment.endByte}"
            else -> "bytes=$from-"
        }
        val request = prober.buildRequest(
            url = url,
            credentials = credentials,
            allowedHost = entity.cookieHost,
            range = range,
            head = false,
        ).newBuilder().apply {
            // Make the server refuse the resume rather than silently hand back a
            // different file than the one we started with.
            val validator = entity.etag ?: entity.lastModified
            if (range != null && validator != null) header("If-Range", validator)
        }.build()

        val response = client.newCall(request).execute()
        response.use {
            when {
                it.code == 429 || it.code == 503 || it.code == 500 ->
                    throw ServerBusyException(it.code, retryAfterMillis(it.header("Retry-After")))
                it.code == 416 ->
                    throw RangeNotSatisfiableException("Server returned HTTP 416 for range $range")
                range != null && it.code == 200 && from > 0L ->
                    throw RangeNotSatisfiableException("Server ignored resume request (HTTP 200 instead of 206)")
                !it.isSuccessful -> throw IOException("HTTP ${it.code} ${it.message}")
            }

            // Verify ETag / Last-Modified if server provides one and we have recorded validator
            val currentEtag = it.header("ETag")?.trim()
            if (entity.etag != null && currentEtag != null && entity.etag != currentEtag) {
                throw RangeNotSatisfiableException("Remote content changed (ETag mismatch: ${entity.etag} vs $currentEtag)")
            }

            val source = it.body.byteStream()
            val buffer = ByteArray(limiter.chunkSize().coerceAtLeast(4 * 1024))
            var position = segment.startByte + counter.get()
            val limit = if (segment.endByte >= 0) segment.endByte else Long.MAX_VALUE
            var bytesSinceLastFlush = 0L

            while (coroutineContext.isActive) {
                val maxRead = minOf(buffer.size.toLong(), limit - position + 1).toInt()
                if (maxRead <= 0) break
                limiter.acquire(maxRead)
                val read = source.read(buffer, 0, maxRead)
                if (read <= 0) break
                var written = 0
                while (written < read) {
                    written += channel.write(
                        ByteBuffer.wrap(buffer, written, read - written),
                        position + written,
                    )
                }
                position += read
                counter.addAndGet(read.toLong())
                bytesSinceLastFlush += read

                // Periodic buffer flush & fsync for crash durability
                if (bytesSinceLastFlush >= FLUSH_THRESHOLD_BYTES) {
                    channel.force(false)
                    bytesSinceLastFlush = 0L
                }
            }

            if (bytesSinceLastFlush > 0) {
                channel.force(false)
            }

            if (segment.endByte < 0) {
                // Length was unknown: the stream end defines the segment end.
                val total = counter.get()
                segmentDao.setEndByte(segment.id, segment.startByte + total - 1)
                segmentDao.setProgress(segment.id, total)
            }
        }
    }

    // ----------------------------------------------------------------- finish

    private suspend fun finish(
        entity: DownloadEntity,
        partFile: File,
        downloaded: Long,
    ): Outcome = withContext(NonCancellable) {
        val appSettings = settings.current()
        var checksumActual: String? = null
        var checksumOk: Boolean? = null

        val expected = Checksum.normalize(entity.checksumExpected)
        if (expected != null) {
            downloadDao.setStatus(downloadId, DownloadStatus.VERIFYING)
            val algorithm = Checksum.detect(expected)
                ?: throw IOException("Unsupported checksum length: ${expected.length}")
            checksumActual = withContext(Dispatchers.IO) { Checksum.hash(partFile, algorithm) }
            checksumOk = Checksum.matches(expected, checksumActual)
        }

        val published = fileStore.publish(
            part = partFile,
            displayName = entity.fileName,
            mimeType = entity.mimeType,
            category = entity.category,
            treeUri = entity.targetDirUri ?: appSettings.targetDirUri,
            useSubfolder = appSettings.useCategorySubfolders,
        )

        val now = System.currentTimeMillis()
        // A checksum mismatch fails the row but the file itself is kept.
        val status = if (checksumOk == false) DownloadStatus.FAILED else DownloadStatus.COMPLETED
        downloadDao.update(
            entity.copy(
                status = status,
                downloadedBytes = downloaded,
                totalBytes = if (entity.totalBytes > 0) entity.totalBytes else downloaded,
                finishedAt = now,
                finalUri = published.uri,
                fileName = published.displayName,
                checksumActual = checksumActual,
                checksumVerified = checksumOk,
                checksumAlgorithm = expected?.let { Checksum.detect(it)?.name },
                errorMessage = if (checksumOk == false) "Checksum mismatch" else null,
                activeConnections = 0,
                phase = null,
            ),
        )
        if (checksumOk == false) {
            Outcome.ChecksumMismatch(expected.orEmpty(), checksumActual.orEmpty())
        } else {
            Outcome.Completed
        }
    }

    private suspend fun persistOutcome(status: DownloadStatus) = withContext(NonCancellable) {
        val downloaded = segmentDao.totalDownloaded(downloadId)
        downloadDao.setProgress(downloadId, downloaded)
        downloadDao.setStatus(downloadId, status)
        downloadDao.setActiveConnections(downloadId, 0)
    }

    // ---------------------------------------------------------------- reports

    private suspend fun reportProgress(entity: DownloadEntity, gate: ConnectionGate) {
        var lastTotal = segmentCounters.values.sumOf { it.get() }
        var lastPersist = 0L
        var smoothedSpeed = 0L
        while (coroutineContext.isActive) {
            delay(PROGRESS_INTERVAL_MILLIS)
            val segments = segmentDao.forDownload(downloadId)
            val total = segmentCounters.values.sumOf { it.get() }
            val delta = (total - lastTotal).coerceAtLeast(0)
            val instantSpeed = delta * 1000 / PROGRESS_INTERVAL_MILLIS
            lastTotal = total

            // Exponential moving average: 0.7 current + 0.3 instant, reduces jump spikes
            smoothedSpeed = if (smoothedSpeed == 0L) {
                instantSpeed
            } else {
                ((smoothedSpeed * 7 + instantSpeed * 3) / 10).coerceAtLeast(0)
            }

            speedHistory.addLast(smoothedSpeed)
            while (speedHistory.size > SPEED_HISTORY_SIZE) speedHistory.removeFirst()

            onProgress(
                ProgressSnapshot(
                    downloadId = downloadId,
                    downloadedBytes = total,
                    totalBytes = entity.totalBytes,
                    bytesPerSecond = smoothedSpeed,
                    segments = segments.map { segment ->
                        val live = segmentCounters[segment.index]?.get() ?: segment.downloadedBytes
                        SegmentProgress(
                            index = segment.index,
                            startByte = segment.startByte,
                            endByte = segment.endByte,
                            downloadedBytes = live,
                            status = segment.status,
                            bytesPerSecond = segmentSpeed[segment.index] ?: 0L,
                            attempts = segment.attempts,
                        )
                    },
                    speedHistory = speedHistory.toList(),
                ),
            )

            val now = System.currentTimeMillis()
            if (now - lastPersist >= PERSIST_INTERVAL_MILLIS) {
                lastPersist = now
                segments.forEach { segment ->
                    segmentCounters[segment.index]?.get()?.let { segmentDao.setProgress(segment.id, it) }
                }
                downloadDao.setProgress(downloadId, total)
                downloadDao.setActiveConnections(downloadId, gate.current)
            }
        }
    }

    companion object {
        const val MAX_ATTEMPTS_PER_SEGMENT = 12
        const val INITIAL_BACKOFF_MILLIS = 1_000L
        const val MAX_BACKOFF_MILLIS = 60_000L
        const val PROGRESS_INTERVAL_MILLIS = 500L
        const val PERSIST_INTERVAL_MILLIS = 2_000L
        const val SPEED_HISTORY_SIZE = 60
        const val FLUSH_THRESHOLD_BYTES = 1_048_576L // 1 MB
        const val RESEGMENT_CHECK_INTERVAL_MILLIS = 10_000L // 10 sn
        const val RESEGMENT_MIN_REMAINING_BYTES = 2 * 1_048_576L // 2 MB

        fun retryAfterMillis(header: String?): Long? {
            val seconds = header?.trim()?.toLongOrNull() ?: return null
            return (seconds * 1000).coerceIn(1_000L, MAX_BACKOFF_MILLIS)
        }
    }
}

/** Keeps error text short but specific enough to act on. */
internal fun Exception.readableMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName
