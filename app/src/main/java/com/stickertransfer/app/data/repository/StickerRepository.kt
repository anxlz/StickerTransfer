package com.stickertransfer.app.data.repository

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import com.stickertransfer.app.data.model.Sticker
import com.stickertransfer.app.data.model.StickerPack
import com.stickertransfer.app.data.network.TelegramApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun parseTelegramLink(link: String): String? {
        val trimmed = link.trim()
        val regex = Regex("""(?:https?://)?(?:t\.me|telegram\.me|telegram\.dog)/addstickers/([A-Za-z0-9_]+)""")
        val tgRegex = Regex("""tg://addstickers\?set=([A-Za-z0-9_]+)""")
        return regex.find(trimmed)?.groupValues?.get(1) ?: tgRegex.find(trimmed)?.groupValues?.get(1)
    }

    fun splitIntoParts(stickers: List<Sticker>, maxPerPart: Int = 30): List<List<Sticker>> {
        return stickers.chunked(maxPerPart)
    }

    suspend fun fetchStickerPack(
        botToken: String,
        packName: String
    ): Result<StickerPack> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getStickerSet(botToken, packName)
            if (!response.ok || response.result == null) {
                return@withContext Result.Error(response.description ?: "Failed to fetch sticker pack")
            }
            val set = response.result
            // CAP at 120 total stickers
            val stickers = set.stickers.take(120).mapIndexed { idx, s ->
                Sticker(
                    imageFileName = "%03d.webp".format(idx + 1),
                    emojis = listOfNotNull(s.emoji).ifEmpty { listOf("😀") },
                    fileId = s.fileId,
                    isAnimated = s.isAnimated
                )
            }
            Result.Success(StickerPack(
                identifier = set.name,
                name = set.title,
                publisher = "Telegram",
                stickers = stickers
            ))
        } catch (e: Exception) {
            Result.Error("Network error: ${e.message}", e)
        }
    }

    suspend fun downloadSticker(
        botToken: String,
        sticker: Sticker,
        targetFolderName: String
    ): Sticker? = withContext(Dispatchers.IO) {
        try {
            val packDir = File(context.filesDir, "stickers/$targetFolderName").apply { mkdirs() }
            val fileResponse = apiService.getFile(botToken, sticker.fileId)
            val filePath = fileResponse.result?.filePath ?: return@withContext null
            val rawBytes = apiService.downloadFile(botToken, filePath)
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return@withContext null
            val outFile = File(packDir, sticker.imageFileName)
            if (convertAndSaveSticker(bitmap, outFile, isTray = false)) {
                sticker.copy(localPath = outFile.absolutePath)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading sticker", e)
            null
        }
    }

    fun savePackMeta(pack: StickerPack) {
        val dir = File(context.filesDir, "stickers/${pack.identifier}").apply { mkdirs() }
        File(dir, "meta.json").writeText(json.encodeToString(pack))
    }

    fun createTrayIcon(stickerPath: String, targetFolderName: String) {
        try {
            val bitmap = BitmapFactory.decodeFile(stickerPath) ?: return
            val outFile = File(context.filesDir, "stickers/$targetFolderName/tray_icon.webp")
            convertAndSaveSticker(bitmap, outFile, isTray = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed tray icon", e)
        }
    }

    fun convertAndSaveSticker(bitmap: Bitmap, outFile: File, isTray: Boolean): Boolean {
        val targetSize = if (isTray) 96 else 512
        val maxSize = if (isTray) 50 * 1024 else 100 * 1024
        val scaled = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(scaled)
        val ratio = targetSize.toFloat() / Math.max(bitmap.width, bitmap.height)
        val w = (bitmap.width * ratio).toInt()
        val h = (bitmap.height * ratio).toInt()
        canvas.drawBitmap(bitmap, null, Rect((targetSize - w) / 2, (targetSize - h) / 2, (targetSize + w) / 2, (targetSize + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        var q = 100
        val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        var success = false
        while (q >= 10) {
            val out = ByteArrayOutputStream()
            scaled.compress(format, q, out)
            if (out.size() <= maxSize) {
                outFile.writeBytes(out.toByteArray())
                success = true; break
            }
            q -= 10
        }
        scaled.recycle(); return success
    }

    fun getLocalPacks(): List<StickerPack> {
        val root = File(context.filesDir, "stickers")
        if (!root.exists()) return emptyList()
        return root.listFiles { f -> f.isDirectory }?.mapNotNull { dir ->
            val meta = File(dir, "meta.json")
            if (meta.exists()) try {
                json.decodeFromString<StickerPack>(meta.readText())
            } catch (e: Exception) { null } else null
        }?.sortedBy { it.name } ?: emptyList()
    }

    fun deletePack(identifier: String) {
        File(context.filesDir, "stickers/$identifier").deleteRecursively()
    }
}
