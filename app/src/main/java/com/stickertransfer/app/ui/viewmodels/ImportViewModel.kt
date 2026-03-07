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
import java.io.ByteArrayOutputStream
import java.io.File

sealed class ImportUiState {
    object Idle : ImportUiState()
    object Processing : ImportUiState()
    data class Ready(val packs: List<StickerPack>) : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}

class ImportViewModel(context: Context) : ViewModel() {

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

                    val imageFiles = extractDir.walkTopDown()
                        .filter { it.isFile && (it.extension.lowercase() == "webp" || it.extension.lowercase() == "png" || it.extension.lowercase() == "jpg") }
                        .sortedBy { it.name }
                        .toList()

                    if (imageFiles.isEmpty()) return@withContext null

                    val limitedFiles = imageFiles.take(120)
                    val basePackId = "import_${System.currentTimeMillis()}"
                    
                    val packs = limitedFiles.chunked(30).mapIndexed { packIdx, chunk ->
                        val packId = "${basePackId}_p${packIdx + 1}"
                        val packDir = File(appContext.filesDir, "stickers/$packId").apply { mkdirs() }
                        
                        val stickers = chunk.mapIndexedNotNull { idx, file ->
                            try {
                                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@mapIndexedNotNull null
                                val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 512, 512, true)
                                val out = ByteArrayOutputStream()
                                scaled.compress(android.graphics.Bitmap.CompressFormat.WEBP, 85, out)
                                
                                val destName = "%03d.webp".format(idx + 1)
                                val destFile = File(packDir, destName)
                                destFile.writeBytes(out.toByteArray())

                                Sticker(
                                    imageFileName = destName,
                                    emojis = listOf("😀"),
                                    localPath = destFile.absolutePath
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }

                        if (stickers.size < 3) return@mapIndexed null

                        stickers.firstOrNull()?.let { first ->
                            val bmp = BitmapFactory.decodeFile(first.localPath)
                            val tray = android.graphics.Bitmap.createScaledBitmap(bmp, 96, 96, true)
                            val out = ByteArrayOutputStream()
                            tray.compress(android.graphics.Bitmap.CompressFormat.WEBP, 90, out)
                            File(packDir, "tray.webp").writeBytes(out.toByteArray())
                        }

                        val pack = StickerPack(
                            identifier = packId,
                            name = "Imported Pack ${packIdx + 1}",
                            publisher = "StickerTransfer",
                            trayImageFile = "tray.webp",
                            stickers = stickers,
                            localDirectory = packDir.absolutePath
                        )

                        val meta = StoredPackMeta(
                            identifier = pack.identifier,
                            name = pack.name,
                            publisher = pack.publisher,
                            trayImageFile = pack.trayImageFile,
                            stickers = stickers.map { StoredStickerMeta(it.imageFileName, it.emojis) }
                        )
                        File(packDir, "meta.json").writeText(json.encodeToString(meta))
                        pack
                    }.filterNotNull()

                    if (packs.isEmpty()) null else packs
                } catch (e: Exception) {
                    null
                }
            }
            if (result != null) {
                _uiState.value = ImportUiState.Ready(result)
            } else {
                _uiState.value = ImportUiState.Error("Import failed. Ensure ZIP contains at least 3 images.")
            }
        }
    }

    fun renamePack(pack: StickerPack, newName: String) {
        viewModelScope.launch {
            val packDir = File(appContext.filesDir, "stickers/${pack.identifier}")
            val metaFile = File(packDir, "meta.json")
            if (metaFile.exists()) {
                try {
                    val meta = json.decodeFromString<StoredPackMeta>(metaFile.readText())
                    val newMeta = meta.copy(name = newName)
                    metaFile.writeText(json.encodeToString(newMeta))
                    
                    val currentState = _uiState.value
                    if (currentState is ImportUiState.Ready) {
                        val updatedPacks = currentState.packs.map {
                            if (it.identifier == pack.identifier) it.copy(name = newName) else it
                        }
                        _uiState.value = ImportUiState.Ready(updatedPacks)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun removePack(pack: StickerPack) {
        viewModelScope.launch {
            File(appContext.filesDir, "stickers/${pack.identifier}").deleteRecursively()
            val currentState = _uiState.value
            if (currentState is ImportUiState.Ready) {
                val updatedPacks = currentState.packs.filter { it.identifier != pack.identifier }
                if (updatedPacks.isEmpty()) {
                    _uiState.value = ImportUiState.Idle
                } else {
                    _uiState.value = ImportUiState.Ready(updatedPacks)
                }
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
        val success = WhatsAppUtils.addStickerPackToWhatsApp(appContext, pack, business)
        if (!success) {
            _snackbar.value = SnackbarMessage("Failed to launch WhatsApp")
        }
    }

    fun clearSnackbar() { _snackbar.value = null }
    fun reset() { _uiState.value = ImportUiState.Idle }
}
