package com.example.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.icons.AppIcons

enum class FileCategory(
    val title: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color
) {
    IMAGE(
        title = "Image",
        icon = AppIcons.Image,
        containerColor = Color(0xFFE0F2FE),
        contentColor = Color(0xFF0284C7)
    ),
    VIDEO(
        title = "Video",
        icon = AppIcons.Videocam,
        containerColor = Color(0xFFF3E8FF),
        contentColor = Color(0xFF9333EA)
    ),
    AUDIO(
        title = "Audio",
        icon = AppIcons.AudioFile,
        containerColor = Color(0xFFFEF3C7),
        contentColor = Color(0xFFD97706)
    ),
    APP(
        title = "App",
        icon = AppIcons.Apps,
        containerColor = Color(0xFFDCFCE7),
        contentColor = Color(0xFF16A34A)
    ),
    DOCUMENTS(
        title = "Documents",
        icon = AppIcons.Description,
        containerColor = Color(0xFFFFE4E6),
        contentColor = Color(0xFFE11D48)
    ),
    ARCHIVES(
        title = "Archives",
        icon = AppIcons.Archive,
        containerColor = Color(0xFFF1F5F9),
        contentColor = Color(0xFF475569)
    ),
    STORAGE(
        title = "Storage",
        icon = AppIcons.Folder,
        containerColor = Color(0xFFCFFAFE),
        contentColor = Color(0xFF0891B2)
    )
}
