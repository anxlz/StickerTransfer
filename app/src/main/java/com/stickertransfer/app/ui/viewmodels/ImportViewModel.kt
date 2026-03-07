package com.stickertransfer.app.ui.viewmodels

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickertransfer.app.data.model.Sticker
import com.stickertransfer.app.data.model.StickerPack
import com.stickertransfer.app.provider.StoredPackMeta
import com.stickertransfer.app.provider.StoredStickerMeta
import com.stickertransfer.app.utils.WhatsAppUtils
import com.stickertransfer.app.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

sealed class ImportUiState {
    object Idle : ImportUiState()
    object Processing : ImportUiState()
    data class Ready(val pack: StickerPack) : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}

class ImportViewModel(private val context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableStateFlow<SnackbarMessage?>(null)
    val snackbar: StateFlow<SnackbarMessage?> = _snackbar.asStateFlow()

    fun processZip(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Processing
            val result = withContext(Dispatchers.IO) {
                try {
                    val bytes = appContext.contentResolver.openInputStream(uri)?.readBytes()
                        ?: return@withContext null
                    val extractDir = ZipUtils.extractZip(appContext, bytes) ?: return@withContext null

                    // Find all WEBP files
                    val webpFiles = extractDir.walkTopDown()
                        .filter { it.isFile && it.extension.lowercase() == "webp" }
                        .sortedBy { it.name }
                        .toList()

                    if (webpFiles.isEmpty()) return@withContext null

                    // Validate each file is 512×512
                    val validStickers = webpFiles.filter { file ->
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, opts)
                        opts.outWidth == 512 && opts.outHeight == 512
                    }

                    if (validStickers.isEmpty()) return@withContext null

                    val packId = "import_${System.currentTimeMillis()}"
                    val packDir = File(appContext.filesDir, "stickers/$packId").apply { mkdirs() }

                    // Copy valid stickers into app storage
                    val stickers = validStickers.mapIndexed { idx, file ->
                        val destName = "%03d.webp".format(idx + 1)
                        file.copyTo(File(packDir, destName), overwrite = true)
                        Sticker(
                            imageFileName = destName,
                            emojis = listOf("😀"),
                            localPath = File(packDir, destName).absolutePath
                        )
                    }

                    // Create tray icon from first sticker
                    validStickers.firstOrNull()?.let { first ->
                        val bmp = BitmapFactory.decodeFile(first.absolutePath)
                        val tray = android.graphics.Bitmap.createScaledBitmap(bmp, 96, 96, true)
                        val out = java.io.ByteArrayOutputStream()
                        tray.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 90, out)
                        File(packDir, "tray.webp").writeBytes(out.toByteArray())
                    }

                    val pack = StickerPack(
                        identifier = packId,
                        name = "Imported Pack",
                        publisher = "StickerTransfer",
                        trayImageFile = "tray.webp",
                        stickers = stickers,
                        localDirectory = packDir.absolutePath
                    )

                    // Save metadata
                    val meta = StoredPackMeta(
                        identifier = pack.identifier,
                        name = pack.name,
                        publisher = pack.publisher,
                        trayImageFile = pack.trayImageFile,
                        stickers = stickers.map { StoredStickerMeta(it.imageFileName, it.emojis) }
                    )
                    File(packDir, "meta.json").writeText(json.encodeToString(meta))

                    pack
                } catch (e: Exception) {
                    null
                }
            }
            if (result != null) {
                _uiState.value = ImportUiState.Ready(result)
            } else {
                _uiState.value = ImportUiState.Error("Invalid ZIP — ensure stickers are 512×512 WEBP files")
            }
        }
    }

    fun addToWhatsApp(pack: StickerPack, business: Boolean = false) {
        val installed = if (business)
            WhatsAppUtils.isWhatsAppBusinessInstalled(appContext)
        else
            WhatsAppUtils.isWhatsAppInstalled(appContext)

        if (!installed) {
            _snackbar.value = SnackbarMessage(if (business) "WhatsApp Business not installed" else "WhatsApp not installed")
            return
        }
        WhatsAppUtils.addStickerPackToWhatsApp(appContext, pack, business)
    }

    fun clearSnackbar() { _snackbar.value = null }
    fun reset() { _uiState.value = ImportUiState.Idle }
}
