package com.stickertransfer.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String,
    @SerialName("tray_image_file") val trayImageFile: String = "tray_icon.webp",
    @SerialName("publisher_email") val publisherEmail: String = "",
    @SerialName("publisher_website") val publisherWebsite: String = "",
    @SerialName("privacy_policy_website") val privacyPolicyWebsite: String = "",
    @SerialName("license_agreement_website") val licenseAgreementWebsite: String = "",
    @SerialName("image_data_version") val imageDataVersion: String = "1",
    @SerialName("avoid_cache") val avoidCache: Boolean = false,
    @SerialName("animated_sticker_pack") val animatedStickerPack: Boolean = false,
    val stickers: List<Sticker> = emptyList(),
    // Local path used by the app logic
    val localDirectory: String = ""
)

@Serializable
data class Sticker(
    @SerialName("image_file") val imageFileName: String,
    val emojis: List<String> = listOf("😀"),
    val fileId: String = "", // Telegram file ID
    val isAnimated: Boolean = false,
    val localPath: String = ""
)
