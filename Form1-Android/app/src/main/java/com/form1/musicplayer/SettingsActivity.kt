package com.form1.musicplayer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storage
import com.form1.musicplayer.music.MusicSourceConfig
import com.form1.musicplayer.onedrive.OneDriveAuthManager
import com.form1.musicplayer.onedrive.OneDriveService
import com.form1.musicplayer.profile.ProfileConfig
import com.form1.musicplayer.profile.ProfileManager
import com.form1.musicplayer.profile.StorageType
import com.form1.musicplayer.ui.AppNavigationDrawer
import com.form1.musicplayer.ui.NavigationScreen
import com.form1.musicplayer.ui.PlayerBar
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
    val oneDriveService = remember { OneDriveService(authManager) }
    val musicSourceConfig = remember { MusicSourceConfig(context) }
    val profileConfig = remember { ProfileConfig(context) }
    val profileManager = remember { ProfileManager(context, profileConfig, oneDriveService) }

    var isInitialized by remember { mutableStateOf(false) }
    var isSignedIn by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Not connected") }

    val musicFolderPath by musicSourceConfig.oneDriveMusicFolderPath.collectAsState(initial = null)
    val musicFolderId by musicSourceConfig.oneDriveMusicFolderId.collectAsState(initial = null)

    val profileStorageType by profileConfig.storageType.collectAsState(initial = StorageType.LOCAL)
    val profileFolderPath by profileConfig.oneDriveFolderPath.collectAsState(initial = null)
    var isMigrating by remember { mutableStateOf(false) }
    var migrationError by remember { mutableStateOf<String?>(null) }

    // Launcher for the music folder picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val folderId = result.data?.getStringExtra(OneDriveBrowserActivity.RESULT_FOLDER_ID) ?: ""
            val folderName = result.data?.getStringExtra(OneDriveBrowserActivity.RESULT_FOLDER_NAME) ?: ""
            scope.launch {
                musicSourceConfig.setOneDriveMusicFolder(folderId, folderName)
            }
        }
    }

    // Launcher for the profile folder picker
    val profileFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val folderId = result.data?.getStringExtra(OneDriveBrowserActivity.RESULT_FOLDER_ID) ?: ""
            val folderName = result.data?.getStringExtra(OneDriveBrowserActivity.RESULT_FOLDER_NAME) ?: ""
            scope.launch {
                isMigrating = true
                migrationError = null
                val switchResult = profileManager.switchToOneDrive(folderId, folderName)
                isMigrating = false
                if (switchResult.isFailure) {
                    migrationError = switchResult.exceptionOrNull()?.message ?: "Migration failed"
                }
            }
        }
    }

    // Initialize and check sign-in status
    LaunchedEffect(Unit) {
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
            },
            bottomBar = { PlayerBar() }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "App Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // OneDrive Account Card
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
                            text = "OneDrive Account",
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

                        Spacer(modifier = Modifier.height(4.dp))

                        if (isSignedIn) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        try { authManager.signOut() } catch (_: Exception) { }
                                        // Always treat as signed out — MSAL cache may be stale
                                        isSignedIn = false
                                        statusMessage = "Not connected"
                                        isLoading = false
                                    }
                                }
                            ) {
                                Text("Sign Out")
                            }
                        } else {
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

                // Music Folder Card
                Card(
                    modifier = Modifier.fillMaxWidth().alpha(if (isSignedIn) 1f else 0.4f),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Music Folder",
                            modifier = Modifier.padding(bottom = 8.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "OneDrive Music Folder",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(
                            text = if (musicFolderPath != null) musicFolderPath!! else "Not configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (musicFolderPath != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                val intent = Intent(context, OneDriveBrowserActivity::class.java).apply {
                                    putExtra(OneDriveBrowserActivity.EXTRA_MODE, OneDriveBrowserActivity.MODE_FOLDER_PICKER)
                                    putExtra(OneDriveBrowserActivity.EXTRA_PICKER_TITLE, "Select Music Folder")
                                }
                                folderPickerLauncher.launch(intent)
                            },
                            enabled = isSignedIn
                        ) {
                            Text(if (musicFolderId != null) "Change Music Folder" else "Choose Music Folder")
                        }

                        if (musicFolderId != null) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        musicSourceConfig.clearOneDriveMusicFolder()
                                    }
                                }
                            ) {
                                Text("Clear")
                            }
                        }

                        if (!isSignedIn) {
                            Text(
                                text = "Connect to OneDrive first",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // Profile Storage Card
                Card(
                    modifier = Modifier.fillMaxWidth().alpha(if (isSignedIn) 1f else 0.4f),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Profile Storage",
                            modifier = Modifier.padding(bottom = 8.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "Profile Storage",
                            style = MaterialTheme.typography.titleLarge
                        )

                        val locationLabel = when (profileStorageType) {
                            StorageType.LOCAL -> "Stored on device"
                            StorageType.ONEDRIVE -> profileFolderPath ?: "OneDrive (unknown folder)"
                        }
                        Text(
                            text = locationLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (isMigrating) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                            Text(
                                text = "Migrating profile files…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        migrationError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (profileStorageType == StorageType.ONEDRIVE) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isMigrating = true
                                        migrationError = null
                                        val result = profileManager.switchToLocal()
                                        isMigrating = false
                                        if (result.isFailure) {
                                            migrationError = result.exceptionOrNull()?.message ?: "Switch failed"
                                        }
                                    }
                                },
                                enabled = !isMigrating
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("Store on Device")
                            }
                        }

                        Button(
                            onClick = {
                                val intent = Intent(context, OneDriveBrowserActivity::class.java).apply {
                                    putExtra(OneDriveBrowserActivity.EXTRA_MODE, OneDriveBrowserActivity.MODE_FOLDER_PICKER)
                                    putExtra(OneDriveBrowserActivity.EXTRA_PICKER_TITLE, "Select Profile Folder")
                                }
                                profileFolderPickerLauncher.launch(intent)
                            },
                            enabled = isSignedIn && !isMigrating
                        ) {
                            Text(
                                if (profileStorageType == StorageType.ONEDRIVE) "Change OneDrive Folder"
                                else "Store in OneDrive"
                            )
                        }

                        if (!isSignedIn) {
                            Text(
                                text = "Connect to OneDrive first",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
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
                            text = "Version: 1.0 (Alpha)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
