package com.form1.musicplayer.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for managing playlists
 */
class PlaylistRepository private constructor(context: Context) {

    private val database = PlaylistDatabase.getInstance(context)
    private val playlistDao = database.playlistDao()

    companion object {
        @Volatile
        private var instance: PlaylistRepository? = null

        fun getInstance(context: Context): PlaylistRepository {
            return instance ?: synchronized(this) {
                instance ?: PlaylistRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Get all playlists as a Flow
     */
    fun getAllPlaylists(): Flow<List<PlaylistEntity>> {
        return playlistDao.getAllPlaylists()
    }

    /**
     * Get a single playlist with its tracks
     */
    suspend fun getPlaylistWithTracks(playlistId: Long): Playlist? {
        return playlistDao.getPlaylistWithTracks(playlistId)
    }

    /**
     * Create a new playlist
     */
    suspend fun createPlaylist(name: String): Long {
        val playlist = PlaylistEntity(name = name)
        return playlistDao.insertPlaylist(playlist)
    }

    /**
     * Update playlist name
     */
    suspend fun updatePlaylist(playlistId: Long, newName: String) {
        val playlist = playlistDao.getPlaylist(playlistId) ?: return
        playlistDao.updatePlaylist(
            playlist.copy(
                name = newName,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Delete a playlist
     */
    suspend fun deletePlaylist(playlistId: Long) {
        val playlist = playlistDao.getPlaylist(playlistId) ?: return
        playlistDao.deletePlaylist(playlist)
    }

    /**
     * Add tracks to a playlist
     */
    suspend fun addTracksToPlaylist(
        playlistId: Long,
        tracks: List<TrackInfo>
    ) {
        val currentMaxPosition = playlistDao.getMaxPosition(playlistId) ?: -1
        val trackEntities = tracks.mapIndexed { index, trackInfo ->
            PlaylistTrackEntity(
                playlistId = playlistId,
                title = trackInfo.title,
                source = trackInfo.source,
                uri = trackInfo.uri,
                sourceId = trackInfo.sourceId,
                position = currentMaxPosition + 1 + index
            )
        }
        playlistDao.insertTracks(trackEntities)

        // Update playlist timestamp
        val playlist = playlistDao.getPlaylist(playlistId) ?: return
        playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Remove a track from playlist
     */
    suspend fun removeTrackFromPlaylist(trackId: Long) {
        val track = playlistDao.getTracksForPlaylist(0).find { it.id == trackId } ?: return
        playlistDao.deleteTrack(track)
    }

    /**
     * Clear all tracks from a playlist
     */
    suspend fun clearPlaylist(playlistId: Long) {
        playlistDao.deleteAllTracksFromPlaylist(playlistId)

        // Update playlist timestamp
        val playlist = playlistDao.getPlaylist(playlistId) ?: return
        playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
    }
}

/**
 * Helper data class for adding tracks to playlists
 */
data class TrackInfo(
    val title: String,
    val source: String, // "local" or "onedrive"
    val uri: String,
    val sourceId: String
)
