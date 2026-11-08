package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class FileCategory(
    val title: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color
) {
    IMAGE(
        title = "Image",
        icon = Icons.Rounded.Image,
        containerColor = Color(0xFFE0F2FE),
        contentColor = Color(0xFF0284C7)
    ),
    VIDEO(
        title = "Video",
        icon = Icons.Rounded.Videocam,
        containerColor = Color(0xFFF3E8FF),
        contentColor = Color(0xFF9333EA)
    ),
    AUDIO(
        title = "Audio",
        icon = Icons.Rounded.AudioFile,
        containerColor = Color(0xFFFEF3C7),
        contentColor = Color(0xFFD97706)
    ),
    APP(
        title = "App",
        icon = Icons.Rounded.Apps,
        containerColor = Color(0xFFDCFCE7),
        contentColor = Color(0xFF16A34A)
    ),
    DOCUMENTS(
        title = "Documents",
        icon = Icons.Rounded.Description,
        containerColor = Color(0xFFFFE4E6),
        contentColor = Color(0xFFE11D48)
    ),
    ARCHIVES(
        title = "Archives",
        icon = Icons.Rounded.Archive,
        containerColor = Color(0xFFF1F5F9),
        contentColor = Color(0xFF475569)
    ),
    STORAGE(
        title = "Storage",
        icon = Icons.Rounded.Folder,
        containerColor = Color(0xFFCFFAFE),
        contentColor = Color(0xFF0891B2)
    )
}
