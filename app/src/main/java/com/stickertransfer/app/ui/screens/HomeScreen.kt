package com.stickertransfer.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stickertransfer.app.data.model.Sticker
import com.stickertransfer.app.data.model.StickerPack
import com.stickertransfer.app.ui.viewmodels.HomeUiState
import com.stickertransfer.app.ui.viewmodels.HomeViewModel
import com.stickertransfer.app.utils.WhatsAppUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar by viewModel.snackbar.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var linkInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    val waLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    LaunchedEffect(snackbar) {
        snackbar?.let {
            snackbarHostState.showSnackbar(it.text)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeroBanner()

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LinkInputCard(
                    value = linkInput,
                    onValueChange = { linkInput = it },
                    onPaste = { viewModel.pasteFromClipboard()?.let { linkInput = it } },
                    onLoad = { viewModel.loadStickerPack(linkInput) },
                    isLoading = uiState is HomeUiState.Loading
                )

                AnimatedContent(targetState = uiState, label = "home_state") { state ->
                    when (state) {
                        is HomeUiState.Loading -> LoadingState("Fetching pack info...")
                        is HomeUiState.Downloading -> DownloadProgress(state.current, state.total)
                        is HomeUiState.PackLoaded -> PackDetail(
                            pack = state.pack,
                            parts = state.parts,
                            isDownloaded = state.isDownloaded,
                            onDownload = { viewModel.downloadAll(state.pack, state.parts) },
                            onRemovePart = { viewModel.removePart(it) },
                            waLauncher = waLauncher
                        )
                        is HomeUiState.Error -> ErrorState(state.message) { viewModel.dismissError() }
                        else -> {}
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun HeroBanner() {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "StickerTransfer",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Download & Transfer Telegram Stickers",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingEmoji("🎨", 0)
                FloatingEmoji("😎", 300)
                FloatingEmoji("✨", 600)
            }
        }
    }
}

@Composable
fun FloatingEmoji(emoji: String, delay: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "emoji")
    val yOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, delayMillis = delay, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y"
    )
    Text(emoji, fontSize = 24.sp, modifier = Modifier.offset(y = yOffset.dp))
}

@Composable
fun LinkInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    onLoad: () -> Unit,
    isLoading: Boolean
) {
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Paste Telegram Sticker Pack Link") },
                placeholder = { Text("https://t.me/addstickers/...") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = onPaste) { Icon(Icons.Default.ContentPaste, "Paste") }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Button(
                onClick = onLoad,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !isLoading && value.isNotBlank(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Load Sticker Pack")
            }
        }
    }
}

@Composable
fun LoadingState(msg: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(msg)
    }
}

@Composable
fun DownloadProgress(current: Int, total: Int) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Downloading: $current / $total", fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = { if (total > 0) current.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
fun PackDetail(
    pack: StickerPack,
    parts: List<List<Sticker>>,
    isDownloaded: Boolean,
    onDownload: () -> Unit,
    onRemovePart: (String) -> Unit,
    waLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ElevatedCard(shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pack.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("By ${pack.publisher} • ${pack.stickers.size} stickers")
                }
                if (!isDownloaded) {
                    Button(onClick = onDownload, shape = RoundedCornerShape(12.dp)) {
                        Text("Download All")
                    }
                }
            }
        }

        if (isDownloaded) {
            parts.forEachIndexed { index, stickers ->
                val partId = "${pack.identifier}_part${index + 1}"
                PartCard(
                    title = "Part ${index + 1} (${stickers.size} stickers)",
                    stickers = stickers,
                    onAddWA = { waLauncher.launch(WhatsAppUtils.buildStickerIntent(context, pack.copy(identifier = partId, stickers = stickers), false)) },
                    onAddWAB = { waLauncher.launch(WhatsAppUtils.buildStickerIntent(context, pack.copy(identifier = partId, stickers = stickers), true)) },
                    onRemove = { onRemovePart(partId) }
                )
            }
        }
    }
}

@Composable
fun PartCard(
    title: String,
    stickers: List<Sticker>,
    onAddWA: () -> Unit,
    onAddWAB: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Rename Part") }, onClick = { showMenu = false })
                        DropdownMenuItem(text = { Text("Remove Part", color = Color.Red) }, onClick = { showMenu = false; onRemove() })
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(180.dp),
                contentPadding = PaddingValues(top = 8.dp)
            ) {
                items(stickers) { s ->
                    AsyncImage(
                        model = s.localPath,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddWA, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("WhatsApp", fontSize = 12.sp) }
                OutlinedButton(onClick = onAddWAB, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("WA Business", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun ErrorState(msg: String, onDismiss: () -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            Text(msg, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Dismiss") }
        }
    }
}
