package com.form1.musicplayer.playlist

/**
 * Root object for a .f2pl playlist file.
 * Filename convention: "YYYY-MM-DD-HH-mm-ss - Playlist Name.f2pl"
 */
data class F2plFile(
    val version: Int = 1,
    val playlistGuid: String,
    val name: String,
    val fileRefs: List<F2plFileRef> = emptyList(),
    val modifications: List<F2plMod> = emptyList()
)

/**
 * A canonical reference to one audio file. Multiple paths/IDs let the app locate
 * the same file on different devices or after it has been moved/renamed.
 *
 * [index] is the stable per-playlist integer key used by [F2plMod.fileIndex].
 */
data class F2plFileRef(
    val index: Int,
    val relativePaths: List<String> = emptyList(),  // e.g. ["Artist/Album/song.mp3"]
    val oneDriveItemId: String? = null,
    val oneDriveDriveId: String? = null,
    val pcGuid: String? = null                       // future: PC-app song GUID
)

/**
 * A single playlist event (append-only log).
 *
 * [modGuid]   — UUID for deduplication during merge
 * [ts]        — ISO-8601 timestamp, used for ordering
 * [op]        — "AddSong" | "RemoveSong" | "RenamePlaylist"
 * [fileIndex] — index into [F2plFile.fileRefs]; required for AddSong / RemoveSong
 * [name]      — new playlist name; required for RenamePlaylist
 * [title]     — display title stored with AddSong (redundant but handy)
 * [source]    — "onedrive" | "local"; stored with AddSong
 * [uri]       — cached playback URL stored with AddSong (may expire for OneDrive)
 */
data class F2plMod(
    val modGuid: String,
    val ts: String,
    val op: String,
    val fileIndex: Int? = null,
    val name: String? = null,
    val title: String? = null,
    val source: String? = null,
    val uri: String? = null
)
