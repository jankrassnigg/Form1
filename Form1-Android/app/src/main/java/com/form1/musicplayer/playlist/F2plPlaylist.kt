package com.form1.musicplayer.playlist

/**
 * Root object for a .f2pl playlist file.
 * Filename convention: "YYYY-MM-DD-HH-mm-ss - Playlist Name.f2pl"
 *
 * There is no top-level "name" field — the playlist name is derived from the last
 * "RenamePlaylist" modification (its [F2plMod.misc] field).
 */
data class F2plFile(
    val version: Int = 1,
    val playlistGuid: String,
    val references: List<F2plRef> = emptyList(),
    val modifications: List<F2plMod> = emptyList()
)

/**
 * A canonical reference to one audio file.
 * The index into [F2plFile.references] is implicit (array position).
 *
 * [display]        — display title shown in the UI
 * [oneDriveItemId] — stable OneDrive item ID (null for local files)
 * [relativePaths]  — relative path(s) from the OneDrive music folder or local root
 */
data class F2plRef(
    val display: String,
    val oneDriveItemId: String? = null,
    val relativePaths: List<String> = emptyList()
)

/**
 * A single playlist event (append-only log).
 *
 * [modGuid] — UUID for deduplication during merge
 * [ts]      — ISO-8601 UTC timestamp, used for ordering
 * [op]      — "AddSong" | "RemoveSong" | "RenamePlaylist"
 * [ref]     — index into [F2plFile.references]; required for AddSong / RemoveSong
 * [misc]    — new playlist display name; required for RenamePlaylist
 */
data class F2plMod(
    val modGuid: String,
    val ts: String,
    val op: String,
    val ref: Int? = null,
    val misc: String? = null
)
