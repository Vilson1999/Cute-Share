package com.example.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.FileCategory
import com.example.model.SharedFile
import com.example.model.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object DeviceFileScanner {

    suspend fun queryDeviceFiles(context: Context): List<SharedFile> = withContext(Dispatchers.IO) {
        val fileList = mutableListOf<SharedFile>()
        val contentResolver = context.contentResolver
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        fun queryUri(collectionUri: Uri, defaultCategory: FileCategory, extraSelection: String? = null) {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
            )

            try {
                contentResolver.query(
                    collectionUri,
                    projection,
                    extraSelection,
                    null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)

                    var count = 0
                    while (cursor.moveToNext() && count < 80) {
                        val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "File_$id" else "File_$id"
                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                        val dateSecs = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                        val formattedDate = if (dateSecs > 0) dateFormat.format(Date(dateSecs * 1000L)) else "Recent"

                        val itemUri = ContentUris.withAppendedId(collectionUri, id)
                        val ext = name.substringAfterLast('.', "").uppercase()

                        val category = when (ext) {
                            "JPG", "JPEG", "PNG", "WEBP", "GIF", "HEIC", "BMP" -> FileCategory.IMAGE
                            "MP4", "MKV", "MOV", "AVI", "3GP", "WEBM" -> FileCategory.VIDEO
                            "MP3", "FLAC", "WAV", "M4A", "AAC", "OGG" -> FileCategory.AUDIO
                            "APK" -> FileCategory.APP
                            "PDF", "DOC", "DOCX", "TXT", "XLSX", "XLS", "PPTX", "PPT", "CSV" -> FileCategory.DOCUMENTS
                            "ZIP", "RAR", "7Z", "TAR", "GZ", "BZ2" -> FileCategory.ARCHIVES
                            else -> defaultCategory
                        }

                        fileList.add(
                            SharedFile(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                sizeBytes = size,
                                formattedSize = formatBytes(size),
                                category = category,
                                extension = ext.ifEmpty { "FILE" },
                                lastModified = formattedDate,
                                uriString = itemUri.toString()
                            )
                        )
                        count++
                    }
                }
            } catch (_: Exception) {
                // Ignore permission or provider errors gracefully
            }
        }

        // 1. Query device media stores
        queryUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, FileCategory.IMAGE)
        queryUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, FileCategory.VIDEO)
        queryUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, FileCategory.AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryUri(MediaStore.Downloads.EXTERNAL_CONTENT_URI, FileCategory.STORAGE)
        }

        // 2. Query Installed Applications
        try {
            val packageManager = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
            val seenPackages = mutableSetOf<String>()

            for (resolveInfo in resolveInfos) {
                try {
                    val appInfo = resolveInfo.activityInfo.applicationInfo ?: continue
                    val pkgName = appInfo.packageName ?: continue
                    if (seenPackages.contains(pkgName) || pkgName == context.packageName) continue
                    seenPackages.add(pkgName)

                    val appLabel = resolveInfo.loadLabel(packageManager).toString().ifEmpty { pkgName }
                    val sourceDir = appInfo.sourceDir
                    if (sourceDir.isNullOrEmpty()) continue

                    val apkFile = File(sourceDir)
                    if (!apkFile.exists()) continue

                    val size = apkFile.length()
                    val fileName = if (appLabel.endsWith(".apk", ignoreCase = true)) appLabel else "$appLabel.apk"

                    fileList.add(
                        SharedFile(
                            id = "app_$pkgName",
                            name = fileName,
                            sizeBytes = size,
                            formattedSize = formatBytes(size),
                            category = FileCategory.APP,
                            extension = "APK",
                            lastModified = "Installed App",
                            uriString = Uri.fromFile(apkFile).toString()
                        )
                    )
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 3. Query Downloaded APK Files in Downloads & Storage
        try {
            val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsFolder?.listFiles { file -> file.isFile && file.name.endsWith(".apk", ignoreCase = true) }?.forEach { apkFile ->
                if (fileList.none { it.name.equals(apkFile.name, ignoreCase = true) }) {
                    val size = apkFile.length()
                    fileList.add(
                        SharedFile(
                            id = "dl_apk_${apkFile.name}",
                            name = apkFile.name,
                            sizeBytes = size,
                            formattedSize = formatBytes(size),
                            category = FileCategory.APP,
                            extension = "APK",
                            lastModified = "Downloaded APK",
                            uriString = Uri.fromFile(apkFile).toString()
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        fileList
    }
}
