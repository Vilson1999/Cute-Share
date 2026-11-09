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
     * Comprehensive scanner that queries both Internal Storage and Removable SD Card Storage:
     * - MediaStore across ALL external volumes (primary storage + external SD cards) via MediaStore.getExternalVolumeNames
     * - Direct file system scanning of external storage & physical SD card mounts (/storage/XXXX-XXXX)
     * - Direct file system scanning of internal storage (filesDir, cacheDir, app storage)
     * - Installed applications (as APKs) and APK Apps found in storage & SD card
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

        fun queryUri(collectionUri: Uri, defaultCategory: FileCategory, maxCount: Int = 150) {
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
        // 1. QUERY MEDIASTORE: ALL EXTERNAL VOLUMES (INTERNAL + REMOVABLE SD CARDS)
        // =========================================================================
        val volumeNames = mutableSetOf<String>()
        volumeNames.add("external")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                volumeNames.addAll(MediaStore.getExternalVolumeNames(context))
            } catch (_: Exception) {}
        }

        for (vol in volumeNames) {
            try {
                queryUri(MediaStore.Images.Media.getContentUri(vol), FileCategory.IMAGE, maxCount = 150)
                queryUri(MediaStore.Video.Media.getContentUri(vol), FileCategory.VIDEO, maxCount = 150)
                queryUri(MediaStore.Audio.Media.getContentUri(vol), FileCategory.AUDIO, maxCount = 150)
                queryUri(MediaStore.Files.getContentUri(vol), FileCategory.STORAGE, maxCount = 200)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        queryUri(MediaStore.Downloads.getContentUri(vol), FileCategory.STORAGE, maxCount = 150)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }

        // Internal system volume MediaStore query
        try {
            queryUri(MediaStore.Files.getContentUri("internal"), FileCategory.STORAGE, maxCount = 80)
            queryUri(MediaStore.Images.Media.INTERNAL_CONTENT_URI, FileCategory.IMAGE, maxCount = 50)
            queryUri(MediaStore.Video.Media.INTERNAL_CONTENT_URI, FileCategory.VIDEO, maxCount = 50)
            queryUri(MediaStore.Audio.Media.INTERNAL_CONTENT_URI, FileCategory.AUDIO, maxCount = 50)
        } catch (_: Exception) {}

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
        // 3. DIRECT FILE SYSTEM SCANNING: DISCOVER SD CARDS AND STORAGE ROOTS
        // =========================================================================
        fun scanDirectory(dir: File?, maxDepth: Int = 2, isSdCard: Boolean = false, isInternal: Boolean = false) {
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
                            scanDirectory(file, maxDepth - 1, isSdCard, isInternal)
                        }
                    } else if (file.isFile && file.length() > 0) {
                        val isApk = file.name.endsWith(".apk", ignoreCase = true)
                        val lastMod = if (file.lastModified() > 0) {
                            dateFormat.format(Date(file.lastModified()))
                        } else if (isSdCard) {
                            "SD Card"
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
                            defaultCategory = FileCategory.STORAGE,
                            isApkApp = isApk
                        )
                    }
                }
            } catch (_: Exception) {
                // Ignore inaccessible directories gracefully
            }
        }

        // Collect all distinct storage roots: Primary Storage + Removable SD Cards
        val storageRoots = mutableListOf<Pair<File, Boolean>>() // Pair of File and isSdCard

        // Primary external storage
        try {
            val primaryRoot = Environment.getExternalStorageDirectory()
            if (primaryRoot != null && primaryRoot.exists() && primaryRoot.canRead()) {
                storageRoots.add(Pair(primaryRoot, false))
            }
        } catch (_: Exception) {}

        // Removable SD cards in /storage/
        try {
            val storageParent = File("/storage")
            if (storageParent.exists() && storageParent.canRead()) {
                storageParent.listFiles()?.forEach { candidate ->
                    if (candidate.isDirectory && candidate.canRead() &&
                        !candidate.name.equals("emulated", ignoreCase = true) &&
                        !candidate.name.equals("self", ignoreCase = true) &&
                        !candidate.name.equals("knox", ignoreCase = true)
                    ) {
                        if (storageRoots.none { it.first.absolutePath == candidate.absolutePath }) {
                            storageRoots.add(Pair(candidate, true))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback SD card discovery through getExternalFilesDirs
        try {
            val extDirs = context.getExternalFilesDirs(null)
            for (fDir in extDirs) {
                if (fDir != null) {
                    var curr: File? = fDir
                    while (curr != null && curr.parentFile != null && curr.parentFile?.name != "storage") {
                        curr = curr.parentFile
                    }
                    if (curr != null && curr.exists() && curr.canRead()) {
                        if (storageRoots.none { it.first.absolutePath == curr?.absolutePath }) {
                            val isSd = !curr.name.equals("emulated", ignoreCase = true) && !curr.name.equals("self", ignoreCase = true)
                            storageRoots.add(Pair(curr, isSd))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Scan all discovered storage roots (Primary and SD Cards)
        for ((root, isSdCard) in storageRoots) {
            val subFolders = listOf(
                File(root, "Download"),
                File(root, "Downloads"),
                File(root, "DCIM"),
                File(root, "Pictures"),
                File(root, "Music"),
                File(root, "Movies"),
                File(root, "Documents"),
                File(root, "CuteShare"),
                File(root, "Bluetooth")
            )

            for (folder in subFolders) {
                scanDirectory(folder, maxDepth = 2, isSdCard = isSdCard, isInternal = false)
            }

            // Also scan immediate root files of storage / SD card
            try {
                root.listFiles { f -> f.isFile && !f.isHidden }?.forEach { f ->
                    if (f.length() > 0) {
                        val isApk = f.name.endsWith(".apk", ignoreCase = true)
                        addFileSafely(
                            name = f.name,
                            size = f.length(),
                            formattedDate = if (f.lastModified() > 0) dateFormat.format(Date(f.lastModified())) else if (isSdCard) "SD Card" else "External Storage",
                            uriString = Uri.fromFile(f).toString(),
                            defaultCategory = FileCategory.STORAGE,
                            isApkApp = isApk
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        // Direct scan Internal App Storage
        try {
            scanDirectory(context.filesDir, maxDepth = 3, isSdCard = false, isInternal = true)
            scanDirectory(context.cacheDir, maxDepth = 2, isSdCard = false, isInternal = true)
            scanDirectory(context.getExternalFilesDir(null), maxDepth = 3, isSdCard = false, isInternal = true)
        } catch (_: Exception) {}

        fileList
    }
}
