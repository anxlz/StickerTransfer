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

    fun parseTelegramLink(link: String): String? {
        val trimmed = link.trim()
        val regex = Regex("""(?:https?://)?t\.me/addstickers/([A-Za-z0-9_]+)""")
        return regex.find(trimmed)?.groupValues?.get(1)
    }

    /**
     * Fetch sticker pack metadata from Telegram and split into parts of 30 if needed.
     */
    suspend fun fetchStickerPackParts(
        botToken: String,
        packName: String
    ): Result<List<StickerPack>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getStickerSet(botToken, packName)
            if (!response.ok || response.result == null) {
                return@withContext Result.Error(
                    response.description ?: "Failed to fetch sticker pack"
                )
            }
            val set = response.result
            val allStickers = set.stickers.take(120) // Still cap at 120 total
            
            val packs = allStickers.chunked(30).mapIndexed { packIdx, chunk ->
                val partName = if (allStickers.size > 30) "${set.title} Part ${packIdx + 1}" else set.title
                val partId = if (allStickers.size > 30) "${set.name}_p${packIdx + 1}" else set.name
                
                val stickers = chunk.mapIndexed { idx, s ->
                    Sticker(
                        imageFileName = "%03d.webp".format(idx + 1),
                        emojis = listOfNotNull(s.emoji).ifEmpty { listOf("😀") },
                        fileId = s.fileId,
                        isAnimated = s.isAnimated,
                        isVideo = s.isVideo
                    )
                }
                StickerPack(
                    identifier = partId,
                    name = partName,
                    publisher = "Telegram",
                    trayImageFile = "tray.webp",
                    animatedStickerPack = set.isAnimated,
                    stickers = stickers
                )
            }
            Result.Success(packs)
        } catch (e: Exception) {
            Result.Error("Network error: ${e.message}", e)
        }
    }

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

                val fileResponse = apiService.getFile(botToken, sticker.fileId)
                if (!fileResponse.ok || fileResponse.result?.filePath == null) {
                    throw Exception("Failed to get file path for sticker ${sticker.fileId}")
                }
                val filePath = fileResponse.result.filePath
                val rawBytes = apiService.downloadFile(botToken, filePath)
                
                // Convert to 512x512 WEBP
                val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                val webpBytes = if (bitmap != null) {
                    val scaled = Bitmap.createScaledBitmap(bitmap, 512, 512, true)
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, out)
                    out.toByteArray()
                } else rawBytes

                val outFile = File(packDir, sticker.imageFileName)
                outFile.writeBytes(webpBytes)

                sticker.copy(filePath = filePath, localPath = outFile.absolutePath)
            }

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

    fun deletePack(identifier: String) {
        File(context.filesDir, "stickers/$identifier").deleteRecursively()
    }
}
