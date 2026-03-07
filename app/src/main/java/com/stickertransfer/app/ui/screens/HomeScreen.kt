package com.stickertransfer.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stickertransfer.app.data.model.StickerPack
import com.stickertransfer.app.ui.viewmodels.HomeUiState
import com.stickertransfer.app.ui.viewmodels.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar by viewModel.snackbar.collectAsState()
    val botToken by viewModel.botToken.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var linkInput by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf(botToken) }

    LaunchedEffect(snackbar) {
        snackbar?.let {
            snackbarHostState.showSnackbar(it.text)
            viewModel.clearSnackbar()
        }
    }
    LaunchedEffect(botToken) { tokenInput = botToken }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "StickerTransfer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Input card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Load Telegram Sticker Pack",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = linkInput,
                        onValueChange = { linkInput = it },
                        label = { Text("Paste Telegram Sticker Pack Link") },
                        placeholder = { Text("https://t.me/addstickers/PackName") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Link, null) },
                        trailingIcon = {
                            if (linkInput.isNotEmpty()) {
                                IconButton(onClick = { linkInput = "" }) {
                                    Icon(Icons.Default.Clear, "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = { viewModel.loadStickerPack(linkInput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = linkInput.isNotBlank() &&
                                uiState !is HomeUiState.Loading &&
                                uiState !is HomeUiState.Downloading,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Load Sticker Pack", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // State-driven content
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(200))
                },
                label = "state_transition"
            ) { state ->
                when (state) {
                    is HomeUiState.Loading -> LoadingCard("Fetching sticker pack…")
                    is HomeUiState.Downloading -> DownloadProgressCard(state.current, state.total)
                    is HomeUiState.PackLoaded -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Pack Split into ${state.packs.size} Parts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        state.packs.forEach { pack ->
                            PackPreviewCard(
                                pack = pack,
                                isDownloaded = false,
                                onAction = { viewModel.downloadPack(pack) },
                                onActionBusiness = { viewModel.downloadPack(pack) },
                                onRename = { newName -> viewModel.renamePack(pack, newName) },
                                onRemove = { viewModel.removePack(pack) }
                            )
                        }
                    }
                    is HomeUiState.Downloaded -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        state.packs.forEach { pack ->
                            PackPreviewCard(
                                pack = pack,
                                isDownloaded = true,
                                onAction = { viewModel.addToWhatsApp(pack, false) },
                                onActionBusiness = { viewModel.addToWhatsApp(pack, true) },
                                onRename = { newName -> viewModel.renamePack(pack, newName) },
                                onRemove = { viewModel.removePack(pack) }
                            )
                        }
                    }
                    is HomeUiState.Error -> ErrorCard(state.message) { viewModel.dismissError() }
                    HomeUiState.Idle -> BotTokenHint(hasToken = botToken.isNotBlank()) {
                        showSettingsDialog = true
                    }
                }
            }
        }
    }

    // Settings dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            icon = { Icon(Icons.Outlined.Settings, null) },
            title = { Text("Telegram Bot Token") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "To download stickers, you need a Telegram Bot Token.\n\n" +
                        "1. Open Telegram and search for @BotFather\n" +
                        "2. Send /newbot and follow instructions\n" +
                        "3. Copy the token and paste it below",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Bot Token") },
                        placeholder = { Text("1234567890:ABC-DEF…") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveBotToken(tokenInput.trim())
                    showSettingsDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LoadingCard(message: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DownloadProgressCard(current: Int, total: Int) {
    val progress = if (total > 0) current.toFloat() / total else 0f
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Downloading stickers…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Text(
                "$current / $total stickers",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PackPreviewCard(
    pack: StickerPack,
    isDownloaded: Boolean,
    onAction: () -> Unit,
    onActionBusiness: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var newNameInput by remember { mutableStateOf(pack.name) }
    var showMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Pack header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tray icon or first sticker preview
                val firstSticker = pack.stickers.firstOrNull()
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    if (firstSticker != null) {
                        val imageModel = if (isDownloaded && firstSticker.localPath.isNotEmpty())
                            firstSticker.localPath
                        else
                            "https://t.me/i/stickers/${pack.identifier.substringBefore("_p")}/1.webp"
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        pack.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${pack.stickers.size} stickers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = {
                                showMenu = false
                                onRemove()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            if (isDownloaded && pack.stickers.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(100.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    userScrollEnabled = false
                ) {
                    items(pack.stickers.take(5)) { sticker ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(sticker.localPath)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val label = if (isDownloaded) "WhatsApp" else "Download"
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
                if (isDownloaded) {
                    OutlinedButton(
                        onClick = onActionBusiness,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Business", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Pack") },
            text = {
                OutlinedTextField(
                    value = newNameInput,
                    onValueChange = { newNameInput = it },
                    label = { Text("New Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRename(newNameInput)
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                Text("Error", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun BotTokenHint(hasToken: Boolean, onSetupToken: () -> Unit) {
    if (!hasToken) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.secondary)
                    Text("Setup Required", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Text("A free Telegram Bot Token is required. Tap Settings to get started.", style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onSetupToken) {
                    Icon(Icons.Outlined.Settings, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Configure Bot Token")
                }
            }
        }
    }
}
