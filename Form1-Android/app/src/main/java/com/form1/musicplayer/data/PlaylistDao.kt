package com.form1.musicplayer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    // Playlist operations
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylist(playlistId: Long): PlaylistEntity?

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    // Track operations
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getTracksForPlaylist(playlistId: Long): List<PlaylistTrackEntity>

    @Insert
    suspend fun insertTrack(track: PlaylistTrackEntity): Long

    @Insert
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>)

    @Delete
    suspend fun deleteTrack(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteAllTracksFromPlaylist(playlistId: Long)

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int?

    // Combined operations
    @Transaction
    suspend fun getPlaylistWithTracks(playlistId: Long): Playlist? {
        val playlist = getPlaylist(playlistId) ?: return null
        val tracks = getTracksForPlaylist(playlistId).map { it.toDomain() }
        return Playlist(
            id = playlist.id,
            name = playlist.name,
            tracks = tracks,
            createdAt = playlist.createdAt,
            updatedAt = playlist.updatedAt
        )
    }
}

// Extension functions for mapping
fun PlaylistTrackEntity.toDomain() = PlaylistTrack(
    id = id,
    playlistId = playlistId,
    title = title,
    source = source,
    uri = uri,
    sourceId = sourceId,
    position = position
)

fun PlaylistTrack.toEntity() = PlaylistTrackEntity(
    id = id,
    playlistId = playlistId,
    title = title,
    source = source,
    uri = uri,
    sourceId = sourceId,
    position = position
)
