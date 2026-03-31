package com.form1.musicplayer

import android.app.Activity
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.form1.musicplayer.data.PlaylistRepository
import com.form1.musicplayer.data.TrackInfo
import com.form1.musicplayer.music.MusicSourceConfig
import com.form1.musicplayer.onedrive.OneDriveAuthManager
import com.form1.musicplayer.onedrive.OneDriveFile
import com.form1.musicplayer.onedrive.OneDriveFolder
import com.form1.musicplayer.onedrive.OneDriveFolderContents
import com.form1.musicplayer.onedrive.OneDriveService
import com.form1.musicplayer.player.AudioPlayerViewModel
import com.form1.musicplayer.player.Track
import com.form1.musicplayer.ui.AppNavigationDrawer
import com.form1.musicplayer.ui.NavigationScreen
import com.form1.musicplayer.ui.PlayerBar
import com.form1.musicplayer.ui.theme.Form1MusicPlayerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OneDriveBrowserActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PICKER_TITLE = "pickerTitle"
        const val MODE_FOLDER_PICKER = "FOLDER_PICKER"
        const val RESULT_FOLDER_ID = "folderId"
        const val RESULT_FOLDER_NAME = "folderName"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isFolderPickerMode = intent.getStringExtra(EXTRA_MODE) == MODE_FOLDER_PICKER
        val pickerTitle = intent.getStringExtra(EXTRA_PICKER_TITLE) ?: "Select Folder"
        setContent {
            Form1MusicPlayerTheme {
                OneDriveBrowserScreen(
                    isFolderPickerMode = isFolderPickerMode,
                    pickerTitle = pickerTitle,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneDriveBrowserScreen(
    isFolderPickerMode: Boolean = false,
    pickerTitle: String = "Select Folder",
    onBackClick: () -> Unit,
    audioPlayerViewModel: AudioPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val authManager = remember { OneDriveAuthManager(context) }
    val oneDriveService = remember { OneDriveService(authManager) }
    val repository = remember { PlaylistRepository.getInstance(context) }
    val musicSourceConfig = remember { MusicSourceConfig(context) }

    var uiState by remember { mutableStateOf<OneDriveUiState>(OneDriveUiState.Loading) }

    // null = not yet initialised from DataStore; initialised in LaunchedEffect(Unit) below
    var folderStack by remember { mutableStateOf<List<Pair<String?, String>>?>(null) }

    // Selection state (normal browse mode only)
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    // New folder dialog (folder picker mode only)
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderError by remember { mutableStateOf<String?>(null) }
    var isCreatingFolder by remember { mutableStateOf(false) }

    val currentFolder = folderStack?.lastOrNull()

    // Read configured music folder from DataStore and set initial stack
    LaunchedEffect(Unit) {
        val configuredId = musicSourceConfig.oneDriveMusicFolderId.first()
        val configuredPath = musicSourceConfig.oneDriveMusicFolderPath.first()
        folderStack = if (!isFolderPickerMode && configuredId != null) {
            listOf(configuredId to (configuredPath ?: "Music"))
        } else {
            listOf(null to "OneDrive")
        }
    }

    // Load folder contents whenever the current folder changes (skip while stack is uninitialised)
    LaunchedEffect(currentFolder) {
        if (folderStack == null) return@LaunchedEffect
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
            selectedFiles = emptySet()
            loadFolderContents(oneDriveService, currentFolder?.first) { state -> uiState = state }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isFolderPickerMode,
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
                    title = {
                        Text(
                            if (isFolderPickerMode) pickerTitle
                            else currentFolder?.second ?: "OneDrive"
                        )
                    },
                    navigationIcon = {
                        if (isFolderPickerMode) {
                            IconButton(onClick = { activity?.finish() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    },
                    actions = {
                        if ((folderStack?.size ?: 0) > 1) {
                            IconButton(onClick = { folderStack = folderStack!!.dropLast(1) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                            }
                        }
                        if (isFolderPickerMode) {
                            IconButton(onClick = {
                                newFolderName = ""
                                newFolderError = null
                                showNewFolderDialog = true
                            }) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                            }
                        } else {
                            IconButton(onClick = {
                                scope.launch {
                                    loadFolderContents(oneDriveService, currentFolder?.first) { state -> uiState = state }
                                }
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            bottomBar = { if (!isFolderPickerMode) PlayerBar() },
            floatingActionButton = {
                if (isFolderPickerMode) {
                    FloatingActionButton(
                        onClick = {
                            val result = Intent().apply {
                                putExtra(OneDriveBrowserActivity.RESULT_FOLDER_ID, currentFolder?.first ?: "")
                                putExtra(OneDriveBrowserActivity.RESULT_FOLDER_NAME, currentFolder?.second ?: "OneDrive")
                            }
                            activity?.setResult(Activity.RESULT_OK, result)
                            activity?.finish()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Select folder")
                            Text("Select This Folder")
                        }
                    }
                } else if (selectedFiles.isNotEmpty()) {
                    FloatingActionButton(onClick = { showPlaylistDialog = true }) {
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
                                text = if (isFolderPickerMode) "No folders found" else "No audio files found",
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
                            // Summary and action buttons
                            item {
                                val folderCount = state.contents.folders.size
                                val fileCount = state.contents.audioFiles.size
                                val summary = buildString {
                                    append("$folderCount folder${if (folderCount != 1) "s" else ""}")
                                    if (!isFolderPickerMode && fileCount > 0) {
                                        append(", $fileCount song${if (fileCount != 1) "s" else ""}")
                                    }
                                }
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    if (!isFolderPickerMode && state.contents.audioFiles.isNotEmpty()) {
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
                                        onClick = {
                                            folderStack = folderStack!! + (folder.id to folder.name)
                                        }
                                    )
                                }
                                if (!isFolderPickerMode && state.contents.audioFiles.isNotEmpty()) {
                                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                                }
                            }

                            // Audio files — not shown in folder picker mode
                            if (!isFolderPickerMode) {
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

        // New Folder dialog (folder picker mode only)
        if (isFolderPickerMode && showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { if (!isCreatingFolder) showNewFolderDialog = false },
                title = { Text("New Folder") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it; newFolderError = null },
                            label = { Text("Folder name") },
                            singleLine = true,
                            enabled = !isCreatingFolder
                        )
                        newFolderError?.let { err ->
                            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (isCreatingFolder) {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val name = newFolderName.trim()
                            if (name.isEmpty()) { newFolderError = "Please enter a folder name"; return@Button }
                            scope.launch {
                                isCreatingFolder = true
                                val result = oneDriveService.createFolder(currentFolder?.first, name)
                                isCreatingFolder = false
                                result.fold(
                                    onSuccess = { folder ->
                                        showNewFolderDialog = false
                                        // Navigate into the newly created folder
                                        folderStack = folderStack!! + (folder.id to folder.name)
                                    },
                                    onFailure = { newFolderError = it.message ?: "Failed to create folder" }
                                )
                            }
                        },
                        enabled = !isCreatingFolder
                    ) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }, enabled = !isCreatingFolder) { Text("Cancel") }
                }
            )
        }

        // Playlist selection dialog (normal browse mode only)
        if (!isFolderPickerMode && showPlaylistDialog) {
            val currentState = uiState
            val currentFiles = if (currentState is OneDriveUiState.Success) currentState.contents.audioFiles else emptyList()
            AddToPlaylistDialog(
                selectedCount = selectedFiles.size,
                selectedFileNames = currentFiles.filter { selectedFiles.contains(it.id) }.map { it.name },
                repository = repository,
                onAddToPlaylist = { playlistId ->
                    scope.launch {
                        val tracks = currentFiles
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
                        selectedFiles = emptySet()
                        showPlaylistDialog = false
                    }
                },
                onDismiss = { showPlaylistDialog = false }
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

internal suspend fun loadFolderContents(
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

internal suspend fun playAllOneDriveFiles(
    files: List<OneDriveFile>,
    service: OneDriveService,
    playerViewModel: AudioPlayerViewModel,
    startIndex: Int = 0
) {
    if (files.isEmpty()) return
    val tracks = files.mapNotNull { file ->
        val downloadUrl = file.downloadUrl ?: service.getDownloadUrl(file.id).getOrNull()
        if (downloadUrl != null) {
            Track(uri = android.net.Uri.parse(downloadUrl), title = file.name, id = file.id)
        } else null
    }
    if (tracks.isNotEmpty()) {
        playerViewModel.playQueue(tracks, startIndex)
    }
}

internal fun formatFileSize(bytes: Long): String {
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
