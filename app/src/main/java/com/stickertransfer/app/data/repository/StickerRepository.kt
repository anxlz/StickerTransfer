package com.stickertransfer.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.stickertransfer.app.data.model.Sticker
import com.stickertransfer.app.data.model.StickerPack
import com.stickertransfer.app.data.network.TelegramApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

class StickerRepository(
    private val context: Context,
    private val apiService: TelegramApiService = TelegramApiService()
) {

    /**
     * Parse a Telegram sticker share link and return the pack name.
     * Accepted formats:
     *   https://t.me/addstickers/PackName
     *   t.me/addstickers/PackName
     */
    fun parseTelegramLink(link: String): String? {
        val trimmed = link.trim()
        val regex = Regex("""(?:https?://)?t\.me/addstickers/([A-Za-z0-9_]+)""")
        return regex.find(trimmed)?.groupValues?.get(1)
    }

    /**
     * Fetch sticker pack metadata from Telegram (no downloads yet).
     */
    suspend fun fetchStickerPack(
        botToken: String,
        packName: String
    ): Result<StickerPack> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getStickerSet(botToken, packName)
            if (!response.ok || response.result == null) {
                return@withContext Result.Error(
                    response.description ?: "Failed to fetch sticker pack"
                )
            }
            val set = response.result
            val stickers = set.stickers.mapIndexed { idx, s ->
                Sticker(
                    imageFileName = "%03d.webp".format(idx + 1),
                    emojis = listOfNotNull(s.emoji).ifEmpty { listOf("😀") },
                    fileId = s.fileId,
                    isAnimated = s.isAnimated,
                    isVideo = s.isVideo
                )
            }
            val pack = StickerPack(
                identifier = set.name,
                name = set.title,
                publisher = "Telegram",
                trayImageFile = "tray.webp",
                animatedStickerPack = set.isAnimated,
                stickers = stickers
            )
            Result.Success(pack)
        } catch (e: Exception) {
            Result.Error("Network error: ${e.message}", e)
        }
    }

    /**
     * Download all stickers and convert to WEBP 512×512.
     * Files are stored in: filesDir/stickers/{identifier}/
     * Returns the updated StickerPack with localPath set on each Sticker.
     */
    suspend fun downloadAndConvertPack(
        botToken: String,
        pack: StickerPack,
        onProgress: suspend (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<StickerPack> = withContext(Dispatchers.IO) {
        try {
            val packDir = File(context.filesDir, "stickers/${pack.identifier}").apply { mkdirs() }
            val total = pack.stickers.size
            val downloadedStickers = pack.stickers.mapIndexed { idx, sticker ->
                onProgress(idx + 1, total)

                // Resolve file path
                val fileResponse = apiService.getFile(botToken, sticker.fileId)
                if (!fileResponse.ok || fileResponse.result?.filePath == null) {
                    throw Exception("Failed to get file path for sticker ${sticker.fileId}")
                }
                val filePath = fileResponse.result.filePath

                // Download raw bytes
                val rawBytes = apiService.downloadFile(botToken, filePath)

                // Convert / resize to 512x512 WEBP
                val webpBytes = convertToWebp(rawBytes, sticker.isAnimated || sticker.isVideo)

                // Save to disk
                val outFile = File(packDir, sticker.imageFileName)
                outFile.writeBytes(webpBytes)

                sticker.copy(filePath = filePath, localPath = outFile.absolutePath)
            }

            // Create tray icon from first sticker (96×96)
            if (downloadedStickers.isNotEmpty()) {
                createTrayIcon(downloadedStickers.first(), packDir)
            }

            val updatedPack = pack.copy(
                stickers = downloadedStickers,
                localDirectory = packDir.absolutePath
            )
            Result.Success(updatedPack)
        } catch (e: Exception) {
            Result.Error("Download failed: ${e.message}", e)
        }
    }

    /**
     * Convert raw image bytes to WEBP format at 512×512.
     * For animated/video stickers, returns the original bytes (WEBP is already the format).
     */
    private fun convertToWebp(rawBytes: ByteArray, isAnimated: Boolean): ByteArray {
        // Animated stickers (.tgs = gzip'd lottie or .webm) are kept as-is
        // WhatsApp supports animated WEBP — for now we pass through
        if (isAnimated) return rawBytes

        return try {
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: return rawBytes

            // Scale to exactly 512×512
            val scaled = Bitmap.createScaledBitmap(bitmap, 512, 512, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, out)

            // If still > 100KB, reduce quality
            var quality = 80
            var bytes = out.toByteArray()
            while (bytes.size > 102_400 && quality > 10) {
                quality -= 10
                val retry = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, retry)
                bytes = retry.toByteArray()
            }
            bytes
        } catch (e: Exception) {
            rawBytes
        }
    }

    /** Create 96×96 tray icon from the first sticker */
    private fun createTrayIcon(firstSticker: Sticker, packDir: File) {
        try {
            val src = File(firstSticker.localPath)
            if (!src.exists()) return
            val bitmap = BitmapFactory.decodeFile(src.absolutePath) ?: return
            val tray = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
            val out = ByteArrayOutputStream()
            tray.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, out)
            File(packDir, "tray.webp").writeBytes(out.toByteArray())
        } catch (_: Exception) {}
    }

    /**
     * Get the local stickers directory for a given pack identifier.
     */
    fun getPackDirectory(identifier: String): File =
        File(context.filesDir, "stickers/$identifier")

    /**
     * List all locally stored packs.
     */
    fun listLocalPacks(): List<String> {
        val base = File(context.filesDir, "stickers")
        return base.listFiles()?.map { it.name } ?: emptyList()
    }

    /**
     * Delete a local sticker pack.
     */
    fun deletePack(identifier: String) {
        getPackDirectory(identifier).deleteRecursively()
    }
}
