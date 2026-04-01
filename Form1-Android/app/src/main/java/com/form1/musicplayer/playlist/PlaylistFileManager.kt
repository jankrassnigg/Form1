package com.form1.musicplayer.playlist

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.form1.musicplayer.data.Playlist
import com.form1.musicplayer.data.PlaylistEntity
import com.form1.musicplayer.data.PlaylistTrack
import com.form1.musicplayer.data.TrackInfo
import com.form1.musicplayer.onedrive.OneDriveCacheManager
import com.form1.musicplayer.onedrive.OneDriveDownloadQueue
import com.form1.musicplayer.profile.ProfileConfig
import com.form1.musicplayer.profile.ProfileManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val cacheManager = OneDriveCacheManager.getInstance(context)
    private val profileManager = ProfileManager(context, profileConfig, cacheManager)
    private val downloadQueue = OneDriveDownloadQueue.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(F2plFile::class.java, JsonSerializer<F2plFile> { src, _, ctx ->
            JsonObject().apply {
                addProperty("version", src.version)
                addProperty("playlistGuid", src.playlistGuid)
                add("references", ctx.serialize(src.references))
                add("modifications", ctx.serialize(src.modifications))
            }
        })
        .registerTypeAdapter(F2plRef::class.java, JsonSerializer<F2plRef> { src, _, _ ->
            JsonObject().apply {
                addProperty("display", src.display)
                if (src.oneDriveItemId != null) addProperty("oneDriveItemId", src.oneDriveItemId)
                if (src.relativePaths.isNotEmpty()) {
                    add("relativePaths", com.google.gson.JsonArray().also { arr ->
                        src.relativePaths.forEach { arr.add(it) }
                    })
                }
            }
        })
        .registerTypeAdapter(F2plMod::class.java, JsonSerializer<F2plMod> { src, _, _ ->
            JsonObject().apply {
                addProperty("modGuid", src.modGuid)
                addProperty("op", src.op)
                addProperty("ts", src.ts)
                if (src.ref != null) addProperty("ref", src.ref)
                if (src.misc != null) addProperty("misc", src.misc)
            }
        })
        .create()

    // ── In-memory state ───────────────────────────────────────────────────────

    /** guid → current playlist state */
    private val playlists = mutableMapOf<String, PlaylistState>()
    private val _flow = MutableStateFlow<List<PlaylistEntity>>(emptyList())

    /** True while the initial load from storage is in progress. */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** pending debounce jobs keyed by playlist guid */
    private val debounceJobs = mutableMapOf<String, Job>()

    private data class PlaylistState(
        val guid: String,
        var file: F2plFile,
        /** resolved display name — cached to avoid re-scanning mods on every emitFlow() */
        var name: String,
        /** file names (without path) loaded from disk; used to delete stale files after compact */
        val loadedFileNames: MutableList<String> = mutableListOf()
    )

    // ── Startup ───────────────────────────────────────────────────────────────

    init {
        scope.launch { loadAll() }
        // Flush any pending debounce saves when the app goes to background so that
        // modifications are never lost on process kill (e.g. redeploy, force-close).
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    flushPendingSaves()
                }
            })
        }
    }

    /** Immediately save all playlists that have a pending debounced write. */
    fun flushPendingSaves() {
        val pending = debounceJobs.keys.toList()
        for (guid in pending) {
            debounceJobs[guid]?.cancel()
            debounceJobs.remove(guid)
            val state = playlists[guid] ?: continue
            scope.launch { save(state) }
        }
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
                name = nameFromMods(merged.modifications),
                loadedFileNames = pairs.map { it.first }.toMutableList()
            )
            playlists[guid] = state

            if (pairs.size > 1) {
                compact(state)
            }
        }
        emitFlow()
        _isLoading.value = false
        enqueueOfflineDownloads()
    }

    /** After load, kick off background downloads for any offline playlist tracks not yet cached. */
    private fun enqueueOfflineDownloads() {
        for (state in playlists.values) {
            if (!offlineFromMods(state.file.modifications)) continue
            val tracks = reconstructTracks(state.file, toId(state.guid))
            for (track in tracks) {
                if (track.source == "onedrive" && !cacheManager.isCached(track.sourceId)) {
                    downloadQueue.enqueue(track.sourceId, track.title)
                }
            }
        }
    }

    // ── Public query API ──────────────────────────────────────────────────────

    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = _flow

    suspend fun getPlaylistWithTracks(playlistId: Long): Playlist? =
        withContext(Dispatchers.Default) {
            val state = playlists.values.firstOrNull { toId(it.guid) == playlistId } ?: return@withContext null
            Playlist(
                id = playlistId,
                name = state.name,
                tracks = reconstructTracks(state.file, playlistId),
                createdAt = 0L,
                updatedAt = 0L,
                offlineAvailable = offlineFromMods(state.file.modifications)
            )
        }

    // ── Public mutation API ───────────────────────────────────────────────────

    suspend fun createPlaylist(name: String): Long {
        val guid = UUID.randomUUID().toString()
        val renameMod = F2plMod(
            modGuid = UUID.randomUUID().toString(),
            ts = nowIso(),
            op = "RenamePlaylist",
            misc = name
        )
        val file = F2plFile(playlistGuid = guid, modifications = listOf(renameMod))
        val state = PlaylistState(guid = guid, file = file, name = name)
        playlists[guid] = state
        emitFlow()
        saveNow(state)   // immediate — don't risk losing a new playlist to the debounce window
        return toId(guid)
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        val state = playlists.values.firstOrNull { toId(it.guid) == playlistId } ?: return
        val mod = F2plMod(modGuid = UUID.randomUUID().toString(), ts = nowIso(), op = "RenamePlaylist", misc = newName)
        state.file = state.file.copy(modifications = state.file.modifications + mod)
        state.name = newName
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
        val newRefs = mutableListOf<F2plRef>()
        val newMods = mutableListOf<F2plMod>()
        var nextRefIndex = state.file.references.size

        for (track in tracks) {
            val ref = F2plRef(
                display = track.title,
                oneDriveItemId = track.sourceId.takeIf { track.source == "onedrive" },
                relativePaths = listOfNotNull(track.uri.takeIf { track.source == "local" })
            )
            newRefs += ref
            newMods += F2plMod(
                modGuid = UUID.randomUUID().toString(),
                ts = nowIso(),
                op = "AddSong",
                ref = nextRefIndex
            )
            nextRefIndex++
        }

        state.file = state.file.copy(
            references = state.file.references + newRefs,
            modifications = state.file.modifications + newMods
        )
        emitFlow()
        scheduleSave(state)   // debounced — rapid adds (e.g. check-all) coalesce into one write

        // If the playlist is marked offline, enqueue newly added OneDrive tracks for download
        if (offlineFromMods(state.file.modifications)) {
            for (track in tracks) {
                if (track.source == "onedrive" && !cacheManager.isCached(track.sourceId)) {
                    downloadQueue.enqueue(track.sourceId, track.title)
                }
            }
        }
    }

    /**
     * Remove the track at [position] (0-indexed in the current displayed list) from the playlist.
     */
    suspend fun setOfflineAvailable(playlistId: Long, enabled: Boolean) {
        val state = playlists.values.firstOrNull { toId(it.guid) == playlistId } ?: return
        val mod = F2plMod(
            modGuid = UUID.randomUUID().toString(),
            ts = nowIso(),
            op = "SetOfflineAvailable",
            misc = enabled.toString()
        )
        state.file = state.file.copy(modifications = state.file.modifications + mod)
        saveNow(state)
    }

    suspend fun getDownloadUrl(itemId: String): Result<String> =
        cacheManager.getDownloadUrl(itemId)

    suspend fun removeTrackAtPosition(playlistId: Long, position: Int) {
        val state = playlists.values.firstOrNull { toId(it.guid) == playlistId } ?: return
        val active = buildActiveList(state.file)
        val refIndex = active.getOrNull(position) ?: return
        val mod = F2plMod(modGuid = UUID.randomUUID().toString(), ts = nowIso(), op = "RemoveSong", ref = refIndex)
        state.file = state.file.copy(modifications = state.file.modifications + mod)
        emitFlow()
        saveNow(state)
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Builds the ordered list of active reference indices by replaying the modification log.
     * RemoveSong removes the last occurrence of the given ref index.
     */
    private fun buildActiveList(file: F2plFile): List<Int> {
        val active = mutableListOf<Int>()
        for (mod in file.modifications.sortedBy { it.ts }) {
            when (mod.op) {
                "AddSong" -> mod.ref?.let { active += it }
                "RemoveSong" -> mod.ref?.let { ri ->
                    val idx = active.indexOfLast { it == ri }
                    if (idx >= 0) active.removeAt(idx)
                }
            }
        }
        return active
    }

    private fun reconstructTracks(file: F2plFile, playlistId: Long): List<PlaylistTrack> {
        val active = buildActiveList(file)
        return active.mapIndexed { pos, refIndex ->
            val ref = file.references.getOrNull(refIndex)
            val source = if (ref?.oneDriveItemId != null) "onedrive" else "local"
            PlaylistTrack(
                id = pos.toLong(),              // position used as ID for deletion
                playlistId = playlistId,
                title = ref?.display ?: "Unknown",
                source = source,
                uri = "",                       // not stored; resolved at playback time
                sourceId = ref?.oneDriveItemId ?: ref?.relativePaths?.firstOrNull() ?: "",
                position = pos
            )
        }
    }

    private fun merge(files: List<F2plFile>): F2plFile {
        if (files.size == 1) return files[0]
        val base = files.first()

        // Collect unique refs; dedup by (display, oneDriveItemId) pair
        val uniqueRefs = files.flatMap { it.references }
            .distinctBy { it.display to it.oneDriveItemId }

        // Build old-index-to-new-index remapping per file
        // For each file, its refs are at positions 0..n-1 in that file's list.
        // After dedup, find their new position in uniqueRefs.
        val fileMods = files.flatMap { file ->
            file.modifications.map { mod ->
                if (mod.ref == null) {
                    mod
                } else {
                    val oldRef = file.references.getOrNull(mod.ref)
                    val newIdx = if (oldRef != null) {
                        uniqueRefs.indexOfFirst {
                            it.display == oldRef.display && it.oneDriveItemId == oldRef.oneDriveItemId
                        }.takeIf { it >= 0 } ?: mod.ref
                    } else {
                        mod.ref
                    }
                    mod.copy(ref = newIdx)
                }
            }
        }

        val allMods = fileMods.distinctBy { it.modGuid }.sortedBy { it.ts }
        return F2plFile(
            version = 1,
            playlistGuid = base.playlistGuid,
            references = uniqueRefs,
            modifications = allMods
        )
    }

    private fun nameFromMods(mods: List<F2plMod>): String =
        mods.sortedBy { it.ts }.lastOrNull { it.op == "RenamePlaylist" }?.misc ?: "Unnamed"

    private fun offlineFromMods(mods: List<F2plMod>): Boolean =
        mods.sortedBy { it.ts }.lastOrNull { it.op == "SetOfflineAvailable" }?.misc == "true"

    private suspend fun compact(state: PlaylistState) {
        val newName = buildFileName(state)
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
            val newName = buildFileName(state)
            val json = gson.toJson(state.file)
            Log.d(TAG, "Saving playlist '${state.name}' → $newName")
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
                name = state.name,
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

    private fun buildFileName(state: PlaylistState): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val ts = sdf.format(Date())
        val safeName = state.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        return "$ts - $safeName.$EXTENSION"
    }
}
