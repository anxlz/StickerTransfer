package com.stickertransfer.app.ui.viewmodels

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickertransfer.app.data.model.Sticker
import com.stickertransfer.app.data.model.StickerPack
import com.stickertransfer.app.data.network.PreferencesRepository
import com.stickertransfer.app.data.repository.Result
import com.stickertransfer.app.data.repository.StickerRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

sealed class HomeUiState {
    data object Idle : HomeUiState()
    data object Loading : HomeUiState()
    data class Downloading(val current: Int, val total: Int) : HomeUiState()
    data class PackLoaded(
        val pack: StickerPack,
        val parts: List<List<Sticker>>,
        val isDownloaded: Boolean = false
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val repository = StickerRepository(appContext)
    private val prefsRepo = PreferencesRepository(appContext)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _snackbar = MutableStateFlow<SnackbarMessage?>(null)
    val snackbar = _snackbar.asStateFlow()

    private val _botToken = MutableStateFlow("")
    val botToken = _botToken.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepo.botTokenFlow.collect { _botToken.value = it }
        }
    }

    fun saveBotToken(token: String) = viewModelScope.launch { prefsRepo.saveBotToken(token) }

    fun pasteFromClipboard(): String? {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
            return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        }
        return null
    }

    fun loadStickerPack(link: String) = viewModelScope.launch {
        val token = _botToken.value
        if (token.isBlank()) {
            _uiState.value = HomeUiState.Error("Please set Telegram Bot Token in Settings")
            return@launch
        }
        val packName = repository.parseTelegramLink(link) ?: run {
            _uiState.value = HomeUiState.Error("Invalid link format")
            return@launch
        }

        _uiState.value = HomeUiState.Loading
        when (val result = repository.fetchStickerPack(token, packName)) {
            is Result.Success -> {
                val pack = result.data
                val parts = repository.splitIntoParts(pack.stickers)
                _uiState.value = HomeUiState.PackLoaded(pack, parts)
            }
            is Result.Error -> _uiState.value = HomeUiState.Error(result.message)
            else -> {}
        }
    }

    fun downloadAll(pack: StickerPack, parts: List<List<Sticker>>) = viewModelScope.launch {
        val token = _botToken.value
        val total = pack.stickers.size
        var current = 0
        
        _uiState.value = HomeUiState.Downloading(0, total)
        
        val downloadedParts = mutableListOf<List<Sticker>>()
        
        parts.forEachIndexed { partIdx, partStickers ->
            val partId = "${pack.identifier}_part${partIdx + 1}"
            val downloadedStickers = mutableListOf<Sticker>()
            
            partStickers.forEach { s ->
                val downloaded = repository.downloadSticker(token, s, partId)
                if (downloaded != null) downloadedStickers.add(downloaded)
                current++
                _uiState.value = HomeUiState.Downloading(current, total)
            }
            
            val partPack = pack.copy(
                identifier = partId,
                name = "${pack.name} Part ${partIdx + 1}",
                stickers = downloadedStickers,
                localDirectory = File(appContext.filesDir, "stickers/$partId").absolutePath
            )
            
            if (downloadedStickers.isNotEmpty()) {
                repository.createTrayIcon(downloadedStickers.first().localPath, partId)
                repository.savePackMeta(partPack)
            }
            downloadedParts.add(downloadedStickers)
        }
        
        _uiState.value = HomeUiState.PackLoaded(
            pack = pack.copy(stickers = downloadedParts.flatten()),
            parts = downloadedParts,
            isDownloaded = true
        )
        _snackbar.value = SnackbarMessage("Download Complete!")
    }

    fun removePart(identifier: String) {
        repository.deletePack(identifier)
        _snackbar.value = SnackbarMessage("Part Removed")
        _uiState.value = HomeUiState.Idle
    }

    fun clearSnackbar() { _snackbar.value = null }
    fun dismissError() { _uiState.value = HomeUiState.Idle }
}
