package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DeviceFileScanner
import com.example.model.FileCategory
import com.example.model.SharedFile
import com.example.model.formatBytes
import com.example.ui.components.AsyncAppIconThumbnail
import com.example.ui.components.AsyncImageThumbnail
import com.example.ui.components.CategoryPillRow
import com.example.ui.components.StyledIconBox
import java.util.UUID

@Composable
fun FilePickerScreen(
    onBackClick: () -> Unit,
    onSendFiles: (List<SharedFile>) -> Unit,
    isAddingMore: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(FileCategory.IMAGE) }
    val allFiles = remember { mutableStateListOf<SharedFile>() }
    val selectedFileIds = remember { mutableStateListOf<String>() }
    var isLoadingDeviceFiles by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val scanned = DeviceFileScanner.queryDeviceFiles(context)
        allFiles.clear()
        allFiles.addAll(scanned)
        isLoadingDeviceFiles = false
    }

    // Real device file picker launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val displayName = cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) it.getString(nameIndex) else null
                    } else null
                } ?: "Selected_File_${System.currentTimeMillis()}"

                val size = cursor?.use {
                    if (it.moveToFirst()) {
                        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1) it.getLong(sizeIndex) else 1_048_576L
                    } else 1_048_576L
                } ?: 1_048_576L

                val ext = displayName.substringAfterLast('.', "").uppercase()
                val detectedCat = when (ext) {
                    "JPG", "JPEG", "PNG", "WEBP", "GIF", "HEIC", "BMP" -> FileCategory.IMAGE
                    "MP4", "MKV", "MOV", "AVI", "3GP", "WEBM" -> FileCategory.VIDEO
                    "MP3", "FLAC", "WAV", "M4A", "AAC", "OGG" -> FileCategory.AUDIO
                    else -> FileCategory.APP
                }

                val isApk = ext == "APK"
                val newFile = SharedFile(
                    id = UUID.randomUUID().toString(),
                    name = displayName,
                    sizeBytes = size,
                    formattedSize = formatBytes(size),
                    category = detectedCat,
                    extension = ext.ifEmpty { "FILE" },
                    lastModified = if (isApk) "Apk App" else "Device Storage",
                    uriString = uri.toString(),
                    isDownloadedApk = isApk
                )
                allFiles.add(0, newFile)
                selectedFileIds.add(newFile.id)
            }
        }
    }

    val filteredFiles = remember(selectedCategory, allFiles.toList()) {
        allFiles.filter { it.category == selectedCategory }
    }

    val selectedFiles = remember(selectedFileIds.toList(), allFiles.toList()) {
        allFiles.filter { selectedFileIds.contains(it.id) }
    }
    val totalSelectedSize = remember(selectedFiles) {
        selectedFiles.sumOf { it.sizeBytes }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("file_picker_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Clean Top Bar with Back icon and Device Picker button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Back Button with styled icon background
                StyledIconBox(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    size = 44.dp,
                    iconSize = 22.dp,
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(12.dp),
                    onClick = onBackClick,
                    modifier = Modifier.testTag("picker_back_button")
                )

                if (isAddingMore) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Add More to Share",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }

                // Pick from device storage button with styled icon background
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .clickable { openDocumentLauncher.launch(arrayOf("*/*")) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("pick_device_files_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = "Browse device",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Browse Storage",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Inbuilt file picker: Top row of pill icons (Image, Video, Audio, Apps)
            CategoryPillRow(
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                modifier = Modifier.fillMaxWidth()
            )

            // Select all / Category header bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${filteredFiles.size} items",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (filteredFiles.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val categoryIds = filteredFiles.map { it.id }
                                if (selectedFileIds.containsAll(categoryIds)) {
                                    selectedFileIds.removeAll(categoryIds)
                                } else {
                                    categoryIds.forEach { id ->
                                        if (!selectedFileIds.contains(id)) selectedFileIds.add(id)
                                    }
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val allCategorySelected = filteredFiles.isNotEmpty() && selectedFileIds.containsAll(filteredFiles.map { it.id })
                        Icon(
                            imageVector = if (allCategorySelected) Icons.Rounded.DoneAll else Icons.Rounded.Check,
                            contentDescription = "Select all",
                            tint = if (allCategorySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (allCategorySelected) "Deselect" else "Select All",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (allCategorySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (filteredFiles.isEmpty() && !isLoadingDeviceFiles) {
                // Empty state for category with Browse Storage button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(selectedCategory.containerColor)
                        ) {
                            Icon(
                                imageVector = selectedCategory.icon,
                                contentDescription = null,
                                tint = selectedCategory.contentColor,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No ${selectedCategory.title} Found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Select files directly from your device storage or downloads.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        OutlinedButton(
                            onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Browse Any File")
                        }
                    }
                }
            } else {
                // Category-specific view rendering
                when (selectedCategory) {
                    FileCategory.IMAGE -> {
                        // 1. IMAGE: 4-COLUMN GRID VIEW WITH IMAGE THUMBNAILS
                        ImageGridSection(
                            images = filteredFiles,
                            selectedFileIds = selectedFileIds,
                            onToggleSelection = { id ->
                                if (selectedFileIds.contains(id)) selectedFileIds.remove(id)
                                else selectedFileIds.add(id)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    FileCategory.VIDEO, FileCategory.AUDIO -> {
                        // 2. VIDEO & AUDIO: LIST VIEW
                        AudioVideoListSection(
                            files = filteredFiles,
                            category = selectedCategory,
                            selectedFileIds = selectedFileIds,
                            onToggleSelection = { id ->
                                if (selectedFileIds.contains(id)) selectedFileIds.remove(id)
                                else selectedFileIds.add(id)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    FileCategory.APP -> {
                        // 3. APP: 5-COLUMN GRID VIEW (Installed Apps + Downloaded APKs with APK Icons below)
                        AppGridSection(
                            apps = filteredFiles,
                            selectedFileIds = selectedFileIds,
                            onToggleSelection = { id ->
                                if (selectedFileIds.contains(id)) selectedFileIds.remove(id)
                                else selectedFileIds.add(id)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    else -> {
                        // Fallback list
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredFiles, key = { it.id }) { file ->
                                val isSelected = selectedFileIds.contains(file.id)
                                AudioVideoListItem(
                                    file = file,
                                    isSelected = isSelected,
                                    onToggle = {
                                        if (isSelected) selectedFileIds.remove(file.id)
                                        else selectedFileIds.add(file.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom floating Send action bar when files are selected
        AnimatedVisibility(
            visible = selectedFiles.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            text = "${selectedFiles.size} selected",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatBytes(totalSelectedSize),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Send Button with new design icon and pill shape
                    Button(
                        onClick = { onSendFiles(selectedFiles) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("picker_send_button")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAddingMore) "Add to Share" else "Send",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1. Image Grid View: Exactly 4 columns with square image thumbnails and select check badges.
 */
@Composable
private fun ImageGridSection(
    images: List<SharedFile>,
    selectedFileIds: List<String>,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(images, key = { it.id }) { file ->
            val isSelected = selectedFileIds.contains(file.id)

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE2E8F0))
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onToggleSelection(file.id) }
                    .testTag("image_grid_item_${file.id}")
            ) {
                // Real image as thumbnail
                AsyncImageThumbnail(file = file)

                // Selection checkmark badge on top right
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f)
                        )
                        .border(1.dp, Color.White, CircleShape)
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // File size badge at bottom
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = file.formattedSize,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 2. Video & Audio List View: Clean row items with styled media icon, title, details, and checkbox.
 */
@Composable
private fun AudioVideoListSection(
    files: List<SharedFile>,
    category: FileCategory,
    selectedFileIds: List<String>,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(files, key = { it.id }) { file ->
            val isSelected = selectedFileIds.contains(file.id)
            AudioVideoListItem(
                file = file,
                isSelected = isSelected,
                onToggle = { onToggleSelection(file.id) }
            )
        }
    }
}

@Composable
private fun AudioVideoListItem(
    file: SharedFile,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    }

    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(12.dp)
            .testTag("media_list_item_${file.id}")
    ) {
        // Styled media icon box with category color container
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(file.category.containerColor)
        ) {
            Icon(
                imageVector = if (file.category == FileCategory.VIDEO) Icons.Rounded.PlayArrow else file.category.icon,
                contentDescription = file.category.title,
                tint = file.category.contentColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.formattedSize,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(file.category.contentColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = file.extension,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = file.category.contentColor
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• ${file.lastModified}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Checkbox with circle container
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                )
                .border(
                    width = 1.5.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = CircleShape
                )
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * 3. App View: Exactly 5 columns with installed applications at top,
 * and Apk Apps shown below using the APK icon as thumbnail.
 */
@Composable
private fun AppGridSection(
    apps: List<SharedFile>,
    selectedFileIds: List<String>,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val installedApps = remember(apps) { apps.filter { !it.isDownloadedApk } }
    val downloadedApks = remember(apps) { apps.filter { it.isDownloadedApk } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // Section 1: Installed Apps
        item(span = { GridItemSpan(5) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Installed Apps (${installedApps.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        items(installedApps, key = { it.id }) { app ->
            val isSelected = selectedFileIds.contains(app.id)
            AppGridItem(
                file = app,
                isSelected = isSelected,
                onClick = { onToggleSelection(app.id) }
            )
        }

        // Section 2: Apk Apps
        item(span = { GridItemSpan(5) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E5FF))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Apk Apps (${downloadedApks.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (downloadedApks.isEmpty()) {
            item(span = { GridItemSpan(5) }) {
                Text(
                    text = "No .apk apps found in storage",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(downloadedApks, key = { it.id }) { apk ->
                val isSelected = selectedFileIds.contains(apk.id)
                AppGridItem(
                    file = apk,
                    isSelected = isSelected,
                    onClick = { onToggleSelection(apk.id) }
                )
            }
        }
    }
}

@Composable
private fun AppGridItem(
    file: SharedFile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag("app_grid_item_${file.id}")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp)
        ) {
            // App or APK icon thumbnail
            AsyncAppIconThumbnail(
                file = file,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            // Selection check badge on top right of icon
            if (isSelected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(1.5.dp, Color.White, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // App name below (1 line, 11sp, centered)
        val cleanName = file.name.removeSuffix(".apk").removeSuffix(".APK")
        Text(
            text = cleanName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = file.formattedSize,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
    }
}
