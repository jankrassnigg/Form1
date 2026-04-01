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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import android.widget.Toast
import com.form1.musicplayer.data.PlaylistRepository
import com.form1.musicplayer.data.TrackInfo
import com.form1.musicplayer.music.MusicSourceConfig
import com.form1.musicplayer.onedrive.OneDriveCacheManager
import com.form1.musicplayer.onedrive.OneDriveFile
import com.form1.musicplayer.player.AudioPlayerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Embeddable OneDrive folder browser with search — used in the Music Library tab.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OneDriveBrowserContent(
    modifier: Modifier = Modifier,
    audioPlayerViewModel: AudioPlayerViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val cacheManager = remember { OneDriveCacheManager.getInstance(context) }
    val repository = remember { PlaylistRepository.getInstance(context) }
    val musicSourceConfig = remember { MusicSourceConfig(context) }

    // Folder browser state
    var uiState by remember { mutableStateOf<OneDriveUiState>(OneDriveUiState.Loading) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    var folderStack by remember { mutableStateOf<List<Pair<String?, String>>?>(null) }
    var rootFolderId by remember { mutableStateOf<String?>(null) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<OneDriveFile>?>(null) } // null = browse mode
    var isSearchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // Selection + dialog state (shared between browse and search modes)
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    // The files currently visible (browse folder files or search results)
    var visibleFiles by remember { mutableStateOf<List<OneDriveFile>>(emptyList()) }

    val currentFolder = folderStack?.lastOrNull()

    // Initialise folder stack from DataStore
    LaunchedEffect(Unit) {
        val configuredId = musicSourceConfig.oneDriveMusicFolderId.first()
        val configuredPath = musicSourceConfig.oneDriveMusicFolderPath.first()
        rootFolderId = configuredId
        folderStack = if (configuredId != null) {
            listOf(configuredId to (configuredPath ?: "Music"))
        } else {
            listOf(null to "OneDrive")
        }
    }

    // Load folder contents whenever the current folder changes (browse mode only)
    LaunchedEffect(currentFolder) {
        if (folderStack == null || searchResults != null) return@LaunchedEffect
        scope.launch {
            val initialized = cacheManager.auth.initialize()
            if (!initialized) {
                uiState = OneDriveUiState.Error("Failed to initialize OneDrive")
                return@launch
            }
            if (!cacheManager.auth.isSignedIn()) {
                uiState = OneDriveUiState.Error("Not signed in to OneDrive.\nGo to Settings to connect.")
                return@launch
            }
            selectedFiles = emptySet()
            loadFolderContents(cacheManager, currentFolder?.first) { state ->
                uiState = state
                if (state is OneDriveUiState.Success) visibleFiles = state.contents.audioFiles
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Search bar ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search OneDrive music...") },
                singleLine = true,
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchResults = null
                            searchError = null
                            selectedFiles = emptySet()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                } else null
            )
            Button(
                onClick = {
                    if (searchQuery.isBlank()) return@Button
                    scope.launch {
                        isSearchLoading = true
                        searchError = null
                        selectedFiles = emptySet()
                        val result = cacheManager.searchAudioFiles(searchQuery.trim(), rootFolderId)
                        result.fold(
                            onSuccess = { files ->
                                searchResults = files
                                visibleFiles = files
                            },
                            onFailure = { e ->
                                searchError = e.message ?: "Search failed"
                                searchResults = emptyList()
                                visibleFiles = emptyList()
                            }
                        )
                        isSearchLoading = false
                    }
                },
                enabled = searchQuery.isNotBlank() && !isSearchLoading
            ) {
                if (isSearchLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 4.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                }
                Text("Search")
            }
        }

        HorizontalDivider()

        // ── Content: search results or folder browser ────────────────────────
        if (searchResults != null) {
            // Search results mode
            SearchResultsContent(
                query = searchQuery,
                results = searchResults!!,
                error = searchError,
                selectedFiles = selectedFiles,
                onSelectionChanged = { id, checked ->
                    selectedFiles = if (checked) selectedFiles + id else selectedFiles - id
                },
                onPlayFile = { file ->
                    scope.launch {
                        val idx = searchResults!!.indexOf(file)
                        playAllOneDriveFiles(searchResults!!, cacheManager, audioPlayerViewModel, startIndex = idx)
                    }
                },
                onPlayAll = {
                    scope.launch {
                        playAllOneDriveFiles(searchResults!!, cacheManager, audioPlayerViewModel)
                    }
                },
                onSelectAll = {
                    val allIds = searchResults!!.map { it.id }.toSet()
                    selectedFiles = if (selectedFiles.containsAll(allIds)) emptySet() else allIds
                },
                onAddToPlaylist = { showPlaylistDialog = true },
                modifier = Modifier.weight(1f)
            )
        } else {
            // Browse mode
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        cacheManager.invalidateFolder(currentFolder?.first)
                        loadFolderContents(cacheManager, currentFolder?.first) { state ->
                            uiState = state
                            if (state is OneDriveUiState.Success) visibleFiles = state.contents.audioFiles
                        }
                        isRefreshing = false
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Folder navigation header
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

                    when (val state = uiState) {
                        is OneDriveUiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Text(
                                        "Loading OneDrive files...",
                                        modifier = Modifier.padding(top = 16.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        is OneDriveUiState.Empty -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                    Text(
                                        "No audio files found here",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        is OneDriveUiState.Success -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    val fc = state.contents.folders.size
                                    val ac = state.contents.audioFiles.size
                                    val summary = buildString {
                                        append("$fc folder${if (fc != 1) "s" else ""}")
                                        if (ac > 0) append(", $ac song${if (ac != 1) "s" else ""}")
                                    }
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                        if (state.contents.audioFiles.isNotEmpty()) {
                                            val allIds = state.contents.audioFiles.map { it.id }.toSet()
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(onClick = {
                                                    scope.launch {
                                                        playAllOneDriveFiles(state.contents.audioFiles, cacheManager, audioPlayerViewModel)
                                                    }
                                                }) {
                                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.padding(end = 8.dp))
                                                    Text("Play All")
                                                }
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

                                items(state.contents.folders) { folder ->
                                    OneDriveFolderItem(
                                        folder = folder,
                                        onClick = { folderStack = folderStack!! + (folder.id to folder.name) }
                                    )
                                }

                                if (state.contents.folders.isNotEmpty() && state.contents.audioFiles.isNotEmpty()) {
                                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                                }

                                items(state.contents.audioFiles) { file ->
                                    OneDriveFileItem(
                                        file = file,
                                        isSelected = selectedFiles.contains(file.id),
                                        onCheckedChange = { checked ->
                                            selectedFiles = if (checked) selectedFiles + file.id else selectedFiles - file.id
                                        },
                                        onClick = {
                                            scope.launch {
                                                val idx = state.contents.audioFiles.indexOf(file)
                                                playAllOneDriveFiles(state.contents.audioFiles, cacheManager, audioPlayerViewModel, startIndex = idx)
                                            }
                                        }
                                    )
                                }

                                item { Box(modifier = Modifier.padding(bottom = 72.dp)) }
                            }
                        }

                        is OneDriveUiState.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(16.dp))
                                    Text(state.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }

                // FAB overlaid inside the Box
                if (selectedFiles.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { showPlaylistDialog = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
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
            } // end PullToRefreshBox
        }
    }

    // ── Add to Playlist dialog ───────────────────────────────────────────────
    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            selectedCount = selectedFiles.size,
            selectedFileNames = visibleFiles.filter { selectedFiles.contains(it.id) }.map { it.name },
            repository = repository,
            onAddToPlaylist = { playlistId, playlistName ->
                scope.launch {
                    val tracks = visibleFiles
                        .filter { selectedFiles.contains(it.id) }
                        .map { file ->
                            TrackInfo(
                                title = file.name,
                                source = "onedrive",
                                uri = file.downloadUrl ?: "",
                                sourceId = file.id
                            )
                        }
                    repository.addTracksToPlaylist(playlistId, tracks)
                    val n = tracks.size
                    Toast.makeText(
                        context,
                        "Added $n song${if (n != 1) "s" else ""} to \"$playlistName\"",
                        Toast.LENGTH_SHORT
                    ).show()
                    selectedFiles = emptySet()
                    showPlaylistDialog = false
                }
            },
            onDismiss = { showPlaylistDialog = false }
        )
    }
}

