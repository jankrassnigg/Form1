package com.form1.musicplayer.data

/**
 * Lightweight entity used by the UI list screen — no Room dependency.
 */
data class PlaylistEntity(
    val id: Long,
    val name: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** Domain model for a playlist with its tracks. */
data class Playlist(
    val id: Long,
    val name: String,
    val tracks: List<PlaylistTrack>,
    val createdAt: Long,
    val updatedAt: Long,
    /** Whether the user has requested all tracks to be kept available offline. */
    val offlineAvailable: Boolean = false
)

/** Domain model for a single track inside a playlist. */
data class PlaylistTrack(
    /** Position in the current track list (0-indexed). Used as a stable-within-session ID. */
    val id: Long,
    val playlistId: Long,
    val title: String,
    val source: String,  // "local" or "onedrive"
    val uri: String,     // cached playback URL (may expire for OneDrive)
    val sourceId: String, // OneDrive item ID (stable), or local path
    val position: Int
)
