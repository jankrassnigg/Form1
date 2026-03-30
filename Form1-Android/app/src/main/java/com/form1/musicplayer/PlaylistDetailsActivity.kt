package com.form1.musicplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.form1.musicplayer.data.Playlist
import com.form1.musicplayer.data.PlaylistRepository
import com.form1.musicplayer.data.PlaylistTrack
import com.form1.musicplayer.player.AudioPlayerViewModel
import com.form1.musicplayer.player.Track
import com.form1.musicplayer.ui.AppNavigationDrawer
import com.form1.musicplayer.ui.NavigationScreen
import com.form1.musicplayer.ui.theme.Form1MusicPlayerTheme
import kotlinx.coroutines.launch

class PlaylistDetailsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playlistId = intent.getLongExtra("playlist_id", -1L)
        if (playlistId == -1L) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            Form1MusicPlayerTheme {
                PlaylistDetailsScreen(
                    playlistId = playlistId,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailsScreen(
    playlistId: Long,
    onBackClick: () -> Unit,
    audioPlayerViewModel: AudioPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val repository = remember { PlaylistRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var playlist by remember { mutableStateOf<Playlist?>(null) }
    var selectedTracks by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        playlist = repository.getPlaylistWithTracks(playlistId)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppNavigationDrawer(
                context = context,
                currentScreen = NavigationScreen.PLAYLISTS,
                drawerState = drawerState
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(playlist?.name ?: "Playlist") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            floatingActionButton = {
                if (selectedTracks.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { showDeleteDialog = true },
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected"
                            )
                            Text("Delete ${selectedTracks.size}")
                        }
                    }
                }
            }
        ) { innerPadding ->
            playlist?.let { pl ->
            if (pl.tracks.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "No tracks in this playlist",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Add tracks from OneDrive or Music Library",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        // Play All button
                        Button(
                            onClick = {
                                val tracks = pl.tracks.map { track ->
                                    Track(
                                        uri = Uri.parse(track.uri),
                                        title = track.title,
                                        id = track.sourceId
                                    )
                                }
                                audioPlayerViewModel.playQueue(tracks, 0)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play All (${pl.tracks.size} tracks)")
                        }
                    }

                    item {
                        // Check All / Uncheck All button
                        Button(
                            onClick = {
                                val allTrackIds = pl.tracks.map { it.id }.toSet()
                                selectedTracks = if (selectedTracks.containsAll(allTrackIds)) {
                                    emptySet()
                                } else {
                                    allTrackIds
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            val allTrackIds = pl.tracks.map { it.id }.toSet()
                            Icon(
                                imageVector = if (selectedTracks.containsAll(allTrackIds) && selectedTracks.isNotEmpty()) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Outlined.Circle
                                },
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (selectedTracks.containsAll(allTrackIds) && selectedTracks.isNotEmpty()) {
                                    "Uncheck All"
                                } else {
                                    "Check All"
                                }
                            )
                        }
                    }

                    items(pl.tracks) { track ->
                        PlaylistTrackItem(
                            track = track,
                            isSelected = selectedTracks.contains(track.id),
                            onCheckedChange = { checked ->
                                selectedTracks = if (checked) {
                                    selectedTracks + track.id
                                } else {
                                    selectedTracks - track.id
                                }
                            },
                            onClick = {
                                // Play from this track onwards
                                val tracks = pl.tracks.map { t ->
                                    Track(
                                        uri = Uri.parse(t.uri),
                                        title = t.title,
                                        id = t.sourceId
                                    )
                                }
                                val startIndex = pl.tracks.indexOf(track)
                                audioPlayerViewModel.playQueue(tracks, startIndex)
                            },
                            onDelete = {
                                scope.launch {
                                    repository.removeTrackFromPlaylist(track.id)
                                    playlist = repository.getPlaylistWithTracks(playlistId)
                                }
                            }
                        )
                    }
                }
            }
        }
        }

        // Bulk delete confirmation dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Tracks") },
                text = { Text("Delete ${selectedTracks.size} track(s) from this playlist?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                selectedTracks.forEach { trackId ->
                                    repository.removeTrackFromPlaylist(trackId)
                                }
                                playlist = repository.getPlaylistWithTracks(playlistId)
                                selectedTracks = emptySet()
                                showDeleteDialog = false
                            }
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun PlaylistTrackItem(
    track: PlaylistTrack,
    isSelected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = when (track.source) {
                        "local" -> "Local Storage"
                        "onedrive" -> "OneDrive"
                        else -> track.source
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove from playlist",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove Track") },
            text = { Text("Remove \"${track.title}\" from this playlist?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
