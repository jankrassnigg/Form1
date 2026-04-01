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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
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
import com.form1.musicplayer.onedrive.OneDriveCacheManager
import com.form1.musicplayer.onedrive.OneDriveDownloadQueue
import com.form1.musicplayer.player.AudioPlayerViewModel
import com.form1.musicplayer.player.Track
import com.form1.musicplayer.ui.PlayerBar
import com.form1.musicplayer.ui.theme.Form1MusicPlayerTheme
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
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
    val cacheManager = remember { OneDriveCacheManager.getInstance(context) }
    val downloadQueue = remember { OneDriveDownloadQueue.getInstance(context) }
    val scope = rememberCoroutineScope()

    var playlist by remember { mutableStateOf<Playlist?>(null) }
    var selectedTracks by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Offline availability state
    val playbackState by audioPlayerViewModel.playbackState.collectAsState()
    val currentPlayingId = playbackState.queue.getOrNull(playbackState.currentTrackIndex)?.id

    val downloadingItemIds by downloadQueue.downloadingItemIds.collectAsState()

    var offlineAvailable by remember { mutableStateOf(false) }
    // Track which OneDrive items are locally cached (refreshed when downloads complete)
    var cachedItemIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var offlineDownloadJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(playlistId) {
        playlist = repository.getPlaylistWithTracks(playlistId)
        offlineAvailable = playlist?.offlineAvailable ?: false
        // Compute which OneDrive items are already cached
        cachedItemIds = playlist?.tracks
            ?.filter { it.source == "onedrive" && cacheManager.isCached(it.sourceId) }
            ?.map { it.sourceId }
            ?.toSet() ?: emptySet()
    }

    // Refresh cached indicators whenever a download completes
    LaunchedEffect(Unit) {
        downloadQueue.events.collect { event ->
            if (event.success) {
                cachedItemIds = cachedItemIds + event.itemId
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
            bottomBar = { PlayerBar() },
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
                                scope.launch {
                                    val tracks = resolvePlaybackTracks(pl.tracks, repository)
                                    if (tracks.isNotEmpty()) audioPlayerViewModel.playQueue(tracks, 0, pl.id, pl.name)
                                }
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

                    item {
                        // Offline availability toggle
                        val oneDriveTracks = pl.tracks.filter { it.source == "onedrive" }
                        if (oneDriveTracks.isNotEmpty()) {
                            val cachedCount = oneDriveTracks.count { cachedItemIds.contains(it.sourceId) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Available Offline",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        "$cachedCount / ${oneDriveTracks.size} tracks cached",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Switch(
                                    checked = offlineAvailable,
                                    onCheckedChange = { enabled ->
                                        offlineAvailable = enabled
                                        scope.launch {
                                            repository.setOfflineAvailable(playlistId, enabled)
                                        }
                                        if (enabled) {
                                            offlineDownloadJob?.cancel()
                                            offlineDownloadJob = scope.launch {
                                                for (track in oneDriveTracks) {
                                                    if (!offlineAvailable) break
                                                    if (cacheManager.isCached(track.sourceId)) continue
                                                    downloadQueue.enqueue(track.sourceId, track.title)
                                                    // Wait for this specific item to finish before enqueuing the next
                                                    downloadQueue.events.first { it.itemId == track.sourceId }
                                                }
                                            }
                                        } else {
                                            offlineDownloadJob?.cancel()
                                            offlineDownloadJob = null
                                        }
                                    }
                                )
                            }
                        }
                    }

                    items(pl.tracks) { track ->
                        PlaylistTrackItem(
                            track = track,
                            isSelected = selectedTracks.contains(track.id),
                            isCached = track.source == "onedrive" && cachedItemIds.contains(track.sourceId),
                            isCurrentlyPlaying = track.sourceId == currentPlayingId,
                            isDownloading = track.source == "onedrive" && downloadingItemIds.contains(track.sourceId),
                            onCheckedChange = { checked ->
                                selectedTracks = if (checked) {
                                    selectedTracks + track.id
                                } else {
                                    selectedTracks - track.id
                                }
                            },
                            onClick = {
                                scope.launch {
                                    val tracks = resolvePlaybackTracks(pl.tracks, repository)
                                    val startIndex = pl.tracks.indexOf(track)
                                    if (tracks.isNotEmpty()) audioPlayerViewModel.playQueue(tracks, startIndex.coerceAtMost(tracks.size - 1), pl.id, pl.name)
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    repository.removeTrackFromPlaylist(playlistId, track.position)
                                    playlist = repository.getPlaylistWithTracks(playlistId)
                                }
                            }
                        )
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
                                // selectedTracks contains track.id values which equal track.position
                                selectedTracks
                                    .map { it.toInt() }
                                    .sortedDescending() // remove from end first to keep positions stable
                                    .forEach { pos ->
                                        repository.removeTrackFromPlaylist(playlistId, pos)
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

/**
 * Resolves playback URIs for all tracks in parallel.
 * OneDrive tracks: fetches a fresh download URL via the Graph API.
 * Local tracks: uses sourceId directly as a file URI.
 * Tracks whose URL cannot be resolved are skipped (logged as warnings).
 */
private suspend fun resolvePlaybackTracks(
    tracks: List<PlaylistTrack>,
    repository: PlaylistRepository
): List<Track> = coroutineScope {
    tracks.map { track ->
        async {
            when (track.source) {
                "local" -> Track(
                    uri = Uri.parse(track.sourceId),
                    title = track.title,
                    id = track.sourceId
                )
                else -> {
                    val result = repository.getDownloadUrl(track.sourceId)
                    if (result.isSuccess) {
                        Track(
                            uri = Uri.parse(result.getOrThrow()),
                            title = track.title,
                            id = track.sourceId
                        )
                    } else {
                        android.util.Log.w("PlaylistDetails", "Could not resolve URL for ${track.title}: ${result.exceptionOrNull()?.message}")
                        null
                    }
                }
            }
        }
    }.awaitAll().filterNotNull()
}

@Composable
fun PlaylistTrackItem(
    track: PlaylistTrack,
    isSelected: Boolean,
    isCached: Boolean = false,
    isCurrentlyPlaying: Boolean = false,
    isDownloading: Boolean = false,
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyPlaying)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
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
                imageVector = if (isCurrentlyPlaying) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                contentDescription = if (isCurrentlyPlaying) "Now playing" else null,
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (track.source) {
                            "local" -> "Local Storage"
                            "onedrive" -> "OneDrive"
                            else -> track.source
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(12.dp),
                            strokeWidth = 1.5.dp
                        )
                    } else if (isCached) {
                        Icon(
                            imageVector = Icons.Default.OfflinePin,
                            contentDescription = "Cached offline",
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
