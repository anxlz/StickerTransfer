package com.stickertransfer.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stickertransfer.app.data.model.StickerPack
import com.stickertransfer.app.ui.viewmodels.CreateUiState
import com.stickertransfer.app.ui.viewmodels.CreateViewModel
import com.stickertransfer.app.utils.WhatsAppUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(viewModel: CreateViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar by viewModel.snackbar.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPackName by remember { mutableStateOf("") }

    LaunchedEffect(snackbar) {
        snackbar?.let {
            snackbarHostState.showSnackbar(it.text)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Sticker Pack", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if (uiState is CreateUiState.FolderList) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Add, "New Pack")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(targetState = uiState, label = "create_state") { state ->
                when (state) {
                    is CreateUiState.FolderList -> FolderList(
                        folders = state.folders,
                        onOpen = { viewModel.openFolder(it) },
                        onRemove = { viewModel.removePack(it.identifier) }
                    )
                    is CreateUiState.FolderDetail -> FolderDetail(
                        pack = state.pack,
                        onBack = { viewModel.backToList() },
                        onImport = { uris, isAnimated -> viewModel.importStickers(uris, state.pack, isAnimated) },
                        onRemoveSticker = { viewModel.removeSticker(it, state.pack) }
                    )
                    is CreateUiState.Processing -> Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Processing stickers...")
                    }
                    else -> {}
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Pack") },
            text = {
                OutlinedTextField(
                    value = newPackName,
                    onValueChange = { newPackName = it },
                    label = { Text("Pack Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newPackName.isNotBlank()) {
                        viewModel.createNewPack(newPackName)
                        newPackName = ""
                        showCreateDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun FolderList(folders: List<StickerPack>, onOpen: (StickerPack) -> Unit, onRemove: (StickerPack) -> Unit) {
    if (folders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Create, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                Text("No custom packs yet", color = MaterialTheme.colorScheme.outline)
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(folders) { pack ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(pack) },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pack.name, fontWeight = FontWeight.Bold)
                            Text("${pack.stickers.size} stickers", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onRemove(pack) }) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderDetail(
    pack: StickerPack,
    onBack: () -> Unit,
    onImport: (List<Uri>, Boolean) -> Unit,
    onRemoveSticker: (com.stickertransfer.app.data.model.Sticker) -> Unit
) {
    val context = LocalContext.current
    val waLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    
    val staticPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) onImport(uris, false)
    }
    val animatedPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) onImport(uris, true)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(pack.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pack.stickers) { sticker ->
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    AsyncImage(
                        model = sticker.localPath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { onRemoveSticker(sticker) },
                        modifier = Modifier.align(Alignment.TopEnd).size(24.dp).padding(4.dp).background(Color.Black.copy(0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }

        Surface(tonalElevation = 4.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { staticPicker.launch("image/*") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("Import Image", fontSize = 11.sp)
                    }
                    Button(onClick = { animatedPicker.launch("image/gif") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("Import GIF", fontSize = 11.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { waLauncher.launch(WhatsAppUtils.buildStickerIntent(context, pack, false)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) { Text("WhatsApp", fontSize = 11.sp) }
                    Button(
                        onClick = { waLauncher.launch(WhatsAppUtils.buildStickerIntent(context, pack, true)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF075E54))
                    ) { Text("Business", fontSize = 11.sp) }
                }
            }
        }
    }
}
