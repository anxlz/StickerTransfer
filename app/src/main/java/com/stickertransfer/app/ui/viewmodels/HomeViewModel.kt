package com.stickertransfer.app.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickertransfer.app.data.model.StickerPack
import com.stickertransfer.app.data.network.PreferencesRepository
import com.stickertransfer.app.data.repository.Result
import com.stickertransfer.app.data.repository.StickerRepository
import com.stickertransfer.app.utils.ZipUtils
import com.stickertransfer.app.utils.WhatsAppUtils
import com.stickertransfer.app.provider.StoredPackMeta
import com.stickertransfer.app.provider.StoredStickerMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

sealed class HomeUiState {
    object Idle : HomeUiState()
    object Loading : HomeUiState()
    data class PackLoaded(val pack: StickerPack) : HomeUiState()
    data class Downloading(val current: Int, val total: Int) : HomeUiState()
    data class Downloaded(val pack: StickerPack) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

data class SnackbarMessage(val text: String, val id: Long = System.currentTimeMillis())

class HomeViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val repository = StickerRepository(appContext)
    private val prefsRepo = PreferencesRepository(appContext)
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableStateFlow<SnackbarMessage?>(null)
    val snackbar: StateFlow<SnackbarMessage?> = _snackbar.asStateFlow()

    private val _botToken = MutableStateFlow("")
    val botToken: StateFlow<String> = _botToken.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepo.botTokenFlow.collect { token ->
                _botToken.value = token
            }
        }
    }

    fun saveBotToken(token: String) {
        viewModelScope.launch {
            prefsRepo.saveBotToken(token)
        }
    }

    fun loadStickerPack(link: String) {
        viewModelScope.launch {
            val token = prefsRepo.botTokenFlow.first()
            if (token.isBlank()) {
                _uiState.value = HomeUiState.Error("Please set your Telegram Bot Token in Settings")
                return@launch
            }
            val packName = repository.parseTelegramLink(link)
            if (packName == null) {
                _uiState.value = HomeUiState.Error("Invalid Telegram sticker link.\nFormat: https://t.me/addstickers/PackName")
                return@launch
            }
            _uiState.value = HomeUiState.Loading
            when (val result = repository.fetchStickerPack(token, packName)) {
                is Result.Success -> _uiState.value = HomeUiState.PackLoaded(result.data)
                is Result.Error -> _uiState.value = HomeUiState.Error(result.message)
                else -> {}
            }
        }
    }

    fun downloadPack(pack: StickerPack) {
        viewModelScope.launch {
            val token = prefsRepo.botTokenFlow.first()
            _uiState.value = HomeUiState.Downloading(0, pack.stickers.size)
            val result = repository.downloadAndConvertPack(token, pack) { cur, tot ->
                _uiState.value = HomeUiState.Downloading(cur, tot)
            }
            when (result) {
                is Result.Success -> {
                    savePackMeta(result.data)
                    _uiState.value = HomeUiState.Downloaded(result.data)
                }
                is Result.Error -> _uiState.value = HomeUiState.Error(result.message)
                else -> {}
            }
        }
    }

    fun exportAsZip(pack: StickerPack) {
        viewModelScope.launch {
            val name = ZipUtils.exportPackAsZip(appContext, pack)
            if (name != null) {
                showSnackbar("Saved to Downloads/$name")
            } else {
                showSnackbar("Export failed — download the pack first")
            }
        }
    }

    fun addToWhatsApp(pack: StickerPack, business: Boolean = false) {
        val installed = if (business)
            WhatsAppUtils.isWhatsAppBusinessInstalled(appContext)
        else
            WhatsAppUtils.isWhatsAppInstalled(appContext)

        if (!installed) {
            showSnackbar(if (business) "WhatsApp Business is not installed" else "WhatsApp is not installed")
            return
        }
        val success = WhatsAppUtils.addStickerPackToWhatsApp(appContext, pack, business)
        if (!success) showSnackbar("Failed to launch WhatsApp")
    }

    fun dismissError() {
        _uiState.value = HomeUiState.Idle
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }

    private fun showSnackbar(msg: String) {
        _snackbar.value = SnackbarMessage(msg)
    }

    /** Persist pack metadata so ContentProvider can serve it to WhatsApp */
    private fun savePackMeta(pack: StickerPack) {
        try {
            val meta = StoredPackMeta(
                identifier = pack.identifier,
                name = pack.name,
                publisher = pack.publisher,
                trayImageFile = pack.trayImageFile,
                animatedStickerPack = pack.animatedStickerPack,
                stickers = pack.stickers.map {
                    StoredStickerMeta(it.imageFileName, it.emojis)
                }
            )
            val metaFile = File(pack.localDirectory, "meta.json")
            metaFile.writeText(json.encodeToString(meta))
        } catch (_: Exception) {}
    }
}
