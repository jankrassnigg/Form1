package com.form1.musicplayer

import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Background service that hosts the ExoPlayer and MediaSession.
 *
 * MediaSessionService automatically:
 * - Runs as a foreground service while media is playing
 * - Posts a media notification with play/pause, prev, next controls
 * - Handles audio focus
 * - Stops the service when playback ends and no client is bound
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * Called when the user swipes the app away from the recents list.
     * Stop playback and remove the notification.
     *
     * We intentionally do NOT call super here: MediaSessionService's base implementation
     * reschedules the service to keep running when a player is active, which would undo
     * the stopSelf() call.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
