package com.form1.musicplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.form1.musicplayer.player.AudioPlayerViewModel
import com.form1.musicplayer.player.PlaybackState
import com.form1.musicplayer.ui.AppNavigationDrawer
import com.form1.musicplayer.ui.NavigationScreen
import com.form1.musicplayer.ui.theme.Form1MusicPlayerTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Form1MusicPlayerTheme {
                MainScreenWithDrawer()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenWithDrawer() {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppNavigationDrawer(
                context = context,
                currentScreen = NavigationScreen.PLAYER,
                drawerState = drawerState
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Form1 Music Player") },
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
            }
        ) { innerPadding ->
            MusicPlayerHome(
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
fun MusicPlayerHome(
    modifier: Modifier = Modifier,
    viewModel: AudioPlayerViewModel = viewModel()
) {
    val playbackState by viewModel.playbackState.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Form1 Music Player",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Your music, your way",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Playback Control Card
            PlaybackControlCard(
                playbackState = playbackState,
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onStopClick = { viewModel.stop() },
                onSkipForward = { viewModel.playNext() },
                onSkipBackward = { viewModel.playPrevious() },
                onSeek = { position -> viewModel.seekTo(position) },
                onVolumeChange = { volume -> viewModel.setVolume(volume) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Queue Info
            if (playbackState.queue.isNotEmpty()) {
                Text(
                    text = "Track ${playbackState.currentTrackIndex + 1} of ${playbackState.queue.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Use the menu (☰) to browse your music",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun PlaybackControlCard(
    playbackState: PlaybackState,
    onPlayPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0L) }
    var volumeLevel by remember { mutableStateOf(1f) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Now Playing Text
            Text(
                text = if (playbackState.hasTrack) "Now Playing" else "No Track",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = playbackState.currentTrack.ifEmpty { "Select a file to play" },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Loading indicator
            if (playbackState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Progress bar with time labels
            if (playbackState.hasTrack && playbackState.duration > 0) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Time labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(if (isSeeking) seekPosition else playbackState.currentPosition),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = formatTime(playbackState.duration),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Progress slider
                    Slider(
                        value = if (isSeeking) seekPosition.toFloat() else playbackState.currentPosition.toFloat(),
                        onValueChange = { value ->
                            isSeeking = true
                            seekPosition = value.toLong()
                        },
                        onValueChangeFinished = {
                            onSeek(seekPosition)
                            isSeeking = false
                        },
                        valueRange = 0f..playbackState.duration.toFloat(),
                        enabled = playbackState.hasTrack
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Playback Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip Backward Button
                IconButton(
                    onClick = onSkipBackward,
                    enabled = playbackState.hasTrack
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Skip Backward",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play/Pause Button
                FilledIconButton(
                    onClick = onPlayPauseClick,
                    enabled = playbackState.hasTrack,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Skip Forward Button
                IconButton(
                    onClick = onSkipForward,
                    enabled = playbackState.hasTrack
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip Forward",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Stop Button
                IconButton(
                    onClick = onStopClick,
                    enabled = playbackState.hasTrack
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Volume Control
            if (playbackState.hasTrack) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        modifier = Modifier.size(24.dp)
                    )
                    Slider(
                        value = volumeLevel,
                        onValueChange = { value ->
                            volumeLevel = value
                            onVolumeChange(value)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(volumeLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Status Text
            if (playbackState.hasEnded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Playback finished",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/**
 * Format milliseconds to MM:SS format
 */
fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}

/**
 * Extract filename from URI for display
 */
fun getFileNameFromUri(context: android.content.Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        } ?: uri.lastPathSegment ?: "Unknown"
    } catch (e: Exception) {
        uri.lastPathSegment ?: "Unknown"
    }
}

@Preview(showBackground = true)
@Composable
fun PlaybackControlCardPreview() {
    Form1MusicPlayerTheme {
        PlaybackControlCard(
            playbackState = PlaybackState(
                isPlaying = true,
                hasTrack = true,
                currentTrack = "Sample Song.mp3",
                currentPosition = 75000L,  // 1:15
                duration = 180000L         // 3:00
            ),
            onPlayPauseClick = {},
            onStopClick = {},
            onSkipForward = {},
            onSkipBackward = {},
            onSeek = {},
            onVolumeChange = {}
        )
    }
}

@Preview(showBackground = true, name = "No Track Loaded")
@Composable
fun PlaybackControlCardPreviewEmpty() {
    Form1MusicPlayerTheme {
        PlaybackControlCard(
            playbackState = PlaybackState(),
            onPlayPauseClick = {},
            onStopClick = {},
            onSkipForward = {},
            onSkipBackward = {},
            onSeek = {},
            onVolumeChange = {}
        )
    }
}
