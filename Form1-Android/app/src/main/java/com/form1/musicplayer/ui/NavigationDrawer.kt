package com.form1.musicplayer.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.form1.musicplayer.*
import kotlinx.coroutines.launch

/**
 * Reusable navigation drawer content
 * @param context Application context
 * @param currentScreen Which screen is currently active (for selection highlighting)
 * @param drawerState The drawer state to close after navigation
 */
@Composable
fun AppNavigationDrawer(
    context: Context,
    currentScreen: NavigationScreen,
    drawerState: DrawerState
) {
    val scope = rememberCoroutineScope()

    ModalDrawerSheet {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Form1 Music Player",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text("Player") },
                selected = currentScreen == NavigationScreen.PLAYER,
                onClick = {
                    scope.launch { drawerState.close() }
                    if (currentScreen != NavigationScreen.PLAYER) {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                label = { Text("Playlists") },
                selected = currentScreen == NavigationScreen.PLAYLISTS,
                onClick = {
                    scope.launch { drawerState.close() }
                    if (currentScreen != NavigationScreen.PLAYLISTS) {
                        val intent = Intent(context, PlaylistsActivity::class.java)
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                label = { Text("Music Library") },
                selected = currentScreen == NavigationScreen.LIBRARY,
                onClick = {
                    scope.launch { drawerState.close() }
                    if (currentScreen != NavigationScreen.LIBRARY) {
                        val intent = Intent(context, FileBrowserActivity::class.java)
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Settings") },
                selected = currentScreen == NavigationScreen.SETTINGS,
                onClick = {
                    scope.launch { drawerState.close() }
                    if (currentScreen != NavigationScreen.SETTINGS) {
                        val intent = Intent(context, SettingsActivity::class.java)
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}

/**
 * Enum for navigation screens
 */
enum class NavigationScreen {
    PLAYER,
    PLAYLISTS,
    LIBRARY,
    ONEDRIVE,
    SETTINGS
}
