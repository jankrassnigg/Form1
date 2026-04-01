package com.form1.musicplayer.data

import android.content.Context
import com.form1.musicplayer.playlist.PlaylistFileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing playlists.
 *
 * Backed by [PlaylistFileManager] — playlists are persisted as .f2pl JSON files
 * in the user's profile directory (local or OneDrive).
 */
class PlaylistRepository private constructor(context: Context) {

    private val fileManager = PlaylistFileManager.getInstance(context)

    companion object {
        @Volatile
        private var instance: PlaylistRepository? = null

        fun getInstance(context: Context): PlaylistRepository =
            instance ?: synchronized(this) {
                instance ?: PlaylistRepository(context.applicationContext).also { instance = it }
            }
    }

    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = fileManager.getAllPlaylists()

    /** True while the initial playlist load from storage is in progress. */
    val isLoading: StateFlow<Boolean> get() = fileManager.isLoading

    suspend fun getPlaylistWithTracks(playlistId: Long): Playlist? =
        fileManager.getPlaylistWithTracks(playlistId)

    suspend fun createPlaylist(name: String): Long = fileManager.createPlaylist(name)

    suspend fun updatePlaylist(playlistId: Long, newName: String) =
        fileManager.renamePlaylist(playlistId, newName)

    suspend fun deletePlaylist(playlistId: Long) = fileManager.deletePlaylist(playlistId)

    suspend fun addTracksToPlaylist(playlistId: Long, tracks: List<TrackInfo>) =
        fileManager.addTracksToPlaylist(playlistId, tracks)

    /**
     * Remove the track at [position] (0-indexed in the current displayed list).
     * [playlistId] is required to locate the correct playlist.
     */
    suspend fun removeTrackFromPlaylist(playlistId: Long, position: Int) =
        fileManager.removeTrackAtPosition(playlistId, position)

    /** Set or clear the "make available offline" flag for a playlist. */
    suspend fun setOfflineAvailable(playlistId: Long, enabled: Boolean) =
        fileManager.setOfflineAvailable(playlistId, enabled)

    /** Resolve a temporary OneDrive download URL for [itemId]. */
    suspend fun getDownloadUrl(itemId: String): Result<String> =
        fileManager.getDownloadUrl(itemId)
}

/** Helper data class for adding tracks to playlists. */
data class TrackInfo(
    val title: String,
    val source: String,  // "local" or "onedrive"
    val uri: String,
    val sourceId: String
)
