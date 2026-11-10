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
        val seenApkPaths = mutableSetOf<String>()
        val contentResolver = context.contentResolver
        val packageManager = context.packageManager
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
                    while (cursor.moveToNext() && count < 100) {
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
            } catch (_: Exception) {}
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
                            uriString = Uri.fromFile(apkFile).toString(),
                            packageName = pkgName,
                            isDownloadedApk = false
                        )
                    )
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Helper function to safely register discovered APK files
        fun registerApkFile(apkFile: File, forcedLocationLabel: String? = null) {
            try {
                if (!apkFile.exists() || !apkFile.isFile || apkFile.length() <= 0) return
                val canonical = try { apkFile.canonicalPath } catch (_: Exception) { apkFile.absolutePath }
                if (seenApkPaths.contains(canonical)) return
                seenApkPaths.add(canonical)

                val size = apkFile.length()
                val isSdCard = !canonical.startsWith("/storage/emulated/0")
                val location = forcedLocationLabel ?: if (isSdCard) "SD Card" else "Storage"

                val archiveInfo = try {
                    packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                } catch (_: Exception) {
                    null
                }

                val appLabel = archiveInfo?.applicationInfo?.let { appInfo ->
                    try {
                        appInfo.sourceDir = apkFile.absolutePath
                        appInfo.publicSourceDir = apkFile.absolutePath
                        appInfo.loadLabel(packageManager).toString()
                    } catch (_: Exception) {
                        null
                    }
                }?.ifBlank { null } ?: apkFile.name

                val displayName = if (appLabel.endsWith(".apk", ignoreCase = true)) appLabel else "$appLabel.apk"

                fileList.add(
                    SharedFile(
                        id = "apk_${Math.abs(canonical.hashCode())}",
                        name = displayName,
                        sizeBytes = size,
                        formattedSize = formatBytes(size),
                        category = FileCategory.APP,
                        extension = "APK",
                        lastModified = location,
                        uriString = Uri.fromFile(apkFile).toString(),
                        packageName = archiveInfo?.packageName,
                        isDownloadedApk = true
                    )
                )
            } catch (_: Exception) {}
        }

        // 3. Query MediaStore Files database across all storage volumes (including SD card)
        try {
            val filesUri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATA
            )
            val selection = "(${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.apk' OR ${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/vnd.android.package-archive')"

            contentResolver.query(filesUri, projection, selection, null, null)?.use { cursor ->
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)

                while (cursor.moveToNext()) {
                    val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                    if (!path.isNullOrEmpty()) {
                        registerApkFile(File(path))
                    } else if (idCol >= 0) {
                        val id = cursor.getLong(idCol)
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "app.apk" else "app.apk"
                        val itemUri = ContentUris.withAppendedId(filesUri, id)
                        fileList.add(
                            SharedFile(
                                id = "ms_apk_$id",
                                name = name,
                                sizeBytes = 10_000_000L,
                                formattedSize = "APK",
                                category = FileCategory.APP,
                                extension = "APK",
                                lastModified = "SD Card / Storage",
                                uriString = itemUri.toString(),
                                packageName = null,
                                isDownloadedApk = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // 4. Scan All Mounted Volumes & SD Cards Directly
        val storageRoots = mutableSetOf<File>()

        // A. Primary external storage
        try {
            Environment.getExternalStorageDirectory()?.let { storageRoots.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { storageRoots.add(it) }
        } catch (_: Exception) {}

        // B. Secondary storage volumes (SD Cards) from context.getExternalFilesDirs(null)
        try {
            context.getExternalFilesDirs(null)?.forEach { dir ->
                if (dir != null) {
                    val rootPath = dir.absolutePath.substringBefore("/Android/data")
                    val root = File(rootPath)
                    if (root.exists() && root.canRead()) {
                        storageRoots.add(root)
                    }
                }
            }
        } catch (_: Exception) {}

        // C. /storage directory mount points (e.g. /storage/1234-5678, /storage/sdcard0, etc.)
        try {
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { vol ->
                    if (vol.isDirectory && vol.name != "self" && vol.name != "emulated" && vol.canRead()) {
                        storageRoots.add(vol)
                    }
                }
            }
        } catch (_: Exception) {}

        // D. Traditional SD card mount paths
        listOf("/mnt/sdcard", "/mnt/extSdCard", "/mnt/media_rw", "/sdcard").forEach { p ->
            try {
                val f = File(p)
                if (f.exists() && f.isDirectory && f.canRead()) {
                    storageRoots.add(f)
                }
            } catch (_: Exception) {}
        }

        // Subdirectories commonly containing APKs across SD card and internal storage
        val targetSubDirs = listOf(
            "", "Download", "Downloads", "apk", "apks", "APKs", "APK", "App", "Apps",
            "Bluetooth", "Documents", "Cute Share", "CuteShare", "ShareMe", "Xender",
            "ShareIt", "Telegram", "WhatsApp", "DCIM"
        )

        for (root in storageRoots) {
            val isSdCard = !root.absolutePath.startsWith("/storage/emulated/0")
            val location = if (isSdCard) "SD Card" else "Storage"

            for (sub in targetSubDirs) {
                try {
                    val folder = if (sub.isEmpty()) root else File(root, sub)
                    if (!folder.exists() || !folder.isDirectory || !folder.canRead()) continue

                    folder.listFiles()?.forEach { f ->
                        if (f.isFile && f.name.endsWith(".apk", ignoreCase = true)) {
                            registerApkFile(f, location)
                        } else if (f.isDirectory && !f.name.startsWith(".") && f.name != "Android" && sub.isEmpty()) {
                            // Scan 1 level deeper from root
                            try {
                                f.listFiles { child -> child.isFile && child.name.endsWith(".apk", ignoreCase = true) }?.forEach { apk ->
                                    registerApkFile(apk, location)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        fileList
    }
}
