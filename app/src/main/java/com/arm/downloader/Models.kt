package com.arm.downloader

data class FormatItem(
    val id: String,
    val qualityLabel: String,
    val qualityBadge: String,
    val sizeEstimate: String,
    val cobaltQuality: String,
    val isAudio: Boolean,
    val directUrl: String? = null
)

data class MediaInfo(
    val title: String,
    val thumbnailUrl: String,
    val uploader: String,
    val avatarUrl: String,
    val platform: String,
    val formats: List<FormatItem>,
    val originalUrl: String
)
