package com.form1.musicplayer.playlist

import android.content.Context
import android.util.Log
import com.form1.musicplayer.data.Playlist
import com.form1.musicplayer.data.PlaylistEntity
import com.form1.musicplayer.data.PlaylistTrack
import com.form1.musicplayer.data.TrackInfo
import com.form1.musicplayer.onedrive.OneDriveAuthManager
import com.form1.musicplayer.onedrive.OneDriveService
import com.form1.musicplayer.profile.ProfileConfig
import com.form1.musicplayer.profile.ProfileManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.abs

/**
 * Manages reading and writing of .f2pl playlist files via [ProfileManager].
 *
 * All mutation methods are safe to call from any coroutine dispatcher.
 * Writes are debounced by [WRITE_DEBOUNCE_MS] to avoid thrashing on rapid edits.
 *
 * On startup all .f2pl files are loaded; if multiple files share a [F2plFile.playlistGuid]
 * they are merged (modifications de-duplicated by modGuid and sorted by timestamp) and
 * then compacted into a single file.
 */
class PlaylistFileManager private constructor(context: Context) {

    companion object {
        private const val TAG = "PlaylistFileManager"
        private const val EXTENSION = "f2pl"
        private const val WRITE_DEBOUNCE_MS = 2_000L

        @Volatile
        private var instance: PlaylistFileManager? = null

        fun getInstance(context: Context): PlaylistFileManager =
            instance ?: synchronized(this) {
                instance ?: PlaylistFileManager(context.applicationContext).also { instance = it }
            }
    }

    // ── Dependencies ──────────────────────────────────────────────────────────

    private val profileConfig = ProfileConfig(context)
    private val authManager = OneDriveAuthManager(context)
    private val oneDriveService = OneDriveService(authManager)
    private val profileManager = ProfileManager(context, profileConfig, oneDriveService)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // ── In-memory state ───────────────────────────────────────────────────────

    /** guid → current playlist state */
    private val playlists = mutableMapOf<String, PlaylistState>()
    private val _flow = MutableStateFlow<List<PlaylistEntity>>(emptyList())

    /** pending debounce jobs keyed by playlist guid */
    private val debounceJobs = mutableMapOf<String, Job>()

    private data class PlaylistState(
        val guid: String,
        var file: F2plFile,
        /** file names (without path) loaded from disk; used to delete stale files after compact */
        val loadedFileNames: MutableList<String> = mutableListOf()
    )

    // ── Startup ───────────────────────────────────────────────────────────────

    init {
        scope.launch { loadAll() }
    }

    private suspend fun loadAll() {
        val fileNames = profileManager.listFiles(EXTENSION).getOrElse { emptyList() }
        val grouped = mutableMapOf<String, MutableList<Pair<String, F2plFile>>>()

        for (name in fileNames) {
            val json = profileManager.readFile(name).getOrNull() ?: continue
            val f2pl = try {
                gson.fromJson(json, F2plFile::class.java)
            } catch (e: Exception) {
                Log.w(TAG, "Skipping unreadable file: $name", e)
                continue
            }
            grouped.getOrPut(f2pl.playlistGuid) { mutableListOf() } += name to f2pl
        }

        for ((guid, pairs) in grouped) {
            val merged = merge(pairs.map { it.second })
            val state = PlaylistState(
                guid = guid,
                file = merged,
                loadedFileNames = pairs.map { it.first }.toMutableList()
            )
            playlists[guid] = state

            if (pairs.size > 1) {
                compact(state)
            }
        }
        emitFlow()
    }

    // ── Public query API ──────────────────────────────────────────────────────

    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = _flow

    suspend fun getPlaylistWithTracks(playlistId: Long): Playlist? =
        withContext(Dispatchers.Default) {
            val state = playlists.values.firstOrNull { toId(it.guid) == playlistId } ?: return@withContext null
            Playlist(
                id = playlistId,
                name = state.file.name,
                tracks = reconstructTracks(state.file, playlistId),
                createdAt = 0L,
                updatedAt = 0L
            )
        }

