package com.stickertransfer.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.util.Log
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
    private val TAG = "StickerRepository"

    fun parseTelegramLink(link: String): String? {
        val trimmed = link.trim()
        val regex = Regex("""(?:https?://)?(?:t\.me|telegram\.me|telegram\.dog)/addstickers/([A-Za-z0-9_]+)""")
        val tgRegex = Regex("""tg://addstickers\?set=([A-Za-z0-9_]+)""")
        return regex.find(trimmed)?.groupValues?.get(1) ?: tgRegex.find(trimmed)?.groupValues?.get(1)
    }

    suspend fun fetchStickerPackParts(
        botToken: String,
        packName: String
    ): Result<List<StickerPack>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getStickerSet(botToken, packName)
            if (!response.ok || response.result == null) {
                return@withContext Result.Error(response.description ?: "Failed to fetch sticker pack")
            }
            val set = response.result
            val allStickers = set.stickers.take(120)
            
            // Smart chunking: Ensure every pack has 3-30 stickers
            val tempChunks = allStickers.chunked(30).toMutableList()
            if (tempChunks.size > 1 && tempChunks.last().size < 3) {
                val last = tempChunks.removeAt(tempChunks.size - 1)
                val secondLast = tempChunks.removeAt(tempChunks.size - 1)
                val combined = secondLast + last
                val split = combined.size / 2
                tempChunks.add(combined.subList(0, split))
                tempChunks.add(combined.subList(split, combined.size))
            }

            val packs = tempChunks.mapIndexed { packIdx, chunk ->
                val partName = if (tempChunks.size > 1) "${set.title} Part ${packIdx + 1}" else set.title
                val partId = if (tempChunks.size > 1) "${set.name}_p${packIdx + 1}" else set.name
                
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
                    trayImageFile = "tray_icon.webp",
                    animatedStickerPack = false, 
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
            val downloadedStickers = mutableListOf<Sticker>()

            pack.stickers.forEachIndexed { idx, sticker ->
                onProgress(idx + 1, total)
                try {
                    val fileResponse = apiService.getFile(botToken, sticker.fileId)
                    val filePath = fileResponse.result?.filePath ?: return@forEachIndexed
                    val rawBytes = apiService.downloadFile(botToken, filePath)
                    
                    val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    if (bitmap == null) {
                        Log.e(TAG, "Failed to decode sticker ${sticker.fileId}")
                        return@forEachIndexed 
                    }

                    val outFile = File(packDir, sticker.imageFileName)
                    if (convertAndSaveSticker(bitmap, outFile, isTray = false)) {
                        downloadedStickers.add(sticker.copy(
                            filePath = filePath, 
                            localPath = outFile.absolutePath
                        ))
                    }
                    bitmap.recycle()
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing sticker $idx", e)
                }
            }

            if (downloadedStickers.size < 3) {
                return@withContext Result.Error("Pack must have at least 3 compatible stickers (found ${downloadedStickers.size})")
            }

            // Create tray icon from first successful sticker
            val firstStickerFile = File(downloadedStickers.first().localPath)
            val firstBitmap = BitmapFactory.decodeFile(firstStickerFile.absolutePath)
            if (firstBitmap != null) {
                convertAndSaveSticker(firstBitmap, File(packDir, "tray_icon.webp"), isTray = true)
                firstBitmap.recycle()
            }

            Result.Success(pack.copy(
                stickers = downloadedStickers,
                localDirectory = packDir.absolutePath
            ))
        } catch (e: Exception) {
            Result.Error("Download failed: ${e.message}", e)
        }
    }

    private fun convertAndSaveSticker(bitmap: Bitmap, outFile: File, isTray: Boolean): Boolean {
        val targetSize = if (isTray) 96 else 512
        val maxSize = if (isTray) 50 * 1024 else 100 * 1024
        
        // Letterboxing to ensure exact size without stretching
        val scaledBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(scaledBitmap)
        val ratio = targetSize.toFloat() / Math.max(bitmap.width, bitmap.height)
        val width = (bitmap.width * ratio).toInt()
        val height = (bitmap.height * ratio).toInt()
        val left = (targetSize - width) / 2
        val top = (targetSize - height) / 2
        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        val destRect = Rect(left, top, left + width, top + height)
        canvas.drawBitmap(bitmap, srcRect, destRect, Paint(Paint.FILTER_BITMAP_FLAG))

        var quality = 100
        var success = false
        
        // Use standard WebP format
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        while (quality >= 10) {
            val out = ByteArrayOutputStream()
            scaledBitmap.compress(format, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= maxSize) {
                outFile.writeBytes(bytes)
                success = true
                break
            }
            quality -= 10
        }
        scaledBitmap.recycle()
        return success
    }

    fun deletePack(identifier: String) {
        File(context.filesDir, "stickers/$identifier").deleteRecursively()
    }
}
