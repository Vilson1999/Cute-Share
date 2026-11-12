package com.example.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale

object DownloadStorageHelper {

    const val DOWNLOAD_SUBFOLDER = "Cute Share"

    class DownloadDestination(
        val outputStream: OutputStream,
        val mediaStoreUri: Uri? = null,
        val legacyFile: File? = null
    )

    fun sanitizeDownloadFileName(
        rawName: String,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        val baseLeaf = rawName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim()
            .trimStart('.', ' ')
        if (baseLeaf.isBlank() || baseLeaf == "." || baseLeaf == "..") {
            return "CuteShare_$timestamp"
        }
        val dotIdx = baseLeaf.lastIndexOf('.')
        return if (dotIdx in 1 until baseLeaf.length - 1) {
            val stem = baseLeaf.substring(0, dotIdx).take(150).ifBlank { "CuteShare_$timestamp" }
            val ext = baseLeaf.substring(dotIdx + 1).take(16)
            "$stem.$ext"
        } else {
            baseLeaf.take(160)
        }
    }

    fun openDownloadDestination(context: Context, fileName: String): DownloadDestination? {
        val safeName = sanitizeDownloadFileName(fileName)
        val dotIdx = safeName.lastIndexOf('.')
        val stem = if (dotIdx > 0) safeName.substring(0, dotIdx) else safeName
        val ext = if (dotIdx in 1 until safeName.length - 1) safeName.substring(dotIdx + 1) else ""
        val detectedMime = if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.ROOT))
                ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collections = listOf(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            ).distinct()
            val candidateNames = buildList {
                add(safeName)
                for (i in 1..5) {
                    add(if (ext.isNotEmpty()) "$stem ($i).$ext" else "$stem ($i)")
                }
                val ts = System.currentTimeMillis()
                add(if (ext.isNotEmpty()) "${stem}_$ts.$ext" else "${stem}_$ts")
            }
            val candidateMimes = listOf(detectedMime, "application/octet-stream").distinct()
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_SUBFOLDER"

            for (collectionUri in collections) {
                for (candidateName in candidateNames) {
                    for (mime in candidateMimes) {
                        var insertedUri: Uri? = null
                        try {
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, candidateName)
                                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                            insertedUri = context.contentResolver.insert(collectionUri, values)
                            if (insertedUri != null) {
                                val os = context.contentResolver.openOutputStream(insertedUri)
                                if (os != null) {
                                    return DownloadDestination(
                                        outputStream = os,
                                        mediaStoreUri = insertedUri
                                    )
                                } else {
                                    try {
                                        context.contentResolver.delete(insertedUri, null, null)
                                    } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {
                            if (insertedUri != null) {
                                try {
                                    context.contentResolver.delete(insertedUri, null, null)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }

        val canUsePublicDownloadsDirect = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())

        val candidateDirs = buildList {
            if (canUsePublicDownloadsDirect) {
                @Suppress("DEPRECATION")
                val publicDownloads =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                add(File(publicDownloads, DOWNLOAD_SUBFOLDER))
            }
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
                add(File(it, DOWNLOAD_SUBFOLDER))
            }
            add(File(context.filesDir, DOWNLOAD_SUBFOLDER))
        }

        for (targetDir in candidateDirs) {
            try {
                if (!targetDir.exists()) targetDir.mkdirs()
                if (!targetDir.exists() || !targetDir.isDirectory) continue

                var destFile = File(targetDir, safeName)
                var counter = 1
                while (destFile.exists()) {
                    val newName = if (ext.isNotEmpty()) "$stem ($counter).$ext" else "$stem ($counter)"
                    destFile = File(targetDir, newName)
                    counter++
                }
                return DownloadDestination(
                    outputStream = FileOutputStream(destFile),
                    legacyFile = destFile
                )
            } catch (_: Exception) {}
        }
        return null
    }

    fun finalizeDownloadDestination(
        context: Context,
        dest: DownloadDestination,
        completed: Boolean
    ) {
        if (dest.mediaStoreUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (completed) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    context.contentResolver.update(dest.mediaStoreUri, values, null, null)
                } else {
                    context.contentResolver.delete(dest.mediaStoreUri, null, null)
                }
            } catch (_: Exception) {}
        } else if (dest.legacyFile != null) {
            if (completed) {
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(dest.legacyFile.absolutePath),
                        null,
                        null
                    )
                } catch (_: Exception) {}
            } else {
                try {
                    dest.legacyFile.delete()
                } catch (_: Exception) {}
            }
        }
    }
}
