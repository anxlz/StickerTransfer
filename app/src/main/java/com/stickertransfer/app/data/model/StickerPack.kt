package com.stickertransfer.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Internal sticker pack model used across the app.
 */
data class StickerPack(
    val identifier: String,          // Unique pack ID (Telegram set name)
    val name: String,                // Display name
    val publisher: String,           // Author / publisher
    val trayImageFile: String,       // Tray icon filename (96x96 WEBP)
    val publisherEmail: String = "",
    val publisherWebsite: String = "",
    val privacyPolicyWebsite: String = "",
    val licenseAgreementWebsite: String = "",
    val imageDataVersion: String = "1",
    val avoidCache: Boolean = false,
    val animatedStickerPack: Boolean = false,
    val stickers: List<Sticker> = emptyList(),
    // Local paths (set after download)
    val localDirectory: String = "",
)

data class Sticker(
    val imageFileName: String,       // e.g. "001.webp"
    val emojis: List<String> = listOf("😀"),
    // Telegram file info
    val fileId: String = "",
    val filePath: String = "",       // Relative path on Telegram CDN
    val isAnimated: Boolean = false,
    val isVideo: Boolean = false,
    // Local path (set after download)
    val localPath: String = "",
)

/**
 * WhatsApp sticker pack JSON format (assets/sticker_packs.json)
 */
@Serializable
data class StickerPacksJson(
    @SerialName("android_play_store_link") val androidPlayStoreLink: String = "",
    @SerialName("ios_app_store_link") val iosAppStoreLink: String = "",
    @SerialName("sticker_packs") val stickerPacks: List<StickerPackJson>
)

@Serializable
data class StickerPackJson(
    val identifier: String,
    val name: String,
    val publisher: String,
    @SerialName("tray_image_file") val trayImageFile: String,
    @SerialName("publisher_email") val publisherEmail: String = "",
    @SerialName("publisher_website") val publisherWebsite: String = "",
    @SerialName("privacy_policy_website") val privacyPolicyWebsite: String = "",
    @SerialName("license_agreement_website") val licenseAgreementWebsite: String = "",
    @SerialName("image_data_version") val imageDataVersion: String = "1",
    @SerialName("avoid_cache") val avoidCache: Boolean = false,
    @SerialName("animated_sticker_pack") val animatedStickerPack: Boolean = false,
    val stickers: List<StickerJson>
)

@Serializable
data class StickerJson(
    @SerialName("image_file") val imageFile: String,
    val emojis: List<String>
)
