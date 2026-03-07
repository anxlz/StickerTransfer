package com.stickertransfer.app.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.stickertransfer.app.BuildConfig
import com.stickertransfer.app.data.model.StickerPack
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.stickertransfer.app.data.model.StickerPacksJson
import java.io.File

/**
 * Content provider required by WhatsApp sticker protocol.
 *
 * WhatsApp queries:
 *   content://{authority}/metadata           → list of all sticker packs
 *   content://{authority}/stickers/{id}      → stickers for a specific pack
 *   content://{authority}/stickers/{id}/{file} → raw sticker file bytes
 */
class StickerContentProvider : ContentProvider() {

    companion object {
        private const val METADATA = 1
        private const val METADATA_CODE_FOR_SINGLE_PACK = 2
        private const val STICKERS = 3
        private const val STICKERS_ASSET = 4
        private const val STICKER_PACK_TRAY_ICON = 5

        private val METADATA_COLUMNS = arrayOf(
            "sticker_pack_id",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_store_link",
            "publisher_email",
            "publisher_website",
            "privacy_policy_website",
            "license_agreement_website",
            "image_data_version",
            "avoid_cache",
            "animated_sticker_pack"
        )

        private val STICKERS_COLUMNS = arrayOf(
            "sticker_file_name",
            "sticker_emoji"
        )
    }

    private val uriMatcher by lazy {
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(BuildConfig.PROVIDER_AUTHORITY, "metadata", METADATA)
            addURI(BuildConfig.PROVIDER_AUTHORITY, "metadata/*", METADATA_CODE_FOR_SINGLE_PACK)
            addURI(BuildConfig.PROVIDER_AUTHORITY, "stickers/*", STICKERS)
            addURI(BuildConfig.PROVIDER_AUTHORITY, "stickers/*/*", STICKERS_ASSET)
            addURI(BuildConfig.PROVIDER_AUTHORITY, "stickers_asset/*/tray_icon.webp", STICKER_PACK_TRAY_ICON)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadStickerPacks(): List<StickerPack> {
        val ctx = context ?: return emptyList()
        val stickersBase = File(ctx.filesDir, "stickers")
        if (!stickersBase.exists()) return emptyList()

        val packs = mutableListOf<StickerPack>()
        stickersBase.listFiles()?.forEach { packDir ->
            if (!packDir.isDirectory) return@forEach
            val metaFile = File(packDir, "meta.json")
            if (metaFile.exists()) {
                try {
                    val meta = json.decodeFromString<StoredPackMeta>(metaFile.readText())
                    packs.add(meta.toStickerPack(packDir.absolutePath))
                } catch (_: Exception) {}
            }
        }
        return packs
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        return when (uriMatcher.match(uri)) {
            METADATA -> {
                val packs = loadStickerPacks()
                val cursor = MatrixCursor(METADATA_COLUMNS)
                packs.forEach { pack ->
                    cursor.addRow(arrayOf(
                        pack.identifier,
                        pack.name,
                        pack.publisher,
                        pack.trayImageFile,
                        "", "", "", "", "", "",
                        pack.imageDataVersion,
                        if (pack.avoidCache) 1 else 0,
                        if (pack.animatedStickerPack) 1 else 0
                    ))
                }
                cursor
            }
            METADATA_CODE_FOR_SINGLE_PACK -> {
                val identifier = uri.lastPathSegment ?: return null
                val packs = loadStickerPacks()
                val pack = packs.find { it.identifier == identifier } ?: return null
                val cursor = MatrixCursor(METADATA_COLUMNS)
                cursor.addRow(arrayOf(
                    pack.identifier, pack.name, pack.publisher, pack.trayImageFile,
                    "", "", "", "", "", "",
                    pack.imageDataVersion, if (pack.avoidCache) 1 else 0,
                    if (pack.animatedStickerPack) 1 else 0
                ))
                cursor
            }
            STICKERS -> {
                val identifier = uri.lastPathSegment ?: return null
                val packs = loadStickerPacks()
                val pack = packs.find { it.identifier == identifier } ?: return null
                val cursor = MatrixCursor(STICKERS_COLUMNS)
                pack.stickers.forEach { sticker ->
                    cursor.addRow(arrayOf(
                        sticker.imageFileName,
                        sticker.emojis.joinToString("")
                    ))
                }
                cursor
            }
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        return when (uriMatcher.match(uri)) {
            STICKERS_ASSET -> {
                // content://{auth}/stickers/{identifier}/{filename}
                val segments = uri.pathSegments
                if (segments.size < 3) return null
                val identifier = segments[1]
                val fileName = segments[2]
                val file = File(ctx.filesDir, "stickers/$identifier/$fileName")
                if (!file.exists()) return null
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            STICKER_PACK_TRAY_ICON -> {
                val segments = uri.pathSegments
                if (segments.size < 2) return null
                val identifier = segments[1]
                val file = File(ctx.filesDir, "stickers/$identifier/tray.webp")
                if (!file.exists()) return null
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String = when (uriMatcher.match(uri)) {
        METADATA, METADATA_CODE_FOR_SINGLE_PACK ->
            "vnd.android.cursor.dir/vnd.${BuildConfig.PROVIDER_AUTHORITY}.metadata"
        STICKERS ->
            "vnd.android.cursor.dir/vnd.${BuildConfig.PROVIDER_AUTHORITY}.stickers"
        STICKERS_ASSET ->
            "image/webp"
        else -> throw IllegalArgumentException("Unknown URI: $uri")
    }

    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}

@kotlinx.serialization.Serializable
data class StoredPackMeta(
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImageFile: String = "tray.webp",
    val animatedStickerPack: Boolean = false,
    val imageDataVersion: String = "1",
    val avoidCache: Boolean = false,
    val stickers: List<StoredStickerMeta>
)

@kotlinx.serialization.Serializable
data class StoredStickerMeta(
    val imageFileName: String,
    val emojis: List<String>
)

fun StoredPackMeta.toStickerPack(localDir: String) =
    com.stickertransfer.app.data.model.StickerPack(
        identifier = identifier,
        name = name,
        publisher = publisher,
        trayImageFile = trayImageFile,
        animatedStickerPack = animatedStickerPack,
        imageDataVersion = imageDataVersion,
        avoidCache = avoidCache,
        stickers = stickers.map {
            com.stickertransfer.app.data.model.Sticker(
                imageFileName = it.imageFileName,
                emojis = it.emojis,
                localPath = "$localDir/${it.imageFileName}"
            )
        },
        localDirectory = localDir
    )
