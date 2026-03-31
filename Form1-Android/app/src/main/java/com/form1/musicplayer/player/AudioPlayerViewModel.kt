package com.form1.musicplayer.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for managing audio playback state and interactions.
 * Provides a clean separation between UI and audio player logic.
 * Uses singleton AudioPlayerManager to ensure one player across all activities.
 */
class AudioPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val audioPlayerManager = AudioPlayerManager.getInstance(application.applicationContext)

    // Expose playback state to the UI
    val playbackState: StateFlow<PlaybackState> = audioPlayerManager.playbackState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaybackState()
        )

    /**
     * Play audio from a URI (external file)
     */
    fun playAudio(uri: Uri, title: String = "Unknown") {
        audioPlayerManager.playAudio(uri, title)
    }

    /**
     * Play audio from app resources
     */
    fun playResourceAudio(resourceId: Int, title: String = "Test Audio") {
        audioPlayerManager.playResourceAudio(resourceId, title)
    }

    /**
     * Play a queue of tracks
     */
    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        audioPlayerManager.playQueue(tracks, startIndex)
    }

    /**
     * Play a single track (creates a queue with one item)
     */
    fun playSingleTrack(track: Track) {
        audioPlayerManager.playSingleTrack(track)
    }

    /**
     * Play next track in queue
     */
    fun playNext() {
        audioPlayerManager.playNext()
    }

    /**
     * Play previous track in queue
     */
    fun playPrevious() {
        audioPlayerManager.playPrevious()
    }

    /**
     * Check if there's a next track
     */
    fun hasNext(): Boolean {
        return audioPlayerManager.hasNext()
    }

    /**
     * Check if there's a previous track
     */
    fun hasPrevious(): Boolean {
        return audioPlayerManager.hasPrevious()
    }

    /**
     * Toggle between play and pause
     */
    fun togglePlayPause() {
        audioPlayerManager.togglePlayPause()
    }

    /**
     * Stop playback
     */
    fun stop() {
        audioPlayerManager.stop()
    }

    /**
     * Get current playback position
     */
    fun getCurrentPosition(): Long {
        return audioPlayerManager.getCurrentPosition()
    }

    /**
     * Get track duration
     */
    fun getDuration(): Long {
        return audioPlayerManager.getDuration()
    }

    /**
     * Seek to a specific position
     */
    fun seekTo(positionMs: Long) {
        audioPlayerManager.seekTo(positionMs)
    }

    /**
     * Skip forward (default 10 seconds)
     */
    fun skipForward(skipMs: Long = 10000L) {
        audioPlayerManager.skipForward(skipMs)
    }

    /**
     * Skip backward (default 10 seconds)
     */
    fun skipBackward(skipMs: Long = 10000L) {
        audioPlayerManager.skipBackward(skipMs)
    }

    override fun onCleared() {
        super.onCleared()
        // Don't release the singleton player manager here
        // It will live for the lifetime of the app
    }
}
