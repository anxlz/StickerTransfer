package com.stickertransfer.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val description: String? = null
)

@Serializable
data class TelegramStickerSet(
    val name: String,
    val title: String,
    @SerialName("sticker_type") val stickerType: String = "regular",
    @SerialName("is_animated") val isAnimated: Boolean = false,
    @SerialName("is_video") val isVideo: Boolean = false,
    val stickers: List<TelegramSticker>,
    val thumbnail: TelegramPhotoSize? = null
)

@Serializable
data class TelegramSticker(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String,
    val type: String = "regular",
    val width: Int = 512,
    val height: Int = 512,
    @SerialName("is_animated") val isAnimated: Boolean = false,
    @SerialName("is_video") val isVideo: Boolean = false,
    val emoji: String? = null,
    @SerialName("set_name") val setName: String? = null,
    @SerialName("file_size") val fileSize: Int? = null,
    val thumbnail: TelegramPhotoSize? = null
)

@Serializable
data class TelegramPhotoSize(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String,
    val width: Int,
    val height: Int,
    @SerialName("file_size") val fileSize: Int? = null
)

@Serializable
data class TelegramFile(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String,
    @SerialName("file_size") val fileSize: Int? = null,
    @SerialName("file_path") val filePath: String? = null
)