    // ── Public mutation API ───────────────────────────────────────────────────

    suspend fun createPlaylist(name: String): Long {
        val guid = UUID.randomUUID().toString()
        val file = F2plFile(playlistGuid = guid, name = name)
        val state = PlaylistState(guid = guid, file = file)
        playlists[guid] = state
        emitFlow()
        saveNow(state)   // immediate — don't risk losing a new playlist to the debounce window
        return toId(guid)
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        val state = playlists.values.firstOrNull { toId(it.guid) == playlistId } ?: return
        val mod = F2plMod(modGuid = UUID.randomUUID().toString(), ts = nowIso(), op = "RenamePlaylist", name = newName)
        state.file = state.file.copy(name = newName, modifications = state.file.modifications + mod)
        emitFlow()
        saveNow(state)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        val state = playlists.values.firstOrNull { toId(it.guid) == playlistId } ?: return
        debounceJobs[state.guid]?.cancel()
        debounceJobs.remove(state.guid)
        playlists.remove(state.guid)
        emitFlow()
        scope.launch {
            for (name in state.loadedFileNames.toList()) {
                profileManager.deleteFile(name)
            }
        }
    }

    suspend fun addTracksToPlaylist(playlistId: Long, tracks: List<TrackInfo>) {
        val state = playlists.values.firstOrNull { toId(it.guid) == playlistId } ?: return
        var nextIndex = (state.file.fileRefs.maxOfOrNull { it.index } ?: -1) + 1
        val newRefs = mutableListOf<F2plFileRef>()
        val newMods = mutableListOf<F2plMod>()

        for (track in tracks) {
            val ref = F2plFileRef(
                index = nextIndex,
                relativePaths = listOfNotNull(track.uri.takeIf { track.source == "local" }),
                oneDriveItemId = track.sourceId.takeIf { track.source == "onedrive" }
            )
            newRefs += ref
            newMods += F2plMod(
                modGuid = UUID.randomUUID().toString(),
                ts = nowIso(),
                op = "AddSong",
                fileIndex = nextIndex,
                title = track.title,
                source = track.source,
                uri = track.uri
            )
            nextIndex++
        }

        state.file = state.file.copy(
            fileRefs = state.file.fileRefs + newRefs,
            modifications = state.file.modifications + newMods
        )
        emitFlow()
        scheduleSave(state)   // debounced — rapid adds (e.g. check-all) coalesce into one write
    }

