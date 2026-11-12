package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Persistent 10 MB LRU disk storage for low-quality thumbnails.
 *
 * All cached images are downscaled and aggressively compressed (low JPEG/WebP quality)
 * so thousands of thumbnails fit inside the 10 MB storage cap while keeping RAM & disk tiny.
 */
class ThumbnailDiskCache(private val cacheDir: File) {

    companion object {
        const val MAX_CACHE_BYTES: Long = 20L * 1024L * 1024L // 20 MB strict limit for fast browsing
        const val LOW_QUALITY_PERCENT: Int = 70 // 2x quality compression
        const val MAX_IMAGE_THUMB_PX: Int = 256 // 2x thumbnail resolution
        const val MAX_APP_ICON_PX: Int = 144 // 2x app icon resolution
        private const val CACHE_DIR_NAME = ".thumb_cache"
        private const val ENTRY_SUFFIX = ".thumb"

        @Volatile
        private var instance: ThumbnailDiskCache? = null

        fun getInstance(context: Context): ThumbnailDiskCache {
            return instance ?: synchronized(this) {
                instance ?: ThumbnailDiskCache(
                    File(context.applicationContext.cacheDir, CACHE_DIR_NAME)
                ).also { instance = it }
            }
        }

        internal fun downscaleIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
            val w = bitmap.width
            val h = bitmap.height
            val longest = max(w, h)
            if (longest <= maxDim || longest <= 0) return bitmap
            val scale = maxDim.toFloat() / longest.toFloat()
            val targetW = (w * scale).roundToInt().coerceAtLeast(1)
            val targetH = (h * scale).roundToInt().coerceAtLeast(1)
            return bitmap.scale(targetW, targetH)
        }
    }

    private val lock = Any()

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun getBitmap(key: String, allowAlpha: Boolean): Bitmap? {
        val bytes = getBytes(key) ?: return null
        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = if (allowAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (decoded == null) {
                remove(key)
            }
            decoded
        } catch (_: Exception) {
            remove(key)
            null
        }
    }

    /**
     * Downscales and compresses [source] to a low-quality thumbnail and stores it
     * within the 10 MB LRU budget. Returns the low-quality bitmap for immediate UI display.
     */
    fun putBitmap(key: String, source: Bitmap, allowAlpha: Boolean): Bitmap {
        val maxDim = if (allowAlpha) MAX_APP_ICON_PX else MAX_IMAGE_THUMB_PX
        val scaled = downscaleIfNeeded(source, maxDim)
        val lowQualityBitmap = if (!allowAlpha && scaled.config != Bitmap.Config.RGB_565) {
            try {
                scaled.copy(Bitmap.Config.RGB_565, false) ?: scaled
            } catch (_: Exception) {
                scaled
            }
        } else {
            scaled
        }

        val tempFile = File(cacheDir, "${hashKey(key)}.tmp")
        val targetFile = fileForKey(key)

        synchronized(lock) {
            try {
                if (!cacheDir.exists()) cacheDir.mkdirs()
                FileOutputStream(tempFile).use { out ->
                    val format = if (allowAlpha) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP
                        }
                    } else {
                        Bitmap.CompressFormat.JPEG
                    }
                    lowQualityBitmap.compress(format, LOW_QUALITY_PERCENT, out)
                    out.flush()
                }

                if (tempFile.exists() && tempFile.length() > 0L && tempFile.length() <= MAX_CACHE_BYTES) {
                    if (targetFile.exists()) targetFile.delete()
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                    targetFile.setLastModified(System.currentTimeMillis())
                    evictToCapLocked()
                } else {
                    tempFile.delete()
                }
            } catch (_: Exception) {
                tempFile.delete()
            }
        }

        return lowQualityBitmap
    }

    fun getBytes(key: String): ByteArray? = synchronized(lock) {
        val file = fileForKey(key)
        if (!file.exists() || !file.isFile || file.length() <= 0L) return null
        return try {
            file.setLastModified(System.currentTimeMillis())
            file.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    fun putBytes(key: String, data: ByteArray): Boolean = synchronized(lock) {
        if (data.isEmpty() || data.size.toLong() > MAX_CACHE_BYTES) return false
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val tempFile = File(cacheDir, "${hashKey(key)}.tmp")
        val targetFile = fileForKey(key)
        return try {
            tempFile.writeBytes(data)
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            targetFile.setLastModified(System.currentTimeMillis())
            evictToCapLocked()
            true
        } catch (_: Exception) {
            tempFile.delete()
            false
        }
    }

    fun remove(key: String): Unit = synchronized(lock) {
        val file = fileForKey(key)
        if (file.exists()) {
            file.delete()
        }
    }

    fun currentSizeBytes(): Long = synchronized(lock) {
        if (!cacheDir.exists()) return 0L
        return cacheDir.listFiles { f -> f.isFile && f.name.endsWith(ENTRY_SUFFIX) }
            ?.sumOf { it.length() } ?: 0L
    }

    fun entryCount(): Int = synchronized(lock) {
        if (!cacheDir.exists()) return 0
        return cacheDir.listFiles { f -> f.isFile && f.name.endsWith(ENTRY_SUFFIX) }?.size ?: 0
    }

    fun clear(): Unit = synchronized(lock) {
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun evictToCapLocked() {
        val entries = cacheDir.listFiles { f -> f.isFile && f.name.endsWith(ENTRY_SUFFIX) }
            ?.toMutableList() ?: return
        var totalSize = entries.sumOf { it.length() }
        if (totalSize <= MAX_CACHE_BYTES) return

        // Delete least-recently-used (oldest lastModified) thumbnails first
        entries.sortBy { it.lastModified() }
        for (file in entries) {
            if (totalSize <= MAX_CACHE_BYTES) break
            val len = file.length()
            if (file.delete()) {
                totalSize -= len
            }
        }
    }

    private fun fileForKey(key: String): File = File(cacheDir, "${hashKey(key)}$ENTRY_SUFFIX")

    private fun hashKey(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (b in digest) {
                val hex = (b.toInt() and 0xFF).toString(16)
                if (hex.length == 1) append('0')
                append(hex)
            }
        }
    }
}