// ── Search results ───────────────────────────────────────────────────────────

@Composable
private fun SearchResultsContent(
    query: String,
    results: List<OneDriveFile>,
    error: String?,
    selectedFiles: Set<String>,
    onSelectionChanged: (String, Boolean) -> Unit,
    onPlayFile: (OneDriveFile) -> Unit,
    onPlayAll: () -> Unit,
    onSelectAll: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            }

            results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(16.dp))
                        Text(
                            "No results for \"$query\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            else -> {
                val allIds = results.map { it.id }.toSet()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                "${results.size} result${if (results.size != 1) "s" else ""} for \"$query\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(onClick = onPlayAll) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.padding(end = 8.dp))
                                    Text("Play All")
                                }
                                Button(onClick = onSelectAll) {
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

                    items(results) { file ->
                        OneDriveFileItem(
                            file = file,
                            isSelected = selectedFiles.contains(file.id),
                            onCheckedChange = { checked -> onSelectionChanged(file.id, checked) },
                            onClick = { onPlayFile(file) }
                        )
                    }

                    item { Box(modifier = Modifier.padding(bottom = 72.dp)) }
                }

                if (selectedFiles.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = onAddToPlaylist,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
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
        }
    }
}

// ── Add to Playlist dialog ───────────────────────────────────────────────────

