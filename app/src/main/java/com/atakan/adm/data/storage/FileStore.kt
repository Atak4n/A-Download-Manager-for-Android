package com.atakan.adm.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.atakan.adm.core.model.Category
import com.atakan.adm.core.util.FileNames
import com.atakan.adm.core.util.MimeTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the scratch .part files and publishing of finished downloads into
 * MediaStore, a SAF tree or (API 26-28) the public Downloads folder.
 */
@Singleton
class FileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Published(val uri: String, val displayName: String, val path: String?)

    /** App owned scratch area; needs no storage permission on any API level. */
    fun partDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, PART_DIR).apply { mkdirs() }
    }

    fun partFile(downloadId: Long): File = File(partDir(), "$downloadId.part")

    fun logFile(downloadId: Long): File =
        File(File(context.filesDir, LOG_DIR).apply { mkdirs() }, "$downloadId.log")

    /** Default public folder used when the user has not picked a SAF tree. */
    fun defaultPublicDir(category: Category?, useSubfolder: Boolean): File {
        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val base = File(downloads, APP_FOLDER)
        return if (useSubfolder && category != null) File(base, category.folderName()) else base
    }

    private fun relativePath(category: Category?, useSubfolder: Boolean): String {
        val base = "${Environment.DIRECTORY_DOWNLOADS}/$APP_FOLDER"
        return if (useSubfolder && category != null) "$base/${category.folderName()}/" else "$base/"
    }

    /**
     * Returns a name that does not collide in the destination, incrementing as
     * "file (2).zip" the way the macOS app does.
     */
    suspend fun uniqueDisplayName(
        desired: String,
        treeUri: String?,
        category: Category?,
        useSubfolder: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val taken = existingNames(treeUri, category, useSubfolder)
        FileNames.uniqueName(FileNames.sanitize(desired)) { candidate ->
            taken.any { it.equals(candidate, ignoreCase = true) }
        }
    }

    private fun existingNames(treeUri: String?, category: Category?, useSubfolder: Boolean): Set<String> {
        if (treeUri != null) {
            val dir = resolveTreeDir(treeUri, category, useSubfolder) ?: return emptySet()
            return dir.listFiles().mapNotNull { it.name }.toSet()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val names = mutableSetOf<String>()
            val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
            val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ?"
            val args = arrayOf(relativePath(category, useSubfolder))
            runCatching {
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null,
                )?.use { cursor ->
                    val column = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    while (cursor.moveToNext()) names += cursor.getString(column)
                }
            }
            // A pending MediaStore row may already own the file on disk.
            names += defaultPublicDir(category, useSubfolder).list()?.toSet().orEmpty()
            return names
        }
        return defaultPublicDir(category, useSubfolder).list()?.toSet().orEmpty()
    }

    private fun resolveTreeDir(treeUri: String, category: Category?, useSubfolder: Boolean): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return null
        if (!useSubfolder || category == null) return root
        val folder = category.folderName()
        return root.findFile(folder)?.takeIf { it.isDirectory } ?: root.createDirectory(folder) ?: root
    }

    /**
     * Copies the finished .part file to its destination and deletes the scratch
     * copy. Throws [IOException] with a readable message on failure.
     */
    suspend fun publish(
        part: File,
        displayName: String,
        mimeType: String?,
        category: Category,
        treeUri: String?,
        useSubfolder: Boolean,
    ): Published = withContext(Dispatchers.IO) {
        if (!part.exists()) throw IOException("Part file missing: ${part.absolutePath}")
        val mime = mimeType ?: MimeTypes.mimeFor(displayName) ?: "application/octet-stream"

        val published = when {
            treeUri != null -> publishToTree(part, displayName, mime, category, treeUri, useSubfolder)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                publishToMediaStore(part, displayName, mime, category, useSubfolder)
            else -> publishToLegacyDir(part, displayName, mime, category, useSubfolder)
        }
        part.delete()
        published
    }

    private fun publishToTree(
        part: File,
        displayName: String,
        mime: String,
        category: Category,
        treeUri: String,
        useSubfolder: Boolean,
    ): Published {
        val dir = resolveTreeDir(treeUri, category, useSubfolder)
            ?: throw IOException("Cannot open the selected folder")
        val target = dir.createFile(mime, displayName)
            ?: throw IOException("Cannot create \"$displayName\" in the selected folder")
        context.contentResolver.openOutputStream(target.uri)?.use { out ->
            FileInputStream(part).use { it.copyTo(out, COPY_BUFFER) }
        } ?: throw IOException("Cannot write to \"$displayName\"")
        return Published(target.uri.toString(), target.name ?: displayName, null)
    }

    private fun publishToMediaStore(
        part: File,
        displayName: String,
        mime: String,
        category: Category,
        useSubfolder: Boolean,
    ): Published {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath(category, useSubfolder))
            put(MediaStore.Downloads.IS_PENDING, 1)
            put(MediaStore.Downloads.SIZE, part.length())
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore rejected \"$displayName\"")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(part).use { it.copyTo(out, COPY_BUFFER) }
            } ?: throw IOException("Cannot write to \"$displayName\"")
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }, null, null)
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
        return Published(uri.toString(), displayName, null)
    }

    private fun publishToLegacyDir(
        part: File,
        displayName: String,
        mime: String,
        category: Category,
        useSubfolder: Boolean,
    ): Published {
        val dir = defaultPublicDir(category, useSubfolder)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Cannot create ${dir.absolutePath}")
        }
        val target = File(dir, displayName)
        part.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, COPY_BUFFER) }
        }
        // Index it so the Files app sees the download right away.
        val values = ContentValues().apply {
            @Suppress("DEPRECATION")
            put(MediaStore.MediaColumns.DATA, target.absolutePath)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.SIZE, target.length())
        }
        val uri = runCatching {
            context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
        }.getOrNull()
        return Published(uri?.toString() ?: Uri.fromFile(target).toString(), displayName, target.absolutePath)
    }

    companion object {
        const val APP_FOLDER = "A Download Manager"
        private const val PART_DIR = "parts"
        private const val LOG_DIR = "logs"
        private const val COPY_BUFFER = 1 shl 18
    }
}

/** Folder name used when the user enables category subfolders. */
fun Category.folderName(): String = when (this) {
    Category.IMAGE -> "Images"
    Category.MUSIC -> "Music"
    Category.VIDEO -> "Video"
    Category.APPLICATION -> "Apps"
    Category.DOCUMENT -> "Documents"
    Category.ARCHIVE -> "Archives"
    Category.OTHER -> "Other"
}
