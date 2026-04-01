package com.form1.musicplayer.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.form1.musicplayer.PlaybackService
import com.form1.musicplayer.data.PlaylistRepository
import com.form1.musicplayer.onedrive.OneDriveCacheManager
import com.form1.musicplayer.onedrive.OneDriveDownloadQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages audio playback by connecting to [PlaybackService] via [MediaController].
 *
 * [MediaController] implements [Player], so all playback calls (play, pause, seek, etc.)
 * are the same as calling ExoPlayer directly, but they execute in the service process.
 *
 * The service owns the ExoPlayer instance and the MediaSession. It automatically posts
 * a media notification with play/pause, previous, and next controls.
 *
 * Volume is handled by the system (hardware buttons route to STREAM_MUSIC when a
 * MediaSession is active).
 */
class AudioPlayerManager private constructor(private val context: Context) {

    private var player: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var updatePositionRunnable: Runnable? = null
    private val cacheManager = OneDriveCacheManager.getInstance(context)
    private val downloadQueue = OneDriveDownloadQueue.getInstance(context)
    private val repository = PlaylistRepository.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Tracks which OneDrive item IDs have already had a background cache download triggered
    // this session, so we don't re-enqueue on every position tick.
    private val cacheTriggered = mutableSetOf<String>()

