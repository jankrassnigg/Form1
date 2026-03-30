package com.form1.musicplayer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for playlists
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Database entity for tracks in playlists
 * source: "local" or "onedrive"
 * uri: The URI to play the track
 * sourceId: File ID for OneDrive, URI string for local
 */
@Entity(
    tableName = "playlist_tracks",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val title: String,
    val source: String, // "local" or "onedrive"
    val uri: String, // The URI to play
    val sourceId: String, // Unique ID in source (for OneDrive fileId, for local it's same as uri)
    val position: Int // Order in playlist
)

/**
 * Domain model for playlists with tracks
 */
data class Playlist(
    val id: Long,
    val name: String,
    val tracks: List<PlaylistTrack>,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Domain model for playlist track
 */
data class PlaylistTrack(
    val id: Long,
    val playlistId: Long,
    val title: String,
    val source: String,
    val uri: String,
    val sourceId: String,
    val position: Int
)
