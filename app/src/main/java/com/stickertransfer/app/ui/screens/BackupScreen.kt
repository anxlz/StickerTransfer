package com.stickertransfer.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stickertransfer.app.ui.viewmodels.BackupUiState
import com.stickertransfer.app.ui.viewmodels.BackupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(viewModel: BackupViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPath by viewModel.selectedPath.collectAsState()
    val selectedFile by viewModel.selectedFile.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.setBackupPath(it) }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setBackupFile(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Create Backup Section
            SectionTitle("Create Backup", Icons.Default.CloudUpload)
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Zip your entire collection and save it to a safe place.")
                    
                    OutlinedButton(
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Folder, null)
                        Spacer(Modifier.width(8.dp))
                        Text(selectedPath?.path ?: "Select backup destination")
                    }

                    Button(
                        onClick = { viewModel.createBackup() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = selectedPath != null && uiState !is BackupUiState.Processing,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Create Backup (.zip)")
                    }
                }
            }

            // Restore Backup Section
            SectionTitle("Import Backup", Icons.Default.CloudDownload)
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Restore your stickers from a previously created ZIP file.")
                    
                    OutlinedButton(
                        onClick = { filePicker.launch("application/zip") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FilePresent, null)
                        Spacer(Modifier.width(8.dp))
                        Text(selectedFile?.lastPathSegment ?: "Select .zip file")
                    }

                    Button(
                        onClick = { viewModel.restoreBackup() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = selectedFile != null && uiState !is BackupUiState.Processing,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Restore Backup")
                    }
                    
                    WarningCard("Restoring will merge with your existing packs. Duplicate IDs might be overwritten.")
                }
            }

            // Status feedback
            AnimatedVisibility(visible = uiState !is BackupUiState.Idle) {
                StatusCard(uiState, onDismiss = { viewModel.dismiss() })
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WarningCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun StatusCard(state: BackupUiState, onDismiss: () -> Unit) {
    val color = when(state) {
        is BackupUiState.Success -> Color(0xFF4CAF50)
        is BackupUiState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state is BackupUiState.Processing) {
                CircularProgressIndicator()
                Text("Please wait...", Modifier.padding(top = 16.dp))
            } else {
                val msg = if (state is BackupUiState.Success) state.message else (state as? BackupUiState.Error)?.message ?: ""
                Text(msg, color = color, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
