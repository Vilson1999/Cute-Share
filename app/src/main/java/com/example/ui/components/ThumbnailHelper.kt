package com.example.ui.components

import android.content.Context
import android.content.pm.PackageManager
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

object ThumbnailHelper {
    private val thumbnailCache = LruCache<String, ImageBitmap>(120)

    suspend fun loadImageThumbnail(context: Context, uriString: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        if (uriString.isNullOrEmpty()) return@withContext null
        val cached = thumbnailCache.get(uriString)
        if (cached != null) return@withContext cached

        try {
            val uri = Uri.parse(uriString)
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.loadThumbnail(uri, android.util.Size(180, 180), null)
                } catch (_: Exception) {
                    decodeSampledBitmap(context, uri, 180, 180)
                }
            } else {
                decodeSampledBitmap(context, uri, 180, 180)
            }
            bitmap?.asImageBitmap()?.also { thumbnailCache.put(uriString, it) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadAppIcon(context: Context, packageName: String?, uriString: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = packageName ?: uriString ?: return@withContext null
        val cached = thumbnailCache.get(key)
        if (cached != null) return@withContext cached

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

            drawable?.let { drawableToBitmap(it) }?.asImageBitmap()?.also {
                thumbnailCache.put(key, it)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return drawable.bitmap
            }
            val w = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(144)
            val h = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(144)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
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
