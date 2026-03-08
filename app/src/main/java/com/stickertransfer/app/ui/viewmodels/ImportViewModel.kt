package com.stickertransfer.app.ui.viewmodels

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickertransfer.app.data.model.Sticker
import com.stickertransfer.app.data.model.StickerPack
import com.stickertransfer.app.data.repository.StickerRepository
import com.stickertransfer.app.utils.WhatsAppUtils
import com.stickertransfer.app.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class ImportUiState {
    data object Idle : ImportUiState()
    data object Processing : ImportUiState()
    data class Ready(val packs: List<StickerPack>) : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}

class ImportViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val repository = StickerRepository(appContext)

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
                        .filter { it.isFile && (it.extension.lowercase() in listOf("webp", "png", "jpg", "jpeg")) }
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
                                val destName = "%03d.webp".format(idx + 1)
                                val destFile = File(packDir, destName)
                                
                                if (repository.convertAndSaveSticker(bitmap, destFile, isTray = false)) {
                                    Sticker(
                                        imageFileName = destName,
                                        emojis = listOf("😀"),
                                        localPath = destFile.absolutePath
                                    )
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        }

                        if (stickers.size < 3) return@mapIndexed null

                        // Create tray icon
                        repository.createTrayIcon(stickers.first().localPath, packId)

                        val pack = StickerPack(
                            identifier = packId,
                            name = "Imported Pack ${packIdx + 1}",
                            publisher = "StickerTransfer",
                            trayImageFile = "tray_icon.webp",
                            stickers = stickers,
                            localDirectory = packDir.absolutePath
                        )

                        repository.savePackMeta(pack)
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
                _uiState.value = ImportUiState.Error("Import failed. Ensure ZIP contains at least 3 valid images.")
            }
        }
    }

    fun renamePack(pack: StickerPack, newName: String) {
        viewModelScope.launch {
            val updatedPack = pack.copy(name = newName)
            repository.savePackMeta(updatedPack)
            
            val currentState = _uiState.value
            if (currentState is ImportUiState.Ready) {
                val updatedPacks = currentState.packs.map {
                    if (it.identifier == pack.identifier) updatedPack else it
                }
                _uiState.value = ImportUiState.Ready(updatedPacks)
            }
        }
    }

    fun removePack(pack: StickerPack) {
        viewModelScope.launch {
            repository.deletePack(pack.identifier)
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
