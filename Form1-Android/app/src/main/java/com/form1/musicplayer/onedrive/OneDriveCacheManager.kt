package com.form1.musicplayer.onedrive

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Caching layer over [OneDriveService].
 *
 * Singleton — call [getInstance] everywhere instead of constructing [OneDriveService] directly.
 *
 * Caches:
 *   - Folder contents in memory (5-minute TTL); invalidated on pull-to-refresh.
 *   - Search results in memory (10-minute TTL).
 *   - Download URLs in memory (50-minute TTL, just under the ~1-hour Graph API expiry).
 *   - File content on disk under `cacheDir/onedrive/{itemId}`; validated by eTag.
 *     Immutable files (music, playlist files) are never re-downloaded once present.
 *
 * Offline fallback: if a network call fails and a stale cache entry exists, the stale
 * data is returned so the app remains partially functional without connectivity.
 *
 * Write operations (upload, delete, createFolder) go through [service] directly — they
 * are not cached, but successful mutations should call [invalidateFolder] to keep the
 * in-memory folder cache consistent.
 */
class OneDriveCacheManager private constructor(context: Context) {

    // ── Dependencies ──────────────────────────────────────────────────────────

    /** Exposed for auth operations (initialize, isSignedIn, signIn, signOut). */
    val auth = OneDriveAuthManager(context)

    /** Exposed for write operations (uploadTextFile, deleteFile, createFolder, listFilesInFolderWithIds). */
    val service = OneDriveService(auth)

    private val cacheDir = File(context.cacheDir, "onedrive").also { it.mkdirs() }

    companion object {
        private const val TAG = "OneDriveCacheManager"

        private const val FOLDER_TTL_MS = 5 * 60 * 1_000L    // 5 min
        private const val SEARCH_TTL_MS = 10 * 60 * 1_000L   // 10 min
        private const val URL_TTL_MS    = 50 * 60 * 1_000L   // 50 min (Graph URLs expire in ~1 h)

        @Volatile
        private var instance: OneDriveCacheManager? = null

        fun getInstance(context: Context): OneDriveCacheManager =
            instance ?: synchronized(this) {
                instance ?: OneDriveCacheManager(context.applicationContext).also { instance = it }
            }
    }

    // ── In-memory caches ──────────────────────────────────────────────────────

    private data class CachedFolder(val contents: OneDriveFolderContents, val timestamp: Long)
    private val folderCache = ConcurrentHashMap<String, CachedFolder>()

    private data class CachedSearch(val results: List<OneDriveFile>, val timestamp: Long)
    private val searchCache = ConcurrentHashMap<String, CachedSearch>()

    private data class CachedUrl(val url: String, val timestamp: Long)
    private val urlCache = ConcurrentHashMap<String, CachedUrl>()

    private data class CachedFileList(val files: List<Pair<String, String>>, val timestamp: Long)
    private val fileListCache = ConcurrentHashMap<String, CachedFileList>()

    // ── Folder content cache ──────────────────────────────────────────────────

    /**
     * List items (folders + audio files) in [folderId] (null = root).
     * Returns in-memory cached result if still valid, otherwise fetches from Graph API.
     * Falls back to stale cache if the API call fails (offline mode).
     */
    suspend fun listFolderContents(folderId: String?): Result<OneDriveFolderContents> {
        val key = folderId ?: "root"
        val now = System.currentTimeMillis()
        val cached = folderCache[key]

        if (cached != null && now - cached.timestamp < FOLDER_TTL_MS) {
            return Result.success(cached.contents)
        }

        val result = service.listFolderContents(folderId)
        result.onSuccess { contents ->
            folderCache[key] = CachedFolder(contents, now)
        }.onFailure {
            // Offline fallback: return stale data if available
            if (cached != null) {
                Log.w(TAG, "Network error listing folder $key — returning stale cache")
                return Result.success(cached.contents)
            }
        }
        return result
    }

    /** Remove a single folder from the in-memory cache (call after pull-to-refresh). */
    fun invalidateFolder(folderId: String?) {
        folderCache.remove(folderId ?: "root")
    }

    /** Clear folder and search caches (e.g. on sign-out or full refresh). */
    fun invalidateAll() {
        folderCache.clear()
        searchCache.clear()
        fileListCache.clear()
        // URL and disk caches remain valid — tokens are independent of structure
    }

    // ── Profile folder file listing cache ─────────────────────────────────────

    /**
     * List files in [folderId] as (name, itemId) pairs.
     * Cached in memory for [FOLDER_TTL_MS] — eliminates repeated API calls during playlist load.
     * Call [invalidateFileList] after uploading or deleting files in the folder.
     */
    suspend fun listFilesInFolderWithIds(folderId: String): Result<List<Pair<String, String>>> {
        val now = System.currentTimeMillis()
        val cached = fileListCache[folderId]
        if (cached != null && now - cached.timestamp < FOLDER_TTL_MS) {
            return Result.success(cached.files)
        }
        return service.listFilesInFolderWithIds(folderId).onSuccess { files ->
            fileListCache[folderId] = CachedFileList(files, now)
        }
    }

    /** Invalidate the file listing cache for [folderId] after a write or delete. */
    fun invalidateFileList(folderId: String) {
        fileListCache.remove(folderId)
    }

    // ── Search cache ──────────────────────────────────────────────────────────

