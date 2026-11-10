package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.model.SharedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Dedicated 10 MB disk storage cache for image thumbnails.
 * Stores exclusively low-quality, ultra-compact thumbnails (96x96, JPEG quality 40)
 * to maximize speed, save battery, and fit thousands of thumbnails into the 10 MB quota.
 */
object ThumbnailDiskCache {
    // Dedicated 10 MB disk storage allocation
    const val MAX_DISK_STORAGE_BYTES = 10L * 1024L * 1024L
    private const val PRUNE_HEADROOM_BYTES = 8L * 1024L * 1024L

    // Low quality compression settings: 40% quality JPEG produces tiny 2-4 KB files
    private const val LOW_QUALITY_JPEG_COMPRESSION = 40

    private fun getStorageDir(context: Context): File {
        return File(context.cacheDir, "image_thumbnails").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun getStorageKey(key: String): String {
        return "thumb_lq_" + Math.abs(key.hashCode()).toString() + ".jpg"
    }

    fun get(context: Context, key: String): Bitmap? {
        return try {
            val file = File(getStorageDir(context), getStorageKey(key))
            if (file.exists() && file.length() > 0) {
                file.setLastModified(System.currentTimeMillis())
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun put(context: Context, key: String, bitmap: Bitmap) {
        try {
            val dir = getStorageDir(context)
            val file = File(dir, getStorageKey(key))
            FileOutputStream(file).use { out ->
                // Low quality compression for maximum storage efficiency & fast loading
                bitmap.compress(Bitmap.CompressFormat.JPEG, LOW_QUALITY_JPEG_COMPRESSION, out)
            }
            pruneToQuota(dir)
        } catch (_: Exception) {}
    }

    private fun pruneToQuota(dir: File) {
        try {
            val files = dir.listFiles() ?: return
            var totalBytes = files.sumOf { it.length() }
            if (totalBytes > MAX_DISK_STORAGE_BYTES) {
                val sortedOldestFirst = files.sortedBy { it.lastModified() }
                for (file in sortedOldestFirst) {
                    val len = file.length()
                    if (file.delete()) {
                        totalBytes -= len
                    }
                    if (totalBytes <= PRUNE_HEADROOM_BYTES) break
                }
            }
        } catch (_: Exception) {}
    }
}

object ThumbnailHelper {
    private val thumbnailMemCache = LruCache<String, ImageBitmap>(150)

    // Target dimensions for low quality thumbnails (96x96 px)
    private const val LOW_QUALITY_THUMB_SIZE = 96
    private const val LOW_QUALITY_APP_ICON_SIZE = 72

    suspend fun loadImageThumbnail(context: Context, uriString: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        if (uriString.isNullOrEmpty()) return@withContext null

        // Level 1: Memory Cache
        val memCached = thumbnailMemCache.get(uriString)
        if (memCached != null) return@withContext memCached

        // Level 2: 10 MB Low Quality Disk Cache
        val diskBitmap = ThumbnailDiskCache.get(context, uriString)
        if (diskBitmap != null) {
            val imageBitmap = diskBitmap.asImageBitmap()
            thumbnailMemCache.put(uriString, imageBitmap)
            return@withContext imageBitmap
        }

        // Level 3: Decode low-quality sampled bitmap and persist to 10 MB disk cache
        try {
            val uri = Uri.parse(uriString)
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.loadThumbnail(uri, android.util.Size(LOW_QUALITY_THUMB_SIZE, LOW_QUALITY_THUMB_SIZE), null)
                } catch (_: Exception) {
                    decodeLowQualityBitmap(context, uri, LOW_QUALITY_THUMB_SIZE, LOW_QUALITY_THUMB_SIZE)
                }
            } else {
                decodeLowQualityBitmap(context, uri, LOW_QUALITY_THUMB_SIZE, LOW_QUALITY_THUMB_SIZE)
            }

            if (bitmap != null) {
                // Scale down to low-quality thumbnail if larger than target size
                val lowQualityBitmap = if (bitmap.width > LOW_QUALITY_THUMB_SIZE || bitmap.height > LOW_QUALITY_THUMB_SIZE) {
                    Bitmap.createScaledBitmap(bitmap, LOW_QUALITY_THUMB_SIZE, LOW_QUALITY_THUMB_SIZE, false)
                } else bitmap

                ThumbnailDiskCache.put(context, uriString, lowQualityBitmap)
                val imageBitmap = lowQualityBitmap.asImageBitmap()
                thumbnailMemCache.put(uriString, imageBitmap)
                imageBitmap
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadAppIcon(context: Context, packageName: String?, uriString: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = packageName ?: uriString ?: return@withContext null

        val memCached = thumbnailMemCache.get(key)
        if (memCached != null) return@withContext memCached

        val diskBitmap = ThumbnailDiskCache.get(context, key)
        if (diskBitmap != null) {
            val imageBitmap = diskBitmap.asImageBitmap()
            thumbnailMemCache.put(key, imageBitmap)
            return@withContext imageBitmap
        }

        try {
            val pm = context.packageManager
            val drawable: Drawable? = if (!packageName.isNullOrBlank()) {
                try {
                    pm.getApplicationIcon(packageName)
                } catch (_: Exception) {
                    null
                }
            } else if (!uriString.isNullOrBlank()) {
                try {
                    val rawPath = Uri.parse(uriString).path ?: uriString
                    val file = File(rawPath)
                    if (file.exists()) {
                        val pi = pm.getPackageArchiveInfo(file.absolutePath, 0)
                        pi?.applicationInfo?.let { appInfo ->
                            appInfo.sourceDir = file.absolutePath
                            appInfo.publicSourceDir = file.absolutePath
                            appInfo.loadIcon(pm)
                        }
                    } else null
                } catch (_: Exception) {
                    null
                }
            } else null

            drawable?.let { drawableToLowQualityBitmap(it, LOW_QUALITY_APP_ICON_SIZE, LOW_QUALITY_APP_ICON_SIZE) }?.let { bmp ->
                ThumbnailDiskCache.put(context, key, bmp)
                val imgBitmap = bmp.asImageBitmap()
                thumbnailMemCache.put(key, imgBitmap)
                imgBitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToLowQualityBitmap(drawable: Drawable, width: Int, height: Int): Bitmap? {
        return try {
            val w = width.coerceAtLeast(1)
            val h = height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeLowQualityBitmap(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val inputBounds = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputBounds, null, options)
            inputBounds.close()

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            // RGB_565 uses 50% less RAM than ARGB_8888 (ideal for 2GB RAM devices)
            options.inPreferredConfig = Bitmap.Config.RGB_565

            val inputDecode = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputDecode, null, options)
            inputDecode.close()
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}

@Composable
fun AsyncImageThumbnail(
    file: SharedFile,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmapState = produceState<ImageBitmap?>(initialValue = null, file.uriString) {
        value = ThumbnailHelper.loadImageThumbnail(context, file.uriString)
    }

    val bitmap = bitmapState.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFE0F2FE))
        ) {
            Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = null,
                tint = Color(0xFF0284C7),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun AsyncAppIconThumbnail(
    file: SharedFile,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmapState = produceState<ImageBitmap?>(initialValue = null, file.packageName, file.uriString) {
        value = ThumbnailHelper.loadAppIcon(context, file.packageName, file.uriString)
    }

    val bitmap = bitmapState.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(Color(0xFFDCFCE7), androidx.compose.foundation.shape.CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.Android,
                contentDescription = null,
                tint = Color(0xFF16A34A),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
