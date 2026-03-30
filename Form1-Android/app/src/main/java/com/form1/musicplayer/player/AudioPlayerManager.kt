package com.form1.musicplayer.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages audio playback using ExoPlayer.
 * Handles play, pause, stop, and track management.
 * Singleton to ensure only one player instance exists across the app.
 */
class AudioPlayerManager private constructor(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var updatePositionRunnable: Runnable? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        initializePlayer()
        startPositionUpdates()
    }

    companion object {
        @Volatile
        private var instance: AudioPlayerManager? = null

        fun getInstance(context: Context): AudioPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: AudioPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = isPlaying
                    )
                }

                override fun onPlaybackStateChanged(state: Int) {
                    _playbackState.value = _playbackState.value.copy(
                        isLoading = state == Player.STATE_BUFFERING,
                        hasEnded = state == Player.STATE_ENDED
                    )

                    // Auto-advance to next track when current track ends
                    if (state == Player.STATE_ENDED) {
                        playNext()
                    }
                }
            })
        }
    }

    /**
     * Load and play audio from a URI (file path or resource)
     */
    fun playAudio(uri: Uri, title: String = "Unknown") {
        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()

            _playbackState.value = _playbackState.value.copy(
                currentTrack = title,
                isPlaying = true,
                hasTrack = true
            )
        }
    }

    /**
     * Play audio from app resources (res/raw folder)
     */
    fun playResourceAudio(resourceId: Int, title: String = "Test Audio") {
        val uri = Uri.parse("android.resource://${context.packageName}/$resourceId")
        playAudio(uri, title)
    }

    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    /**
     * Pause playback
     */
    fun pause() {
        exoPlayer?.pause()
    }

    /**
     * Resume playback
     */
    fun play() {
        exoPlayer?.play()
    }

    /**
     * Stop playback and release resources
     */
    fun stop() {
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
            _playbackState.value = PlaybackState()
        }
    }

    /**
     * Get current playback position in milliseconds
     */
    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }

    /**
     * Get total duration in milliseconds
     */
    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
    }

    /**
     * Seek to position in milliseconds
     */
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    /**
     * Skip forward by specified milliseconds (default 10 seconds)
     */
    fun skipForward(skipMs: Long = 10000L) {
        exoPlayer?.let { player ->
            val newPosition = (player.currentPosition + skipMs).coerceAtMost(player.duration)
            player.seekTo(newPosition)
        }
    }

    /**
     * Skip backward by specified milliseconds (default 10 seconds)
     */
    fun skipBackward(skipMs: Long = 10000L) {
        exoPlayer?.let { player ->
            val newPosition = (player.currentPosition - skipMs).coerceAtLeast(0L)
            player.seekTo(newPosition)
        }
    }

    /**
     * Set volume (0.0 to 1.0)
     */
    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    /**
     * Get current volume (0.0 to 1.0)
     */
    fun getVolume(): Float {
        return exoPlayer?.volume ?: 1f
    }

    /**
     * Play a queue of tracks, starting from the specified index
     */
    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return

        val validIndex = startIndex.coerceIn(0, tracks.size - 1)
        _playbackState.value = _playbackState.value.copy(
            queue = tracks,
            currentTrackIndex = validIndex
        )

        playTrackAtIndex(validIndex)
    }

    /**
     * Add a single track and play it immediately
     */
    fun playSingleTrack(track: Track) {
        playQueue(listOf(track), 0)
    }

    /**
     * Play track at specific index in queue
     */
    private fun playTrackAtIndex(index: Int) {
        val currentState = _playbackState.value
        if (index < 0 || index >= currentState.queue.size) return

        val track = currentState.queue[index]
        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(track.uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()

            _playbackState.value = currentState.copy(
                currentTrack = track.title,
                currentTrackIndex = index,
                isPlaying = true,
                hasTrack = true,
                hasEnded = false
            )
        }
    }

    /**
     * Play next track in queue
     */
    fun playNext() {
        val currentState = _playbackState.value
        val nextIndex = currentState.currentTrackIndex + 1

        if (nextIndex < currentState.queue.size) {
            playTrackAtIndex(nextIndex)
        } else {
            // End of queue - stop playback
            _playbackState.value = currentState.copy(
                isPlaying = false,
                hasEnded = true
            )
        }
    }

    /**
     * Play previous track in queue
     */
    fun playPrevious() {
        val currentState = _playbackState.value
        val currentPosition = getCurrentPosition()

        // If more than 3 seconds into track, restart current track
        // Otherwise, go to previous track
        if (currentPosition > 3000) {
            seekTo(0)
        } else {
            val prevIndex = currentState.currentTrackIndex - 1
            if (prevIndex >= 0) {
                playTrackAtIndex(prevIndex)
            } else {
                // Already at first track, just restart it
                seekTo(0)
            }
        }
    }

    /**
     * Check if there's a next track
     */
    fun hasNext(): Boolean {
        val currentState = _playbackState.value
        return currentState.currentTrackIndex < currentState.queue.size - 1
    }

    /**
     * Check if there's a previous track
     */
    fun hasPrevious(): Boolean {
        return _playbackState.value.currentTrackIndex > 0
    }

    /**
     * Start periodic position updates
     */
    private fun startPositionUpdates() {
        updatePositionRunnable = object : Runnable {
            override fun run() {
                exoPlayer?.let { player ->
                    if (player.isPlaying || player.playbackState == Player.STATE_READY) {
                        _playbackState.value = _playbackState.value.copy(
                            currentPosition = player.currentPosition,
                            duration = if (player.duration > 0) player.duration else 0L
                        )
                    }
                }
                handler.postDelayed(this, 200) // Update every 200ms
            }
        }
        handler.post(updatePositionRunnable!!)
    }

    /**
     * Clean up resources when done
     */
    fun release() {
        updatePositionRunnable?.let { handler.removeCallbacks(it) }
        exoPlayer?.release()
        exoPlayer = null
    }
}

/**
 * Represents a track in the queue
 */
data class Track(
    val uri: Uri,
    val title: String,
    val id: String = uri.toString() // Unique identifier
)

/**
 * Represents the current state of audio playback
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val hasTrack: Boolean = false,
    val currentTrack: String = "",
    val isLoading: Boolean = false,
    val hasEnded: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val queue: List<Track> = emptyList(),
    val currentTrackIndex: Int = -1
)
