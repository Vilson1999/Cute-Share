package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.example.data.ThumbnailDiskCache
import com.example.model.SharedFile
import com.example.ui.icons.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ThumbnailHelper {
    // 2 MB in-memory LRU cache of low-quality thumbnails to prevent OOM on low-RAM devices
    private val memoryCache = object : LruCache<String, ImageBitmap>(2 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.asAndroidBitmap().allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    suspend fun loadImageThumbnail(context: Context, uriString: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        if (uriString.isNullOrEmpty()) return@withContext null
        val cacheKey = "img:$uriString"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val diskCache = ThumbnailDiskCache.getInstance(context)
        diskCache.getBitmap(cacheKey, allowAlpha = false)?.let { diskBitmap ->
            return@withContext diskBitmap.asImageBitmap().also { memoryCache.put(cacheKey, it) }
        }

        try {
            val uri = uriString.toUri()
            val maxDim = ThumbnailDiskCache.MAX_IMAGE_THUMB_PX
            val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(maxDim, maxDim), null)
                } catch (_: Exception) {
                    decodeSampledBitmap(context, uri, maxDim, maxDim)
                }
            } else {
                decodeSampledBitmap(context, uri, maxDim, maxDim)
            } ?: return@withContext null

            val lowQualityBitmap = diskCache.putBitmap(cacheKey, rawBitmap, allowAlpha = false)
            lowQualityBitmap.asImageBitmap().also { memoryCache.put(cacheKey, it) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadAppIcon(context: Context, packageName: String?, uriString: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        val rawKey = packageName ?: uriString ?: return@withContext null
        val cacheKey = "app:$rawKey"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val diskCache = ThumbnailDiskCache.getInstance(context)
        diskCache.getBitmap(cacheKey, allowAlpha = true)?.let { diskBitmap ->
            return@withContext diskBitmap.asImageBitmap().also { memoryCache.put(cacheKey, it) }
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
                    val rawPath = uriString.toUri().path ?: uriString
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

            val rawBitmap = drawable?.let { drawableToLowResBitmap(it) } ?: return@withContext null
            val lowQualityBitmap = diskCache.putBitmap(cacheKey, rawBitmap, allowAlpha = true)
            lowQualityBitmap.asImageBitmap().also { memoryCache.put(cacheKey, it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToLowResBitmap(drawable: Drawable): Bitmap? {
        return try {
            val maxDim = ThumbnailDiskCache.MAX_APP_ICON_PX
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return ThumbnailDiskCache.downscaleIfNeeded(drawable.bitmap, maxDim)
            }
            val w = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(maxDim)
            val h = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(maxDim)
            val bitmap = createBitmap(w, h)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { inputBounds ->
                BitmapFactory.decodeStream(inputBounds, null, options)
            } ?: return null

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            // RGB_565 uses 50% less RAM than ARGB_8888 (ideal for low-quality thumbnails)
            options.inPreferredConfig = Bitmap.Config.RGB_565

            context.contentResolver.openInputStream(uri)?.use { inputDecode ->
                BitmapFactory.decodeStream(inputDecode, null, options)
            }
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
                imageVector = AppIcons.Image,
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
            modifier = modifier.background(Color(0xFFDCFCE7), CircleShape)
        ) {
            Icon(
                imageVector = AppIcons.Android,
                contentDescription = null,
                tint = Color(0xFF16A34A),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
