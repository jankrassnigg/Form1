package com.form1.musicplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.form1.musicplayer.onedrive.OneDriveAuthManager
import com.form1.musicplayer.onedrive.OneDriveFile
import com.form1.musicplayer.onedrive.OneDriveFolder
import com.form1.musicplayer.onedrive.OneDriveFolderContents
import com.form1.musicplayer.onedrive.OneDriveService
import com.form1.musicplayer.data.PlaylistRepository
import com.form1.musicplayer.data.TrackInfo
import com.form1.musicplayer.player.AudioPlayerViewModel
import com.form1.musicplayer.player.Track
import com.form1.musicplayer.ui.AppNavigationDrawer
import com.form1.musicplayer.ui.NavigationScreen
import com.form1.musicplayer.ui.theme.Form1MusicPlayerTheme
import kotlinx.coroutines.launch

class OneDriveBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Form1MusicPlayerTheme {
                OneDriveBrowserScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneDriveBrowserScreen(
    onBackClick: () -> Unit,
    audioPlayerViewModel: AudioPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val authManager = remember { OneDriveAuthManager(context) }
    val oneDriveService = remember { OneDriveService(authManager) }
    val repository = remember { PlaylistRepository.getInstance(context) }

    var uiState by remember { mutableStateOf<OneDriveUiState>(OneDriveUiState.Loading) }

    // Navigation stack: list of (folderId, folderName) pairs
    var folderStack by remember { mutableStateOf(listOf<Pair<String?, String>>(null to "OneDrive")) }

    // Selection state
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    // Current folder
    val currentFolder = folderStack.lastOrNull()

    // Initialize and load files
    LaunchedEffect(currentFolder) {
        scope.launch {
            val initialized = authManager.initialize()
            if (!initialized) {
                uiState = OneDriveUiState.Error("Failed to initialize OneDrive")
                return@launch
            }

            val isSignedIn = authManager.isSignedIn()
            if (!isSignedIn) {
                uiState = OneDriveUiState.Error("Not signed in to OneDrive")
                return@launch
            }

            // Clear selection when navigating
            selectedFiles = emptySet()
            loadFolderContents(oneDriveService, currentFolder?.first) { state -> uiState = state }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppNavigationDrawer(
                context = context,
                currentScreen = NavigationScreen.ONEDRIVE,
                drawerState = drawerState
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(currentFolder?.second ?: "OneDrive") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu"
                            )
                        }
                    },
                    actions = {
                        // Show back button when not at root
                        if (folderStack.size > 1) {
                            IconButton(
                                onClick = {
                                    folderStack = folderStack.dropLast(1)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    loadFolderContents(oneDriveService, currentFolder?.first) { state -> uiState = state }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            floatingActionButton = {
                if (selectedFiles.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { showPlaylistDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to Playlist")
                            Text("Add ${selectedFiles.size}")
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is OneDriveUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Loading OneDrive files...",
                            modifier = Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is OneDriveUiState.Empty -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(16.dp)
                        )
                        Text(
                            text = "No audio files found in OneDrive",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                is OneDriveUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Summary and Action buttons
                        if (state.contents.audioFiles.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "${state.contents.folders.size} folder${if (state.contents.folders.size != 1) "s" else ""}, " +
                                                "${state.contents.audioFiles.size} song${if (state.contents.audioFiles.size != 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                // Play all files in current folder as a queue
                                                scope.launch {
                                                    playAllOneDriveFiles(
                                                        state.contents.audioFiles,
                                                        oneDriveService,
                                                        audioPlayerViewModel
                                                    )
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text("Play All")
                                        }

                                        Button(
                                            onClick = {
                                                val allFileIds = state.contents.audioFiles.map { it.id }.toSet()
                                                selectedFiles = if (selectedFiles.containsAll(allFileIds)) {
                                                    emptySet()
                                                } else {
                                                    allFileIds
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selectedFiles.containsAll(state.contents.audioFiles.map { it.id }.toSet()))
                                                    Icons.Default.CheckCircle
                                                else
                                                    Icons.Outlined.Circle,
                                                contentDescription = null,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text(if (selectedFiles.containsAll(state.contents.audioFiles.map { it.id }.toSet())) "Uncheck All" else "Check All")
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                Text(
                                    text = "${state.contents.folders.size} folder${if (state.contents.folders.size != 1) "s" else ""}",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        // Folders
                        if (state.contents.folders.isNotEmpty()) {
                            items(state.contents.folders) { folder ->
                                OneDriveFolderItem(
                                    folder = folder,
                                    onClick = {
                                        // Navigate into folder
                                        folderStack = folderStack + (folder.id to folder.name)
                                    }
                                )
                            }

                            if (state.contents.audioFiles.isNotEmpty()) {
                                item {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }
                        }

                        // Audio files
                        items(state.contents.audioFiles) { file ->
                            OneDriveFileItem(
                                file = file,
                                isSelected = selectedFiles.contains(file.id),
                                onCheckedChange = { checked ->
                                    selectedFiles = if (checked) {
                                        selectedFiles + file.id
                                    } else {
                                        selectedFiles - file.id
                                    }
                                },
                                onClick = {
                                    scope.launch {
                                        // Play this file with rest of folder as queue
                                        val fileIndex = state.contents.audioFiles.indexOf(file)
                                        playAllOneDriveFiles(
                                            state.contents.audioFiles,
                                            oneDriveService,
                                            audioPlayerViewModel,
                                            startIndex = fileIndex
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                is OneDriveUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Error: ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
        }

        // Playlist selection dialog
        if (showPlaylistDialog) {
            val playlists by repository.getAllPlaylists().collectAsState(initial = emptyList())

            AlertDialog(
                onDismissRequest = { showPlaylistDialog = false },
                title = { Text("Add to Playlist") },
                text = {
                    if (playlists.isEmpty()) {
                        Text("No playlists available. Create one first from the Playlists screen.")
                    } else {
                        Column {
                            Text("Select a playlist to add ${selectedFiles.size} song${if (selectedFiles.size != 1) "s" else ""}:")
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                            ) {
                                items(playlists) { playlist ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                scope.launch {
                                                    // Get selected files info
                                                    val currentState = uiState
                                                    if (currentState is OneDriveUiState.Success) {
                                                        val selectedFilesInfo = currentState.contents.audioFiles
                                                            .filter { selectedFiles.contains(it.id) }
                                                            .map { file ->
                                                                TrackInfo(
                                                                    title = file.name,
                                                                    source = "onedrive",
                                                                    uri = file.downloadUrl ?: "",
                                                                    sourceId = file.id
                                                                )
                                                            }

                                                        repository.addTracksToPlaylist(playlist.id, selectedFilesInfo)
                                                        selectedFiles = emptySet()
                                                        showPlaylistDialog = false
                                                    }
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 16.dp)
                                            )
                                            Text(
                                                text = playlist.name,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showPlaylistDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun OneDriveFolderItem(
    folder: OneDriveFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (folder.childCount > 0) {
                    Text(
                        text = "${folder.childCount} item${if (folder.childCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun OneDriveFileItem(
    file: OneDriveFile,
    isSelected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(end = 8.dp)
            )

            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = formatFileSize(file.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

private suspend fun loadFolderContents(
    service: OneDriveService,
    folderId: String?,
    updateState: (OneDriveUiState) -> Unit
) {
    updateState(OneDriveUiState.Loading)

    val result = service.listFolderContents(folderId)
    result.fold(
        onSuccess = { contents ->
            updateState(
                if (contents.folders.isEmpty() && contents.audioFiles.isEmpty()) {
                    OneDriveUiState.Empty
                } else {
                    OneDriveUiState.Success(contents)
                }
            )
        },
        onFailure = { error ->
            updateState(OneDriveUiState.Error(error.message ?: "Unknown error"))
        }
    )
}

/**
 * Play all OneDrive files as a queue
 */
private suspend fun playAllOneDriveFiles(
    files: List<OneDriveFile>,
    service: OneDriveService,
    playerViewModel: AudioPlayerViewModel,
    startIndex: Int = 0
) {
    if (files.isEmpty()) return

    // Build queue of tracks with download URLs
    val tracks = files.mapNotNull { file ->
        val downloadUrl = file.downloadUrl ?: service.getDownloadUrl(file.id).getOrNull()
        if (downloadUrl != null) {
            Track(
                uri = android.net.Uri.parse(downloadUrl),
                title = file.name,
                id = file.id
            )
        } else {
            null
        }
    }

    if (tracks.isNotEmpty()) {
        playerViewModel.playQueue(tracks, startIndex)
    }
}

private suspend fun playOneDriveFile(
    file: OneDriveFile,
    service: OneDriveService,
    playerViewModel: AudioPlayerViewModel
) {
    // Use cached download URL if available
    val downloadUrl = file.downloadUrl ?: run {
        // Otherwise fetch it
        val result = service.getDownloadUrl(file.id)
        result.getOrNull()
    }

    if (downloadUrl != null) {
        val uri = android.net.Uri.parse(downloadUrl)
        playerViewModel.playAudio(uri, file.name)
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}

sealed class OneDriveUiState {
    object Loading : OneDriveUiState()
    object Empty : OneDriveUiState()
    data class Success(val contents: OneDriveFolderContents) : OneDriveUiState()
    data class Error(val message: String) : OneDriveUiState()
}
