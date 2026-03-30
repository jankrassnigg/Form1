package com.form1.musicplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.form1.musicplayer.onedrive.OneDriveAuthManager
import com.form1.musicplayer.ui.AppNavigationDrawer
import com.form1.musicplayer.ui.NavigationScreen
import com.form1.musicplayer.ui.theme.Form1MusicPlayerTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Form1MusicPlayerTheme {
                SettingsScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val authManager = remember { OneDriveAuthManager(context) }
    var isInitialized by remember { mutableStateOf(false) }
    var isSignedIn by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Not connected") }

    // Initialize and check sign-in status
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            val initialized = authManager.initialize()
            isInitialized = initialized

            if (initialized) {
                isSignedIn = authManager.isSignedIn()
                statusMessage = if (isSignedIn) {
                    val account = authManager.getCurrentAccount()
                    "Connected as ${account?.username ?: "Unknown"}"
                } else {
                    "Not connected"
                }
            } else {
                statusMessage = "Failed to initialize"
            }
            isLoading = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppNavigationDrawer(
                context = context,
                currentScreen = NavigationScreen.SETTINGS,
                drawerState = drawerState
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { innerPadding ->
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "App Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // OneDrive Settings Card (placeholder)
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "OneDrive",
                        modifier = Modifier.padding(bottom = 8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "OneDrive Integration",
                        style = MaterialTheme.typography.titleLarge
                    )

                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    } else {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSignedIn) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isSignedIn) {
                        // Browse OneDrive button
                        Button(
                            onClick = {
                                val intent = Intent(context, OneDriveBrowserActivity::class.java)
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Browse OneDrive Music")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Sign out button
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    val success = authManager.signOut()
                                    if (success) {
                                        isSignedIn = false
                                        statusMessage = "Not connected"
                                    } else {
                                        statusMessage = "Sign out failed"
                                    }
                                    isLoading = false
                                }
                            }
                        ) {
                            Text("Sign Out")
                        }
                    } else {
                        // Sign in button
                        Button(
                            onClick = {
                                activity?.let { act ->
                                    scope.launch {
                                        isLoading = true
                                        val result = authManager.signIn(act)
                                        isLoading = false

                                        if (result.success) {
                                            isSignedIn = true
                                            statusMessage = "Connected as ${result.account?.username ?: "Unknown"}"
                                        } else {
                                            statusMessage = "Sign in failed: ${result.error}"
                                        }
                                    }
                                }
                            },
                            enabled = isInitialized && !isLoading
                        ) {
                            Text("Connect to OneDrive")
                        }
                    }
                }
            }

            // App Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Form1 Music Player",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Version: 1.0 (Milestone 3)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
        }
    }
}
