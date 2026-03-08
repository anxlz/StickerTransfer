package com.stickertransfer.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.stickertransfer.app.BuildConfig
import com.stickertransfer.app.data.network.PreferencesRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefsRepo: PreferencesRepository) {
    val botToken by prefsRepo.botTokenFlow.collectAsState("")
    val zipPath by prefsRepo.zipPathFlow.collectAsState("Downloads")
    val scope = rememberCoroutineScope()
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Download Settings", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            
            SettingsItem(
                title = "Telegram Bot Token",
                subtitle = if (botToken.isEmpty()) "Not set" else "••••••••••••",
                icon = Icons.Default.Key,
                onClick = {
                    tokenInput = botToken
                    showTokenDialog = true
                }
            )

            SettingsItem(
                title = "ZIP Download Path",
                subtitle = zipPath,
                icon = Icons.Default.Download,
                onClick = { /* ACTION_OPEN_DOCUMENT_TREE logic */ }
            )

            Spacer(Modifier.height(16.dp))
            Text("About", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = "https://avatars.githubusercontent.com/u/anxlz",
                        contentDescription = "Avatar",
                        modifier = Modifier.size(80.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("anxlz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Lead Android Engineer", style = MaterialTheme.typography.bodySmall)
                    
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { uriHandler.openUri("https://github.com/anxlz") },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Code, null)
                            Spacer(Modifier.width(8.dp))
                            Text("GitHub")
                        }
                        Button(
                            onClick = { uriHandler.openUri("https://github.com/sponsors/anxlz") },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081))
                        ) {
                            Icon(Icons.Default.Favorite, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Support")
                        }
                    }
                    
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        modifier = Modifier.padding(top = 20.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Update Bot Token") },
            text = {
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Bot Token") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch { prefsRepo.saveBotToken(tokenInput) }
                    showTokenDialog = false
                }) { Text("Save") }
            }
        )
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
