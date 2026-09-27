package com.example.model

data class StorageUsageInfo(
    val totalAppBytes: Long,
    val totalDocumentBytes: Long,
    val totalDocumentCount: Int,
    val totalPagesCount: Int,
    val totalAppFormatted: String,
    val folderUsageList: List<FolderStorageUsage>
)

data class FolderStorageUsage(
    val mainCategory: String,
    val mainCategoryId: String,
    val documentCount: Int,
    val totalBytes: Long,
    val formattedSize: String
)
