package com.stickertransfer.app.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.stickertransfer.app.data.model.StickerPack
import com.stickertransfer.app.data.model.Sticker
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException

class StickerContentProvider : ContentProvider() {

    companion object {
        private const val TAG = "StickerContentProvider"
        private const val METADATA = 1
        private const val METADATA_SINGLE = 2
        private const val STICKERS = 3
        private const val STICKERS_ASSET = 4

        private val METADATA_COLUMNS = arrayOf(
            "sticker_pack_identifier",
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

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)

    override fun onCreate(): Boolean {
        val authority = context?.let { "${it.packageName}.StickerContentProvider" } ?: return false
        uriMatcher.addURI(authority, "metadata", METADATA)
        uriMatcher.addURI(authority, "metadata/*", METADATA_SINGLE)
        uriMatcher.addURI(authority, "stickers/*", STICKERS)
        uriMatcher.addURI(authority, "stickers_asset/*/*", STICKERS_ASSET)
        return true
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
                    val pack = json.decodeFromString<StickerPack>(metaFile.readText())
                    packs.add(pack)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading meta for ${packDir.name}", e)
                }
            }
        }
        return packs
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val match = uriMatcher.match(uri)
        return when (match) {
            METADATA -> {
                val cursor = MatrixCursor(METADATA_COLUMNS)
                loadStickerPacks().forEach { pack -> addPackRow(cursor, pack) }
                cursor
            }
            METADATA_SINGLE -> {
                val identifier = uri.lastPathSegment ?: return null
                val pack = loadStickerPacks().find { it.identifier == identifier } ?: return null
                val cursor = MatrixCursor(METADATA_COLUMNS)
                addPackRow(cursor, pack)
                cursor
            }
            STICKERS -> {
                val identifier = uri.lastPathSegment ?: return null
                val pack = loadStickerPacks().find { it.identifier == identifier } ?: return null
                val cursor = MatrixCursor(STICKERS_COLUMNS)
                pack.stickers.forEach { sticker ->
                    cursor.addRow(arrayOf(sticker.imageFileName, sticker.emojis.joinToString(",")))
                }
                cursor
            }
            else -> null
        }
    }

    private fun addPackRow(cursor: MatrixCursor, pack: StickerPack) {
        cursor.addRow(arrayOf(
            pack.identifier,
            pack.name,
            pack.publisher,
            pack.trayImageFile,
            "", // android_play_store_link
            "", // ios_app_store_link
            pack.publisherEmail,
            pack.publisherWebsite,
            pack.privacyPolicyWebsite,
            pack.licenseAgreementWebsite,
            pack.imageDataVersion,
            if (pack.avoidCache) 1 else 0,
            if (pack.animatedStickerPack) 1 else 0
        ))
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val match = uriMatcher.match(uri)
        if (match == STICKERS_ASSET) {
            val segments = uri.pathSegments
            val identifier = segments[1]
            val fileName = segments[2]
            val file = File(context?.filesDir, "stickers/$identifier/$fileName")
            if (file.exists()) {
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        throw FileNotFoundException("No file at $uri")
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            METADATA, METADATA_SINGLE -> "vnd.android.cursor.dir/sticker_pack"
            STICKERS -> "vnd.android.cursor.dir/stickers"
            STICKERS_ASSET -> "image/webp"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
