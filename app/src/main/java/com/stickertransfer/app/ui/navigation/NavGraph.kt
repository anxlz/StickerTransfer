package com.stickertransfer.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Import : Screen("import", "Import ZIP", Icons.Default.FolderZip)
    object Preview : Screen("preview/{packId}", "Preview", Icons.Default.Home) {
        fun createRoute(packId: String) = "preview/$packId"
    }
}

val bottomNavItems = listOf(Screen.Home, Screen.Import)
