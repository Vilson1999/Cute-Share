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

    /**
     * Comprehensive scanner that queries both Internal and External Storage for files:
     * - MediaStore files across external & internal volumes (Images, Videos, Audio, Documents, Archives, Downloads)
     * - Direct file system scanning of external storage (Downloads, Documents, DCIM, Pictures, Music, CuteShare, etc.)
     * - Direct file system scanning of internal storage (filesDir, cacheDir, app storage)
     * - Installed applications (as APKs) and APK Apps found in storage
     */
    suspend fun queryDeviceFiles(context: Context): List<SharedFile> = withContext(Dispatchers.IO) {
        val fileList = mutableListOf<SharedFile>()
        val seenKeys = mutableSetOf<String>()
        val contentResolver = context.contentResolver
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        fun addFileSafely(
            name: String,
            size: Long,
            formattedDate: String,
            uriString: String,
            defaultCategory: FileCategory = FileCategory.STORAGE,
            isApkApp: Boolean = false,
            pkgName: String? = null,
            isInstalledApp: Boolean = false
        ) {
            val dedupKey = "${name.lowercase(Locale.ROOT)}_$size"
            if (seenKeys.contains(dedupKey) || seenKeys.contains(uriString)) return
            seenKeys.add(dedupKey)
            seenKeys.add(uriString)

            val ext = name.substringAfterLast('.', "").uppercase(Locale.ROOT)
            val category = when {
                isInstalledApp || isApkApp || ext == "APK" -> FileCategory.APP
                ext in listOf("JPG", "JPEG", "PNG", "WEBP", "GIF", "HEIC", "BMP", "SVG") -> FileCategory.IMAGE
                ext in listOf("MP4", "MKV", "MOV", "AVI", "3GP", "WEBM", "M4V", "FLV", "WMV") -> FileCategory.VIDEO
                ext in listOf("MP3", "FLAC", "WAV", "M4A", "AAC", "OGG", "OPUS", "WMA", "AMR") -> FileCategory.AUDIO
                ext in listOf("PDF", "DOC", "DOCX", "TXT", "XLSX", "XLS", "PPTX", "PPT", "CSV", "JSON", "XML", "HTML", "MD", "EPUB") -> FileCategory.DOCUMENTS
                ext in listOf("ZIP", "RAR", "7Z", "TAR", "GZ", "BZ2", "XZ", "ISO") -> FileCategory.ARCHIVES
                else -> defaultCategory
            }

            val finalLastModified = when {
                isInstalledApp -> "Installed App"
                category == FileCategory.APP || ext == "APK" || isApkApp -> "Apk App"
                else -> formattedDate
            }

            fileList.add(
                SharedFile(
                    id = if (isInstalledApp) "app_${pkgName ?: UUID.randomUUID()}" else UUID.randomUUID().toString(),
                    name = name,
                    sizeBytes = size,
                    formattedSize = formatBytes(size),
                    category = category,
                    extension = ext.ifEmpty { "FILE" },
                    lastModified = finalLastModified,
                    uriString = uriString,
                    packageName = pkgName,
                    isDownloadedApk = (category == FileCategory.APP || ext == "APK" || isApkApp) && !isInstalledApp
                )
            )
        }

        fun queryUri(collectionUri: Uri, defaultCategory: FileCategory, maxCount: Int = 120) {
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
                    "${MediaStore.MediaColumns.SIZE} > 0",
                    null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)

                    var count = 0
                    while (cursor.moveToNext() && count < maxCount) {
                        val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "File_$id" else "File_$id"
                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                        val dateSecs = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                        val formattedDate = if (dateSecs > 0) dateFormat.format(Date(dateSecs * 1000L)) else "Recent"

                        val itemUri = ContentUris.withAppendedId(collectionUri, id)
                        val isApk = name.endsWith(".apk", ignoreCase = true)

                        addFileSafely(
                            name = name,
                            size = size,
                            formattedDate = formattedDate,
                            uriString = itemUri.toString(),
                            defaultCategory = defaultCategory,
                            isApkApp = isApk
                        )
                        count++
                    }
                }
            } catch (_: Exception) {
                // Ignore permission or content provider errors gracefully
            }
        }

        // =========================================================================
        // 1. QUERY MEDIASTORE: EXTERNAL & INTERNAL STORAGE VOLUMES
        // =========================================================================
        // External storage MediaStore files
        queryUri(MediaStore.Files.getContentUri("external"), FileCategory.STORAGE, maxCount = 200)
        queryUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, FileCategory.IMAGE, maxCount = 150)
        queryUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, FileCategory.VIDEO, maxCount = 150)
        queryUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, FileCategory.AUDIO, maxCount = 150)

        // Internal storage MediaStore files
        try {
            queryUri(MediaStore.Files.getContentUri("internal"), FileCategory.STORAGE, maxCount = 100)
            queryUri(MediaStore.Images.Media.INTERNAL_CONTENT_URI, FileCategory.IMAGE, maxCount = 50)
            queryUri(MediaStore.Video.Media.INTERNAL_CONTENT_URI, FileCategory.VIDEO, maxCount = 50)
            queryUri(MediaStore.Audio.Media.INTERNAL_CONTENT_URI, FileCategory.AUDIO, maxCount = 50)
        } catch (_: Exception) {}

        // Download collections on Android Q+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                queryUri(MediaStore.Downloads.EXTERNAL_CONTENT_URI, FileCategory.STORAGE, maxCount = 150)
            } catch (_: Exception) {}
            try {
                queryUri(MediaStore.Downloads.INTERNAL_CONTENT_URI, FileCategory.STORAGE, maxCount = 80)
            } catch (_: Exception) {}
        }

        // =========================================================================
        // 2. QUERY INSTALLED APPLICATIONS (APKs)
        // =========================================================================
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

                    addFileSafely(
                        name = fileName,
                        size = size,
                        formattedDate = "Installed App",
                        uriString = Uri.fromFile(apkFile).toString(),
                        defaultCategory = FileCategory.APP,
                        isApkApp = false,
                        pkgName = pkgName,
                        isInstalledApp = true
                    )
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // =========================================================================
        // 3. DIRECT FILE SYSTEM SCANNING: EXTERNAL & INTERNAL STORAGE
        // =========================================================================
        fun scanDirectory(dir: File?, maxDepth: Int = 2, isInternal: Boolean = false) {
            if (dir == null || !dir.exists() || !dir.canRead()) return

            try {
                val files = dir.listFiles() ?: return
                for (file in files) {
                    if (file.isHidden || file.name.startsWith(".")) continue

                    if (file.isDirectory) {
                        // Skip system-protected Android data folders on Android 11+
                        if (file.name.equals("Android", ignoreCase = true) ||
                            file.name.equals("data", ignoreCase = true) ||
                            file.name.equals("obb", ignoreCase = true)
                        ) {
                            continue
                        }
                        if (maxDepth > 0) {
                            scanDirectory(file, maxDepth - 1, isInternal)
                        }
                    } else if (file.isFile && file.length() > 0) {
                        val isApk = file.name.endsWith(".apk", ignoreCase = true)
                        val lastMod = if (file.lastModified() > 0) {
                            dateFormat.format(Date(file.lastModified()))
                        } else if (isInternal) {
                            "Internal Storage"
                        } else {
                            "External Storage"
                        }

                        addFileSafely(
                            name = file.name,
                            size = file.length(),
                            formattedDate = lastMod,
                            uriString = Uri.fromFile(file).toString(),
                            defaultCategory = if (isInternal) FileCategory.STORAGE else FileCategory.STORAGE,
                            isApkApp = isApk
                        )
                    }
                }
            } catch (_: Exception) {
                // Ignore inaccessible directories gracefully
            }
        }

        // Direct scan External Storage
        try {
            val extRoot = Environment.getExternalStorageDirectory()
            val publicFolders = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                File(extRoot, "CuteShare"),
                File(extRoot, "Download"),
                File(extRoot, "Documents"),
                File(extRoot, "Bluetooth")
            )

            for (folder in publicFolders) {
                scanDirectory(folder, maxDepth = 2, isInternal = false)
            }

            // Also scan immediate root files of external storage
            extRoot?.listFiles { f -> f.isFile && !f.isHidden }?.forEach { f ->
                if (f.length() > 0) {
                    val isApk = f.name.endsWith(".apk", ignoreCase = true)
                    addFileSafely(
                        name = f.name,
                        size = f.length(),
                        formattedDate = dateFormat.format(Date(f.lastModified())),
                        uriString = Uri.fromFile(f).toString(),
                        defaultCategory = FileCategory.STORAGE,
                        isApkApp = isApk
                    )
                }
            }
        } catch (_: Exception) {}

        // Direct scan Removable SD Cards / External Volumes
        try {
            val extFilesDirs = context.getExternalFilesDirs(null)
            for (fDir in extFilesDirs) {
                if (fDir != null) {
                    scanDirectory(fDir, maxDepth = 2, isInternal = false)
                }
            }
        } catch (_: Exception) {}

        // Direct scan Internal Storage
        try {
            scanDirectory(context.filesDir, maxDepth = 3, isInternal = true)
            scanDirectory(context.cacheDir, maxDepth = 2, isInternal = true)
            scanDirectory(context.getExternalFilesDir(null), maxDepth = 3, isInternal = true)
        } catch (_: Exception) {}

        fileList
    }
}
