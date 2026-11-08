package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.FileCategory
import com.example.model.SharedFile
import com.example.model.formatBytes
import java.util.UUID

object IntentFileResolver {

    fun resolveFilesFromIntent(context: Context, intent: Intent): List<SharedFile> {
        val uris = mutableListOf<Uri>()

        if (intent.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: intent.data
            if (uri != null) uris.add(uri)
        } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (list != null) uris.addAll(list)
        }

        // Also check ClipData
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val itemUri = clipData.getItemAt(i).uri
                if (itemUri != null && !uris.contains(itemUri)) {
                    uris.add(itemUri)
                }
            }
        }

        return uris.map { uri ->
            var displayName = "Shared_File_${System.currentTimeMillis()}"
            var size = 1_048_576L

            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            displayName = it.getString(nameIndex) ?: displayName
                        }
                        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1) {
                            val queriedSize = it.getLong(sizeIndex)
                            if (queriedSize > 0) size = queriedSize
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback to last path segment
                uri.lastPathSegment?.let { displayName = it }
            }

            val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
            val ext = displayName.substringAfterLast('.', "").uppercase()

            val category = when {
                mimeType?.startsWith("image/") == true || listOf("JPG", "JPEG", "PNG", "WEBP", "GIF", "DNG").contains(ext) -> FileCategory.IMAGE
                mimeType?.startsWith("video/") == true || listOf("MP4", "MKV", "MOV", "AVI", "3GP").contains(ext) -> FileCategory.VIDEO
                mimeType?.startsWith("audio/") == true || listOf("MP3", "FLAC", "WAV", "M4A", "AAC").contains(ext) -> FileCategory.AUDIO
                ext == "APK" -> FileCategory.APP
                listOf("PDF", "DOC", "DOCX", "TXT", "XLS", "XLSX", "PPT", "PPTX").contains(ext) -> FileCategory.DOCUMENTS
                listOf("ZIP", "RAR", "7Z", "TAR", "GZ").contains(ext) -> FileCategory.ARCHIVES
                else -> FileCategory.STORAGE
            }

            SharedFile(
                id = UUID.randomUUID().toString(),
                name = displayName,
                sizeBytes = size,
                formattedSize = formatBytes(size),
                category = category,
                extension = ext.ifEmpty { "FILE" },
                lastModified = "Shared from external app",
                uriString = uri.toString()
            )
        }
    }
}