@Composable
fun AddToPlaylistDialog(
    selectedCount: Int,
    selectedFileNames: List<String>,
    repository: PlaylistRepository,
    onAddToPlaylist: (playlistId: Long, playlistName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val playlists by repository.getAllPlaylists().collectAsState(initial = emptyList())

    var showCreateForm by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            Column {
                Text(
                    "Adding $selectedCount song${if (selectedCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Create new playlist section
                if (showCreateForm) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val name = newPlaylistName.trim()
                                if (name.isNotEmpty()) {
                                    scope.launch {
                                        val id = repository.createPlaylist(name)
                                        onAddToPlaylist(id, name)
                                    }
                                }
                            },
                            enabled = newPlaylistName.isNotBlank()
                        ) {
                            Text("Create & Add")
                        }
                        TextButton(onClick = { showCreateForm = false }) {
                            Text("Cancel")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    TextButton(
                        onClick = {
                            newPlaylistName = suggestPlaylistName(selectedFileNames)
                            showCreateForm = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.padding(end = 8.dp))
                        Text("Create New Playlist")
                    }
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                }

                // Existing playlists
                if (playlists.isEmpty()) {
                    Text(
                        "No playlists yet — create one above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(playlists) { playlist ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onAddToPlaylist(playlist.id, playlist.name) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Suggest a playlist name based on selected file names.
 * If all files share the same "Artist" prefix (from "Artist - Title.ext" pattern), use that.
 */
private fun suggestPlaylistName(fileNames: List<String>): String {
    if (fileNames.isEmpty()) return "New Playlist"
    val artists = fileNames.map { name ->
        val withoutExt = name.substringBeforeLast('.')
        if (" - " in withoutExt) withoutExt.substringBefore(" - ").trim() else ""
    }
    val uniqueArtists = artists.filter { it.isNotBlank() }.toSet()
    return if (uniqueArtists.size == 1) uniqueArtists.first() else "New Playlist"
}