    /**
     * Remove the track at [position] (0-indexed in the current displayed list) from the playlist.
     */
    suspend fun removeTrackAtPosition(playlistId: Long, position: Int) {
        val state = playlists.values.firstOrNull { toId(it.guid) == playlistId } ?: return
        val active = buildActiveList(state.file)
        val fileRefIndex = active.getOrNull(position) ?: return
        val mod = F2plMod(modGuid = UUID.randomUUID().toString(), ts = nowIso(), op = "RemoveSong", fileIndex = fileRefIndex)
        state.file = state.file.copy(modifications = state.file.modifications + mod)
        emitFlow()
        saveNow(state)
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Builds the ordered list of active fileRef indices by replaying the modification log.
     * RemoveSong removes the last occurrence of the given fileIndex.
     */
    private fun buildActiveList(file: F2plFile): List<Int> {
        val active = mutableListOf<Int>()
        for (mod in file.modifications.sortedBy { it.ts }) {
            when (mod.op) {
                "AddSong" -> mod.fileIndex?.let { active += it }
                "RemoveSong" -> mod.fileIndex?.let { fi ->
                    val idx = active.indexOfLast { it == fi }
                    if (idx >= 0) active.removeAt(idx)
                }
            }
        }
        return active
    }

    private fun reconstructTracks(file: F2plFile, playlistId: Long): List<PlaylistTrack> {
        val active = buildActiveList(file)
        return active.mapIndexed { pos, fileRefIndex ->
            val ref = file.fileRefs.firstOrNull { it.index == fileRefIndex }
            val addMod = file.modifications
                .filter { it.op == "AddSong" && it.fileIndex == fileRefIndex }
                .lastOrNull()
            PlaylistTrack(
                id = pos.toLong(),              // position used as ID for deletion
                playlistId = playlistId,
                title = addMod?.title
                    ?: ref?.relativePaths?.firstOrNull()?.substringAfterLast('/')
                    ?: "Unknown",
                source = addMod?.source ?: "onedrive",
                uri = addMod?.uri ?: "",
                sourceId = ref?.oneDriveItemId ?: ref?.relativePaths?.firstOrNull() ?: "",
                position = pos
            )
        }
    }

    private fun merge(files: List<F2plFile>): F2plFile {
        if (files.size == 1) return files[0]
        val base = files.first()
        val allRefs = files.flatMap { it.fileRefs }.distinctBy { it.index }.sortedBy { it.index }
        val allMods = files.flatMap { it.modifications }.distinctBy { it.modGuid }.sortedBy { it.ts }
        val name = allMods.lastOrNull { it.op == "RenamePlaylist" }?.name ?: base.name
        return F2plFile(
            version = 1,
            playlistGuid = base.playlistGuid,
            name = name,
            fileRefs = allRefs,
            modifications = allMods
        )
    }

    private suspend fun compact(state: PlaylistState) {
        val newName = buildFileName(state.file)
        val json = gson.toJson(state.file)
        val writeResult = profileManager.writeFile(newName, json)
        if (writeResult.isFailure) {
            Log.e(TAG, "Compact write failed for ${state.guid}")
            return
        }
        // Delete old files
        val oldNames = state.loadedFileNames.toList()
        for (old in oldNames) {
            if (old != newName) profileManager.deleteFile(old)
        }
        state.loadedFileNames.clear()
        state.loadedFileNames += newName
    }

    private fun scheduleSave(state: PlaylistState) {
        debounceJobs[state.guid]?.cancel()
        debounceJobs[state.guid] = scope.launch {
            delay(WRITE_DEBOUNCE_MS)
            save(state)
        }
    }

    /** Save immediately on the IO scope. Returns as soon as the launch is dispatched. */
    private fun saveNow(state: PlaylistState) {
        debounceJobs[state.guid]?.cancel()   // cancel any pending debounce
        debounceJobs.remove(state.guid)
        scope.launch { save(state) }
    }

    private suspend fun save(state: PlaylistState) {
        try {
            val newName = buildFileName(state.file)
            val json = gson.toJson(state.file)
            Log.d(TAG, "Saving playlist '${state.file.name}' → $newName")
            val result = profileManager.writeFile(newName, json)
            if (result.isFailure) {
                Log.e(TAG, "writeFile failed for ${state.guid}", result.exceptionOrNull())
                return
            }
            val old = state.loadedFileNames.toList()
            for (name in old) {
                if (name != newName) profileManager.deleteFile(name)
            }
            state.loadedFileNames.clear()
            state.loadedFileNames += newName
            Log.d(TAG, "Saved playlist: $newName")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error saving playlist ${state.guid}", e)
        }
    }

    private fun emitFlow() {
        val entities = playlists.values.map { state ->
            PlaylistEntity(
                id = toId(state.guid),
                name = state.file.name,
                createdAt = 0L,
                updatedAt = 0L
            )
        }.sortedByDescending { it.id }
        _flow.value = entities
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Deterministic Long ID from UUID string. */
    private fun toId(guid: String): Long = abs(guid.hashCode().toLong())

    private fun nowIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date())
    }

    private fun buildFileName(file: F2plFile): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val ts = sdf.format(Date())
        val safeName = file.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        return "$ts - $safeName.$EXTENSION"
    }
}
