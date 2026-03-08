package com.stickertransfer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stickertransfer.app.data.network.PreferencesRepository
import com.stickertransfer.app.ui.navigation.Screen
import com.stickertransfer.app.ui.navigation.bottomNavItems
import com.stickertransfer.app.ui.screens.BackupScreen
import com.stickertransfer.app.ui.screens.CreateScreen
import com.stickertransfer.app.ui.screens.HomeScreen
import com.stickertransfer.app.ui.screens.SettingsScreen
import com.stickertransfer.app.ui.theme.StickerTransferTheme
import com.stickertransfer.app.ui.viewmodels.BackupViewModel
import com.stickertransfer.app.ui.viewmodels.CreateViewModel
import com.stickertransfer.app.ui.viewmodels.HomeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StickerTransferTheme {
                StickerTransferApp()
            }
        }
    }
}

@Composable
fun StickerTransferApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val prefsRepo = remember { PreferencesRepository(context) }

    val homeViewModel: HomeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(context) as T
            }
        }
    )

    val createViewModel: CreateViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CreateViewModel(context) as T
            }
        }
    )

    val backupViewModel: BackupViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return BackupViewModel(context) as T
            }
        }
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(viewModel = homeViewModel)
            }
            composable(Screen.Create.route) {
                CreateScreen(viewModel = createViewModel)
            }
            composable(Screen.Backup.route) {
                BackupScreen(viewModel = backupViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(prefsRepo = prefsRepo)
            }
        }
    }
}