    // Tracks which track IDs have had their playlist display text updated this session.
    private val displayUpdated = mutableSetOf<String>()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    companion object {
        private const val TAG = "AudioPlayerManager"

        @Volatile
        private var instance: AudioPlayerManager? = null

        fun getInstance(context: Context): AudioPlayerManager =
            instance ?: synchronized(this) {
                instance ?: AudioPlayerManager(context.applicationContext).also { instance = it }
            }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.value = _playbackState.value.copy(
                isLoading = state == Player.STATE_BUFFERING,
                hasEnded = state == Player.STATE_ENDED
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = player?.currentMediaItemIndex ?: 0
            // Use queue data for the immediate display; onMediaMetadataChanged will override
            // these with embedded ID3 tags once ExoPlayer reads them from the file.
            val queueTrack = _playbackState.value.queue.getOrNull(index)
            Log.d(TAG, "onMediaItemTransition: index=$index mediaId=${mediaItem?.mediaId} reason=$reason track=${queueTrack?.title}")
            _playbackState.value = _playbackState.value.copy(
                currentTrack = queueTrack?.title ?: "",
                currentArtist = queueTrack?.artist ?: "",
                currentAlbum = queueTrack?.album ?: "",
                currentTrackIndex = index,
                hasTrack = mediaItem != null,
                hasEnded = false
            )
        }

        // Fires when ExoPlayer reads embedded metadata (ID3 tags) from the audio file.
        // This is what the notification already uses; we mirror it into PlaybackState so
        // the UI stays in sync.
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
            val artist = mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
            val album = mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
            Log.d(TAG, "onMediaMetadataChanged: title=$title artist=$artist album=$album")
            _playbackState.value = _playbackState.value.copy(
                currentTrack = title ?: _playbackState.value.currentTrack,
                currentArtist = artist ?: _playbackState.value.currentArtist,
                currentAlbum = album ?: _playbackState.value.currentAlbum
            )
            // If we got a real title from ID3 tags, update the playlist display text if it differs.
            if (title != null) {
                val newDisplay = if (artist != null) "$artist - $title" else title
                val state = _playbackState.value
                val idx = player?.currentMediaItemIndex ?: state.currentTrackIndex
                val track = state.queue.getOrNull(idx)
                val playlistId = state.currentPlaylistId
                Log.d(TAG, "onMediaMetadataChanged: idx=$idx trackId=${track?.id} playlistId=$playlistId newDisplay=$newDisplay currentTitle=${track?.title}")
                if (track != null && playlistId != null) {
                    val isNew = displayUpdated.add(track.id)
                    Log.d(TAG, "onMediaMetadataChanged: isNewUpdate=$isNew displayAlreadyUpdated=${!isNew}")
                    if (isNew && newDisplay != track.title) {
                        Log.d(TAG, "onMediaMetadataChanged: launching updateTrackDisplay for trackId=${track.id}")
                        scope.launch {
                            repository.updateTrackDisplay(playlistId, track.id, newDisplay)
                        }
                    }
                }
            }
        }
    }

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                controller.addListener(playerListener)
                player = controller
                startPositionUpdates()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to PlaybackService", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun playAudio(uri: Uri, title: String = "Unknown") {
        player?.let { p ->
            val item = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
            p.setMediaItem(item)
            p.prepare()
            p.play()
            _playbackState.value = _playbackState.value.copy(
                currentTrack = title,
                isPlaying = true,
                hasTrack = true,
                queue = emptyList(),
                currentTrackIndex = 0,
                hasEnded = false,
                currentPlaylistId = null,
                currentPlaylistName = ""
            )
        }
    }

    fun playResourceAudio(resourceId: Int, title: String = "Test Audio") {
        val uri = Uri.parse("android.resource://${context.packageName}/$resourceId")
        playAudio(uri, title)
    }

    fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun pause() { player?.pause() }
    fun play() { player?.play() }

    fun stop() {
        player?.let { p ->
            p.stop()
            p.clearMediaItems()
            _playbackState.value = PlaybackState()  // resets all fields including artist/album
        }
    }

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L
    fun getDuration(): Long = player?.duration ?: 0L
    fun seekTo(positionMs: Long) { player?.seekTo(positionMs) }

    fun skipForward(skipMs: Long = 10000L) {
        player?.let { p ->
            p.seekTo((p.currentPosition + skipMs).coerceAtMost(p.duration))
        }
    }

    fun skipBackward(skipMs: Long = 10000L) {
        player?.let { p ->
            p.seekTo((p.currentPosition - skipMs).coerceAtLeast(0L))
        }
    }

    /**
     * Load a queue of tracks and start playing from [startIndex].
     * ExoPlayer manages auto-advance and the notification prev/next buttons automatically.
     */
    fun playQueue(
        tracks: List<Track>,
        startIndex: Int = 0,
        playlistId: Long? = null,
        playlistName: String = ""
    ) {
        if (tracks.isEmpty()) return
        val validIndex = startIndex.coerceIn(0, tracks.size - 1)

        val mediaItems = tracks.map { track ->
            // Do NOT set title/artist/album here — if we pre-populate them, ExoPlayer's merge
            // policy gives our values priority over the embedded ID3 tags, so the notification
            // and onMediaMetadataChanged would never show the real song title.
            // Initial display comes from the queue lookup in onMediaItemTransition;
            // onMediaMetadataChanged then overrides with extracted tags once available.
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaId(track.id)
                .build()
        }

        player?.let { p ->
            p.setMediaItems(mediaItems, validIndex, 0L)
            p.prepare()
            p.play()
        }

        _playbackState.value = _playbackState.value.copy(
            queue = tracks,
            currentTrackIndex = validIndex,
            currentTrack = tracks[validIndex].title,
            currentArtist = tracks[validIndex].artist,
            currentAlbum = tracks[validIndex].album,
            hasTrack = true,
            hasEnded = false,
            currentPlaylistId = playlistId,
            currentPlaylistName = playlistName
        )
    }

    fun playSingleTrack(track: Track) {
        playQueue(listOf(track), 0)
    }

    /** Skip to next track in queue. */
    fun playNext() { player?.seekToNextMediaItem() }

    /**
     * Go to previous track, or seek to start if more than 3 seconds into the current track.
     * [MediaController.seekToPrevious] implements this threshold natively (default 3 s).
     */
    fun playPrevious() { player?.seekToPrevious() }

    fun hasNext(): Boolean = player?.hasNextMediaItem() ?: false
    fun hasPrevious(): Boolean = (player?.currentMediaItemIndex ?: 0) > 0

    private fun startPositionUpdates() {
        updatePositionRunnable = object : Runnable {
            override fun run() {
                player?.let { p ->
                    if (p.isPlaying || p.playbackState == Player.STATE_READY) {
                        val pos = p.currentPosition
                        val dur = if (p.duration > 0) p.duration else 0L
                        _playbackState.value = _playbackState.value.copy(
                            currentPosition = pos,
                            duration = dur
                        )
                        // At 50% playback, cache the file in the background so it's available
                        // offline next time — without wasting bandwidth on skipped songs.
                        if (dur > 0 && pos >= dur / 2) {
                            val track = _playbackState.value.queue
                                .getOrNull(p.currentMediaItemIndex)
                            if (track != null
                                && track.uri.scheme == "https"
                                && cacheTriggered.add(track.id)  // add() returns false if already present
                            ) {
                                downloadQueue.enqueue(track.id, track.title)
                            }
                        }
                    }
                }
                handler.postDelayed(this, 200)
            }
        }
        handler.post(updatePositionRunnable!!)
    }

    fun release() {
        updatePositionRunnable?.let { handler.removeCallbacks(it) }
        player?.release()
        player = null
    }
}

/** Represents a track in the queue. */
data class Track(
    val uri: Uri,
    val title: String,
    val id: String = uri.toString(),
    val artist: String = "",
    val album: String = ""
)

/** Represents the current state of audio playback. */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val hasTrack: Boolean = false,
    val currentTrack: String = "",
    /** Artist name from embedded audio metadata; empty string if unavailable. */
    val currentArtist: String = "",
    /** Album name from embedded audio metadata; empty string if unavailable. */
    val currentAlbum: String = "",
    val isLoading: Boolean = false,
    val hasEnded: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val queue: List<Track> = emptyList(),
    val currentTrackIndex: Int = -1,
    /** ID of the playlist the queue originated from, or null if not from a playlist. */
    val currentPlaylistId: Long? = null,
    /** Display name of the source playlist, or empty string if not from a playlist. */
    val currentPlaylistName: String = ""
)