    /**
     * Search for audio files matching [query] within [rootFolderId] (null = all OneDrive).
     * Results are cached in memory for [SEARCH_TTL_MS].
     * Falls back to stale results on network failure.
     */
    suspend fun searchAudioFiles(query: String, rootFolderId: String?): Result<List<OneDriveFile>> {
        val key = "$query|$rootFolderId"
        val now = System.currentTimeMillis()
        val cached = searchCache[key]

        if (cached != null && now - cached.timestamp < SEARCH_TTL_MS) {
            return Result.success(cached.results)
        }

        val result = service.searchAudioFiles(query, rootFolderId)
        result.onSuccess { results ->
            searchCache[key] = CachedSearch(results, now)
        }.onFailure {
            if (cached != null) {
                Log.w(TAG, "Network error searching '$query' — returning stale cache")
                return Result.success(cached.results)
            }
        }
        return result
    }

    // ── Download URL cache ────────────────────────────────────────────────────

    /**
     * Get a temporary playback URL for [itemId].
     * Cached in memory for [URL_TTL_MS] (just under Graph API's ~1-hour expiry).
     *
     * Offline fallback priority:
     *   1. Valid cached URL (in memory).
     *   2. Local disk-cached file → returns a `file://` URI so ExoPlayer can play offline.
     *   3. Stale cached URL (may still work if not too old).
     *   4. Failure.
     */
    suspend fun getDownloadUrl(itemId: String): Result<String> {
        val now = System.currentTimeMillis()
        val cached = urlCache[itemId]

        if (cached != null && now - cached.timestamp < URL_TTL_MS) {
            return Result.success(cached.url)
        }

        val result = service.getDownloadUrl(itemId)
        result.onSuccess { url ->
            urlCache[itemId] = CachedUrl(url, now)
        }.onFailure {
            // Offline fallback 1: serve from disk cache
            val localFile = getCachedFile(itemId)
            if (localFile != null) {
                Log.i(TAG, "Offline: serving cached file for $itemId")
                return Result.success(localFile.toURI().toString())
            }
            // Offline fallback 2: return stale URL (might still work)
            if (cached != null) {
                Log.w(TAG, "Network error for $itemId — returning stale URL")
                return Result.success(cached.url)
            }
        }
        return result
    }

    // ── File content disk cache ───────────────────────────────────────────────

    /**
     * Download and cache the binary content of [itemId] to disk.
     *
     * If [isImmutable] is true (music files, playlist files), the cached file is
     * returned immediately without checking the eTag. If false, the eTag is compared
     * against the server before using the cached version.
     *
     * On network failure, returns the cached bytes if available.
     */
    suspend fun getFileContent(itemId: String, isImmutable: Boolean = false): Result<ByteArray> {
        val cachedBytes = readDiskCache(itemId)

        if (cachedBytes != null) {
            if (isImmutable) return Result.success(cachedBytes)

            // eTag-based validation for mutable files
            val cachedETag = readCachedETag(itemId)
            val metadata = service.getItemMetadata(itemId).getOrNull()
            if (metadata != null && cachedETag != null && metadata.eTag == cachedETag) {
                return Result.success(cachedBytes)
            }
            // eTag mismatch or missing — fall through to download
        }

        val result = service.downloadFileBytes(itemId)
        result.onSuccess { bytes ->
            val eTag = if (!isImmutable) service.getItemMetadata(itemId).getOrNull()?.eTag else null
            writeDiskCache(itemId, bytes, eTag)
        }.onFailure {
            // Offline fallback: return stale cached bytes if available
            if (cachedBytes != null) {
                Log.w(TAG, "Network error for $itemId — returning stale disk cache")
                return Result.success(cachedBytes)
            }
        }
        return result
    }

    /**
     * Convenience wrapper for profile text files (immutable .f2pl files).
     * Downloads and caches the content; returns it as a UTF-8 string.
     */
    suspend fun getTextFileContent(itemId: String): Result<String> =
        getFileContent(itemId, isImmutable = true).map { String(it, Charsets.UTF_8) }

    /** Returns true if [itemId] has been downloaded to the disk cache. */
    fun isCached(itemId: String): Boolean = diskCacheFile(itemId).exists()

    /** Returns the cached [File] for [itemId], or null if not cached. */
    fun getCachedFile(itemId: String): File? = diskCacheFile(itemId).takeIf { it.exists() }

    // ── Disk cache helpers ────────────────────────────────────────────────────

    private fun diskCacheFile(itemId: String) = File(cacheDir, itemId)
    private fun eTagFile(itemId: String) = File(cacheDir, "$itemId.etag")

    private fun readDiskCache(itemId: String): ByteArray? =
        try { diskCacheFile(itemId).takeIf { it.exists() }?.readBytes() }
        catch (e: Exception) { Log.w(TAG, "Failed to read disk cache for $itemId", e); null }

    private fun readCachedETag(itemId: String): String? =
        try { eTagFile(itemId).takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null } }
        catch (e: Exception) { null }

    private fun writeDiskCache(itemId: String, bytes: ByteArray, eTag: String?) {
        try {
            diskCacheFile(itemId).writeBytes(bytes)
            if (eTag != null) eTagFile(itemId).writeText(eTag)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write disk cache for $itemId", e)
        }
    }
}
