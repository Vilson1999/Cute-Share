package com.example.model

data class SharedFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val category: FileCategory,
    val extension: String,
    val lastModified: String,
    val uriString: String? = null,
    val drawableResId: Int? = null
)
