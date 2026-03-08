package com.stickertransfer.app.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickertransfer.app.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class BackupUiState {
    object Idle : BackupUiState()
    object Processing : BackupUiState()
    data class Success(val message: String) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

class BackupViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    
    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _selectedPath = MutableStateFlow<Uri?>(null)
    val selectedPath = _selectedPath.asStateFlow()

    private val _selectedFile = MutableStateFlow<Uri?>(null)
    val selectedFile = _selectedFile.asStateFlow()

    fun setBackupPath(uri: Uri) {
        _selectedPath.value = uri
    }

    fun setBackupFile(uri: Uri) {
        _selectedFile.value = uri
    }

    fun createBackup() {
        val path = _selectedPath.value ?: return
        viewModelScope.launch {
            _uiState.value = BackupUiState.Processing
            val result = performBackup(path)
            _uiState.value = if (result) BackupUiState.Success("Backup created successfully") 
                             else BackupUiState.Error("Failed to create backup")
        }
    }

    private suspend fun performBackup(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val stickersDir = File(appContext.filesDir, "stickers")
            if (!stickersDir.exists()) return@withContext false

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipName = "StickerTransfer_backup_$timestamp.zip"
            
            val directory = DocumentFile.fromTreeUri(appContext, treeUri) ?: return@withContext false
            val file = directory.createFile("application/zip", zipName) ?: return@withContext false
            
            appContext.contentResolver.openOutputStream(file.uri)?.use { os ->
                ZipOutputStream(os).use { zos ->
                    stickersDir.walkTopDown().filter { it.isFile }.forEach { f ->
                        val entryName = f.relativeTo(stickersDir.parentFile!!).path
                        zos.putNextEntry(ZipEntry(entryName))
                        f.inputStream().copyTo(zos)
                        zos.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun restoreBackup() {
        val fileUri = _selectedFile.value ?: return
        viewModelScope.launch {
            _uiState.value = BackupUiState.Processing
            val result = performRestore(fileUri)
            _uiState.value = if (result) BackupUiState.Success("Backup restored successfully")
                             else BackupUiState.Error("Failed to restore backup")
        }
    }

    private suspend fun performRestore(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext false
            val stickersDir = File(appContext.filesDir, "stickers")
            stickersDir.mkdirs()

            java.util.zip.ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(appContext.filesDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun dismiss() {
        _uiState.value = BackupUiState.Idle
    }
}
