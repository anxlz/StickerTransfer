package com.stickertransfer.app.data.network

import com.stickertransfer.app.data.model.TelegramFile
import com.stickertransfer.app.data.model.TelegramResponse
import com.stickertransfer.app.data.model.TelegramStickerSet
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class TelegramApiService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    /**
     * Fetch sticker set metadata by name.
     * @param botToken Telegram Bot Token from @BotFather
     * @param stickerSetName Pack name from t.me/addstickers/{name}
     */
    suspend fun getStickerSet(
        botToken: String,
        stickerSetName: String
    ): TelegramResponse<TelegramStickerSet> {
        val url = "https://api.telegram.org/bot$botToken/getStickerSet?name=$stickerSetName"
        return client.get(url).body()
    }

    /**
     * Resolve a file_id to a downloadable file path.
     */
    suspend fun getFile(
        botToken: String,
        fileId: String
    ): TelegramResponse<TelegramFile> {
        val url = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"
        return client.get(url).body()
    }

    /**
     * Download file bytes from Telegram CDN.
     * @param filePath The file_path returned by getFile
     */
    suspend fun downloadFile(
        botToken: String,
        filePath: String
    ): ByteArray {
        val url = "https://api.telegram.org/file/bot$botToken/$filePath"
        return client.get(url).readBytes()
    }

    fun close() = client.close()
}
