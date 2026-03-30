package com.form1.musicplayer

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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.form1.musicplayer.data.PlaylistRepository
import com.form1.musicplayer.data.TrackInfo
import com.form1.musicplayer.music.MusicSourceConfig
import com.form1.musicplayer.onedrive.OneDriveAuthManager
import com.form1.musicplayer.onedrive.OneDriveService
import com.form1.musicplayer.player.AudioPlayerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Embeddable OneDrive folder browser composable — used in the Music Library tab.
 * Handles its own state (folder navigation, file selection, auth).
 */
@Composable
fun OneDriveBrowserContent(
    modifier: Modifier = Modifier,
    audioPlayerViewModel: AudioPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authManager = remember { OneDriveAuthManager(context) }
    val oneDriveService = remember { OneDriveService(authManager) }
    val repository = remember { PlaylistRepository.getInstance(context) }
    val musicSourceConfig = remember { MusicSourceConfig(context) }

    var uiState by remember { mutableStateOf<OneDriveUiState>(OneDriveUiState.Loading) }
    var folderStack by remember { mutableStateOf<List<Pair<String?, String>>?>(null) }
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    val currentFolder = folderStack?.lastOrNull()

    // Initialise folder stack from DataStore (use configured music folder if set)
    LaunchedEffect(Unit) {
        val configuredId = musicSourceConfig.oneDriveMusicFolderId.first()
        val configuredPath = musicSourceConfig.oneDriveMusicFolderPath.first()
        folderStack = if (configuredId != null) {
            listOf(configuredId to (configuredPath ?: "Music"))
        } else {
            listOf(null to "OneDrive")
        }
    }

    // Load folder contents whenever the current folder changes
    LaunchedEffect(currentFolder) {
        if (folderStack == null) return@LaunchedEffect
        scope.launch {
            val initialized = authManager.initialize()
            if (!initialized) {
                uiState = OneDriveUiState.Error("Failed to initialize OneDrive")
                return@launch
            }
            if (!authManager.isSignedIn()) {
                uiState = OneDriveUiState.Error("Not signed in to OneDrive.\nGo to Settings to connect.")
                return@launch
            }
            selectedFiles = emptySet()
            loadFolderContents(oneDriveService, currentFolder?.first) { state -> uiState = state }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Folder navigation header (shown when navigated into a sub-folder)
        if ((folderStack?.size ?: 0) > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { folderStack = folderStack!!.dropLast(1) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Up",
                    modifier = Modifier.padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(currentFolder?.second ?: "", style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
        }

        Box(modifier = Modifier.fillMaxSize()) {
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
                            text = "No audio files found here",
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
                        // Summary + action buttons
                        item {
                            val folderCount = state.contents.folders.size
                            val fileCount = state.contents.audioFiles.size
                            val summary = buildString {
                                append("$folderCount folder${if (folderCount != 1) "s" else ""}")
                                if (fileCount > 0) append(", $fileCount song${if (fileCount != 1) "s" else ""}")
                            }
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                if (state.contents.audioFiles.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(onClick = {
                                            scope.launch {
                                                playAllOneDriveFiles(
                                                    state.contents.audioFiles,
                                                    oneDriveService,
                                                    audioPlayerViewModel
                                                )
                                            }
                                        }) {
                                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.padding(end = 8.dp))
                                            Text("Play All")
                                        }
                                        val allIds = state.contents.audioFiles.map { it.id }.toSet()
                                        Button(onClick = {
                                            selectedFiles = if (selectedFiles.containsAll(allIds)) emptySet() else allIds
                                        }) {
                                            Icon(
                                                if (selectedFiles.containsAll(allIds)) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                                null,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text(if (selectedFiles.containsAll(allIds)) "Uncheck All" else "Check All")
                                        }
                                    }
                                }
                            }
                        }

                        // Folders
                        if (state.contents.folders.isNotEmpty()) {
                            items(state.contents.folders) { folder ->
                                OneDriveFolderItem(
                                    folder = folder,
                                    onClick = { folderStack = folderStack!! + (folder.id to folder.name) }
                                )
                            }
                            if (state.contents.audioFiles.isNotEmpty()) {
                                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                            }
                        }

                        // Audio files
                        items(state.contents.audioFiles) { file ->
                            OneDriveFileItem(
                                file = file,
                                isSelected = selectedFiles.contains(file.id),
                                onCheckedChange = { checked ->
                                    selectedFiles = if (checked) selectedFiles + file.id else selectedFiles - file.id
                                },
                                onClick = {
                                    scope.launch {
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

                        // Spacer at bottom so FAB doesn't overlap last item
                        item { Box(modifier = Modifier.padding(bottom = 72.dp)) }
                    }

                    // FAB overlaid inside the Box
                    if (selectedFiles.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = { showPlaylistDialog = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
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

                is OneDriveUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(16.dp)
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                    Text("No playlists available. Create one from the Playlists screen.")
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
                                            Icons.AutoMirrored.Filled.QueueMusic,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 16.dp)
                                        )
                                        Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }
}
