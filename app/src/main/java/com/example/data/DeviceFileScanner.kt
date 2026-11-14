package com.example.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.example.model.FileCategory
import com.example.model.SharedFile
import com.example.model.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object DeviceFileScanner {

    internal fun classifyCategory(
        name: String,
        defaultCategory: FileCategory = FileCategory.STORAGE,
        isApkApp: Boolean = false,
        isInstalledApp: Boolean = false
    ): FileCategory {
        val ext = name.substringAfterLast('.', "").uppercase(Locale.ROOT)
        return when {
            isInstalledApp || isApkApp || ext == "APK" -> FileCategory.APP
            ext in IMAGE_EXTENSIONS -> FileCategory.IMAGE
            ext in VIDEO_EXTENSIONS -> FileCategory.VIDEO
            ext in AUDIO_EXTENSIONS -> FileCategory.AUDIO
            ext in DOC_EXTENSIONS -> FileCategory.DOCUMENTS
            ext in ARCHIVE_EXTENSIONS -> FileCategory.ARCHIVES
            else -> defaultCategory
        }
    }

    private val IMAGE_EXTENSIONS = setOf("JPG", "JPEG", "PNG", "WEBP", "GIF", "HEIC", "HEIF", "BMP", "SVG")
    private val VIDEO_EXTENSIONS = setOf("MP4", "MKV", "MOV", "AVI", "3GP", "WEBM", "M4V", "FLV", "WMV", "TS")
    private val AUDIO_EXTENSIONS = setOf("MP3", "FLAC", "WAV", "M4A", "AAC", "OGG", "OPUS", "WMA", "AMR")
    private val DOC_EXTENSIONS = setOf("PDF", "DOC", "DOCX", "TXT", "XLSX", "XLS", "PPTX", "PPT", "CSV", "JSON", "XML", "HTML", "MD", "EPUB")
    private val ARCHIVE_EXTENSIONS = setOf("ZIP", "RAR", "7Z", "TAR", "GZ", "BZ2", "XZ", "ISO")

    private val SKIPPED_DIR_NAMES = setOf(
        ".thumb_cache",
        "thumb_cache",
        ".thumbnails",
        ".trash",
        "lost.dir",
        "system volume information",
        "\$recycle.bin"
    )

    suspend fun queryDeviceFiles(
        context: Context,
        onBatchUpdate: (suspend (List<SharedFile>) -> Unit)? = null
    ): List<SharedFile> = withContext(Dispatchers.IO) {
        val fileList = mutableListOf<SharedFile>()
        val seenInstalledPackages = HashSet<String>()
        val seenStoragePaths = HashSet<String>()
        val seenUris = HashSet<String>()
        val seenFileSignatures = HashSet<String>()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        var lastEmittedCount = 0

        suspend fun emitIfNeeded(force: Boolean = false) {
            if (onBatchUpdate == null) return
            val size = fileList.size
            if (force || size - lastEmittedCount >= 40) {
                lastEmittedCount = size
                onBatchUpdate(fileList.toList())
            }
        }

        fun addFileSafely(
            name: String,
            size: Long,
            formattedDate: String,
            uriString: String,
            canonicalPath: String? = null,
            defaultCategory: FileCategory = FileCategory.STORAGE,
            isApkApp: Boolean = false,
            pkgName: String? = null,
            isInstalledApp: Boolean = false
        ) {
            if (name.isBlank() || size <= 0L) return

            if (isInstalledApp) {
                val pkgKey = pkgName ?: uriString
                if (!seenInstalledPackages.add(pkgKey)) return
            } else {
                if (!seenUris.add(uriString)) return
                if (!canonicalPath.isNullOrBlank()) {
                    if (!seenStoragePaths.add(canonicalPath)) return
                }
                val isApk = isApkApp || name.endsWith(".apk", ignoreCase = true)
                val sigKey = if (isApk && !canonicalPath.isNullOrBlank()) {
                    // Keep distinct APK files across different folders (e.g., SD card vs Downloads)
                    "apk_path:$canonicalPath"
                } else {
                    "storage:${name.lowercase(Locale.ROOT)}_$size"
                }
                if (!seenFileSignatures.add(sigKey)) return
            }

            val ext = name.substringAfterLast('.', "").uppercase(Locale.ROOT)
            val category = classifyCategory(
                name = name,
                defaultCategory = defaultCategory,
                isApkApp = isApkApp,
                isInstalledApp = isInstalledApp
            )

            val finalLastModified = when {
                isInstalledApp -> "Installed App"
                category == FileCategory.APP || ext == "APK" || isApkApp -> "Apk App"
                else -> formattedDate
            }

            val stableId = when {
                isInstalledApp -> "app_${pkgName ?: UUID.randomUUID()}"
                !canonicalPath.isNullOrBlank() -> "file_${canonicalPath.hashCode()}_${size}"
                else -> "uri_${uriString.hashCode()}_${size}"
            }

            fileList.add(
                SharedFile(
                    id = stableId,
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

        // =========================================================================
        // 1. DIRECT FILE SYSTEM ROOTS (INTERNAL + REMOVABLE SD CARDS + USB OTG)
        // =========================================================================
        val storageRoots = discoverAllStorageRoots(context)

        // =========================================================================
        // 2. INSTALLED APPLICATIONS (FAST INITIAL EMIT)
        // =========================================================================
        try {
            val packageManager = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)

            for (resolveInfo in resolveInfos) {
                currentCoroutineContext().ensureActive()
                try {
                    val appInfo = resolveInfo.activityInfo.applicationInfo ?: continue
                    val pkgName = appInfo.packageName ?: continue
                    if (pkgName == context.packageName) continue

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
        emitIfNeeded(force = true)

        // =========================================================================
        // 3. RECURSIVE DIRECT FILE SYSTEM SCANNING ACROSS ALL VOLUMES & SD CARDS
        // =========================================================================
        val visitedDirs = HashSet<String>()

        suspend fun scanDirectoryRecursive(
            dir: File?,
            maxDepth: Int,
            isSdCard: Boolean,
            isInternal: Boolean
        ) {
            if (dir == null || !dir.exists() || !dir.isDirectory || !dir.canRead()) return
            currentCoroutineContext().ensureActive()

            val canonicalDir = try {
                dir.canonicalPath
            } catch (_: Exception) {
                dir.absolutePath
            }
            if (!visitedDirs.add(canonicalDir)) return

            val children = try {
                dir.listFiles()
            } catch (_: Exception) {
                null
            } ?: return

            for (file in children) {
                currentCoroutineContext().ensureActive()
                val name = file.name
                if (name.startsWith(".") || file.isHidden) continue

                if (file.isDirectory) {
                    val lower = name.lowercase(Locale.ROOT)
                    if (lower in SKIPPED_DIR_NAMES) continue
                    // Skip OS-blocked Android/data and Android/obb, but KEEP Android/media (WhatsApp, Telegram, etc.)
                    if ((lower == "data" || lower == "obb") &&
                        file.parentFile?.name?.equals("Android", ignoreCase = true) == true
                    ) {
                        continue
                    }
                    if (maxDepth > 0) {
                        scanDirectoryRecursive(file, maxDepth - 1, isSdCard, isInternal)
                    }
                } else if (file.isFile) {
                    val size = file.length()
                    if (size <= 0L) continue
                    val isApk = name.endsWith(".apk", ignoreCase = true)
                    val canonicalFile = try {
                        file.canonicalPath
                    } catch (_: Exception) {
                        file.absolutePath
                    }
                    val lastMod = if (file.lastModified() > 0L) {
                        dateFormat.format(Date(file.lastModified()))
                    } else if (isSdCard) {
                        "SD Card"
                    } else if (isInternal) {
                        "Internal Storage"
                    } else {
                        "External Storage"
                    }

                    addFileSafely(
                        name = name,
                        size = size,
                        formattedDate = lastMod,
                        uriString = Uri.fromFile(file).toString(),
                        canonicalPath = canonicalFile,
                        defaultCategory = FileCategory.STORAGE,
                        isApkApp = isApk
                    )
                    emitIfNeeded(force = false)
                }
            }
        }

        for ((root, isSdCard) in storageRoots) {
            scanDirectoryRecursive(
                dir = root,
                maxDepth = 16,
                isSdCard = isSdCard,
                isInternal = false
            )
        }
        emitIfNeeded(force = true)

        // Internal app storage fallback
        try {
            scanDirectoryRecursive(context.filesDir, maxDepth = 6, isSdCard = false, isInternal = true)
            scanDirectoryRecursive(context.cacheDir, maxDepth = 4, isSdCard = false, isInternal = true)
            scanDirectoryRecursive(context.getExternalFilesDir(null), maxDepth = 6, isSdCard = false, isInternal = true)
        } catch (_: Exception) {}

        // =========================================================================
        // 4. MEDIASTORE QUERIES ACROSS ALL EXTERNAL VOLUMES (APKs FIRST, THEN MEDIA)
        // =========================================================================
        val contentResolver = context.contentResolver
        val volumeNames = LinkedHashSet<String>()
        volumeNames.add("external")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                volumeNames.addAll(MediaStore.getExternalVolumeNames(context))
            } catch (_: Exception) {}
        }

        suspend fun queryMediaStoreUri(
            collectionUri: Uri,
            defaultCategory: FileCategory,
            selection: String = "${MediaStore.MediaColumns.SIZE} > 0",
            selectionArgs: Array<String>? = null,
            maxCount: Int = 5000
        ) {
            val projection = mutableListOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                projection.add(MediaStore.MediaColumns.DATA)
            } else {
                @Suppress("DEPRECATION")
                projection.add(MediaStore.MediaColumns.DATA)
            }

            try {
                contentResolver.query(
                    collectionUri,
                    projection.toTypedArray(),
                    selection,
                    selectionArgs,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    @Suppress("DEPRECATION")
                    val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

                    var count = 0
                    while (cursor.moveToNext() && count < maxCount) {
                        currentCoroutineContext().ensureActive()
                        val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "File_$id" else "File_$id"
                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                        val dateSecs = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                        val formattedDate = if (dateSecs > 0) dateFormat.format(Date(dateSecs * 1000L)) else "Recent"
                        val dataPath = if (dataCol >= 0) cursor.getString(dataCol) else null

                        val directFile = dataPath?.takeIf { it.isNotBlank() }?.let { File(it) }
                            ?.takeIf { it.exists() && it.isFile && it.canRead() }
                        val canonicalPath = directFile?.let {
                            try { it.canonicalPath } catch (_: Exception) { it.absolutePath }
                        }
                        val itemUri = if (directFile != null) {
                            Uri.fromFile(directFile)
                        } else {
                            ContentUris.withAppendedId(collectionUri, id)
                        }
                        val isApk = name.endsWith(".apk", ignoreCase = true)

                        addFileSafely(
                            name = name,
                            size = size,
                            formattedDate = formattedDate,
                            uriString = itemUri.toString(),
                            canonicalPath = canonicalPath,
                            defaultCategory = defaultCategory,
                            isApkApp = isApk
                        )
                        count++
                        emitIfNeeded(force = false)
                    }
                }
            } catch (_: Exception) {
                // Ignore permission or provider errors gracefully
            }
        }

        val apkSelection =
            "(${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.apk' OR ${MediaStore.MediaColumns.MIME_TYPE} = ?) AND ${MediaStore.MediaColumns.SIZE} > 0"
        val apkArgs = arrayOf("application/vnd.android.package-archive")

        for (vol in volumeNames) {
            // Dedicated APK query first with no low cutoff
            queryMediaStoreUri(
                collectionUri = MediaStore.Files.getContentUri(vol),
                defaultCategory = FileCategory.APP,
                selection = apkSelection,
                selectionArgs = apkArgs,
                maxCount = Int.MAX_VALUE
            )
            queryMediaStoreUri(MediaStore.Images.Media.getContentUri(vol), FileCategory.IMAGE)
            queryMediaStoreUri(MediaStore.Video.Media.getContentUri(vol), FileCategory.VIDEO)
            queryMediaStoreUri(MediaStore.Audio.Media.getContentUri(vol), FileCategory.AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    queryMediaStoreUri(MediaStore.Downloads.getContentUri(vol), FileCategory.STORAGE)
                } catch (_: Exception) {}
            }
            queryMediaStoreUri(MediaStore.Files.getContentUri(vol), FileCategory.STORAGE)
        }

        emitIfNeeded(force = true)
        fileList
    }

    internal fun discoverAllStorageRoots(context: Context): List<Pair<File, Boolean>> {
        val roots = mutableListOf<Pair<File, Boolean>>()
        val seenCanonical = HashSet<String>()

        fun addRoot(dir: File?, isSdCard: Boolean) {
            if (dir == null || !dir.exists() || !dir.isDirectory || !dir.canRead()) return
            val canonical = try {
                dir.canonicalPath
            } catch (_: Exception) {
                dir.absolutePath
            }
            if (seenCanonical.add(canonical)) {
                roots.add(Pair(dir, isSdCard))
            }
        }

        // 1. StorageManager storageVolumes (covers internal + all removable SD cards & USB drives)
        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            storageManager?.storageVolumes?.forEach { vol ->
                val dir: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    vol.directory
                } else {
                    try {
                        val method = vol.javaClass.getMethod("getPathFile")
                        method.invoke(vol) as? File
                    } catch (_: Exception) {
                        try {
                            val pathMethod = vol.javaClass.getMethod("getPath")
                            (pathMethod.invoke(vol) as? String)?.let { File(it) }
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                addRoot(dir, vol.isRemovable)
            }
        } catch (_: Exception) {}

        // 2. Primary external storage
        try {
            @Suppress("DEPRECATION")
            addRoot(Environment.getExternalStorageDirectory(), isSdCard = false)
        } catch (_: Exception) {}

        // 3. Direct scan of /storage and /mnt/media_rw mounts
        listOf("/storage", "/mnt/media_rw").forEach { mountParentPath ->
            try {
                val parent = File(mountParentPath)
                if (parent.exists() && parent.canRead()) {
                    parent.listFiles()?.forEach { candidate ->
                        if (candidate.isDirectory && candidate.canRead() &&
                            !candidate.name.equals("emulated", ignoreCase = true) &&
                            !candidate.name.equals("self", ignoreCase = true) &&
                            !candidate.name.equals("knox", ignoreCase = true)
                        ) {
                            addRoot(candidate, isSdCard = true)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. Fallback via getExternalFilesDirs climbing out of .../Android/data/...
        try {
            val extDirs = context.getExternalFilesDirs(null)
            for (fDir in extDirs) {
                if (fDir == null) continue
                val abs = fDir.absolutePath
                val androidIdx = abs.indexOf("/Android/")
                val volumeRoot = if (androidIdx > 0) File(abs.substring(0, androidIdx)) else null
                if (volumeRoot != null) {
                    val isSd = !abs.contains("/storage/emulated/", ignoreCase = true)
                    addRoot(volumeRoot, isSdCard = isSd)
                }
            }
        } catch (_: Exception) {}

        return roots
    }
}
