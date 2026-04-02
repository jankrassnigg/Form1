package com.form1.musicplayer.onedrive

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Service for interacting with Microsoft Graph API (OneDrive)
 */
class OneDriveService(private val authManager: OneDriveAuthManager) {

    private val client = OkHttpClient()
    private val gson = Gson()

    companion object {
        private const val TAG = "OneDriveService"
        private const val GRAPH_API_BASE = "https://graph.microsoft.com/v1.0"

        // Audio file extensions to filter
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "mp4", "flac", "wav", "ogg",
            "aac", "wma", "opus", "ape", "alac", "webm"
        )

        // Extensions to use as search queries (OneDrive search has no OR operator —
        // must make one call per term and deduplicate results by item ID)
        private val SEARCH_EXTENSIONS = listOf("mp3", "m4a", "mp4", "flac", "wav", "ogg", "aac", "webm")
    }

    /**
     * List all audio files in OneDrive (search globally).
     * OneDrive search has no OR operator, so we make one call per extension and deduplicate by ID.
     */
    suspend fun listAudioFiles(): Result<List<OneDriveFile>> = withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            val seen = mutableSetOf<String>()
            val audioFiles = mutableListOf<OneDriveFile>()

            for (ext in SEARCH_EXTENSIONS) {
                val url = "$GRAPH_API_BASE/me/drive/root/search(q='.$ext')"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Search for .$ext failed: ${response.code}")
                        return@use
                    }
                    val body = response.body?.string() ?: return@use
                    val apiResponse = gson.fromJson(body, DriveItemsResponse::class.java)
                    apiResponse.value
                        .filter { it.file != null }
                        .filter { item ->
                            val extension = item.name.substringAfterLast('.', "").lowercase()
                            extension in AUDIO_EXTENSIONS
                        }
                        .forEach { item ->
                            if (seen.add(item.id)) {
                                audioFiles.add(item.toOneDriveFile())
                            }
                        }
                }
            }

            Log.d(TAG, "Found ${audioFiles.size} audio files")
            Result.success(audioFiles)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files", e)
            Result.failure(e)
        }
    }

    /**
     * List items (folders and audio files) in a specific folder
     * @param folderId The folder ID, or null for root
     */
    suspend fun listFolderContents(folderId: String? = null): Result<OneDriveFolderContents> = withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getAccessToken()
            if (accessToken == null) {
                return@withContext Result.failure(Exception("Not authenticated"))
            }

            // Build URL for folder contents.
            // Remote/shared folders have compound IDs encoded as "driveId:itemId" and require
            // the /drives/{driveId}/items/{itemId}/children endpoint.
            // We also remember the driveId context so child folders inherit it.
            val contextDriveId: String?
            val url = when {
                folderId == null -> {
                    contextDriveId = null
                    "$GRAPH_API_BASE/me/drive/root/children"
                }
                folderId.contains(':') -> {
                    val (driveId, itemId) = folderId.split(':', limit = 2)
                    contextDriveId = driveId
                    "$GRAPH_API_BASE/drives/$driveId/items/$itemId/children"
                }
                else -> {
                    contextDriveId = null
                    "$GRAPH_API_BASE/me/drive/items/$folderId/children"
                }
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "API request failed: ${response.code} ${response.message}")
                    return@withContext Result.failure(IOException("API request failed: ${response.code}"))
                }

                val body = response.body?.string()
                if (body == null) {
                    return@withContext Result.failure(IOException("Empty response"))
                }

                val apiResponse = gson.fromJson(body, DriveItemsResponse::class.java)

                // Separate folders and audio files
                val folders = mutableListOf<OneDriveFolder>()
                val audioFiles = mutableListOf<OneDriveFile>()

                apiResponse.value.forEach { item ->
                    when {
                        item.folder != null -> {
                            // Regular folder — if we're inside a remote drive, prefix the ID so
                            // subsequent navigation uses the correct /drives/{driveId}/... endpoint.
                            val folder = if (contextDriveId != null) {
                                item.toOneDriveFolder().copy(id = "$contextDriveId:${item.id}")
                            } else {
                                item.toOneDriveFolder()
                            }
                            folders.add(folder)
                        }
                        item.remoteItem?.folder != null -> {
                            // Shared/remote folder integrated into this drive (e.g. via "Add shortcut to My files")
                            folders.add(item.toRemoteOneDriveFolder())
                        }
                        item.file != null -> {
                            // It's a file - check if it's an audio file.
                            // If inside a remote drive, prefix the ID so URL resolution uses the
                            // correct /drives/{driveId}/items/{itemId} endpoint later.
                            val extension = item.name.substringAfterLast('.', "").lowercase()
                            if (extension in AUDIO_EXTENSIONS) {
                                val file = item.toOneDriveFile()
                                audioFiles.add(
                                    if (contextDriveId != null) file.copy(id = "$contextDriveId:${item.id}")
                                    else file
                                )
                            }
                        }
                    }
                }

                Log.d(TAG, "Found ${folders.size} folders and ${audioFiles.size} audio files")
                Result.success(OneDriveFolderContents(folders, audioFiles))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing folder contents", e)
            Result.failure(e)
        }
    }

    /**
     * Search for audio files matching [query] within the given root folder (or all of OneDrive).
     *
     * Two-phase strategy:
     *   Phase 1 — Graph API search by user query: finds audio files whose *name* matches and
     *             folders whose name matches (e.g. artist/album folders).
     *   Phase 2 — for each matching folder, list its children via the `children` endpoint
     *             (two levels deep: artist → album → songs). The `children` endpoint is always
     *             correctly scoped, unlike folder-scoped `search` which can return drive-wide
     *             results.
     *
     * This lets "Beatles" find songs inside a folder named "The Beatles" even though the
     * individual filenames don't contain "Beatles".
     */
    /**
     * Search for audio files matching [query] within the given root folder (or all of OneDrive).
     *
     * Multi-keyword strategy:
     *   - Split query on whitespace into individual keywords.
     *   - Use the longest keyword as the primary Graph API search term (most selective).
     *   - Phase 1: Graph search finds audio files and folders whose name contains that keyword.
     *   - Phase 2: For each matching folder, list children two levels deep (artist → album →
     *     songs) using the `children` endpoint, which is correctly scoped (unlike folder-scoped
     *     `search`).
     *   - Final filter: keep only files where ALL keywords appear somewhere in the combined
     *     parent path + filename (case-insensitive). This enforces AND semantics across the
     *     full path without extra API calls.
     */
    suspend fun searchAudioFiles(query: String, rootFolderId: String?): Result<List<OneDriveFile>> = withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            // Split into keywords; pick the longest as the primary API search term
            val keywords = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (keywords.isEmpty()) return@withContext Result.success(emptyList())
            val primaryKeyword = keywords.maxByOrNull { it.length } ?: keywords.first()

            val seen = mutableSetOf<String>()
            val candidates = mutableListOf<OneDriveFile>()

            // Phase 1: Graph search by the primary keyword — matches file and folder names
            val escaped = primaryKeyword.replace("'", "''")
            val phase1Url = when {
                rootFolderId.isNullOrEmpty() -> "$GRAPH_API_BASE/me/drive/root/search(q='$escaped')"
                rootFolderId.contains(':') -> {
                    val (driveId, itemId) = rootFolderId.split(':', limit = 2)
                    "$GRAPH_API_BASE/drives/$driveId/items/$itemId/search(q='$escaped')"
                }
                else -> "$GRAPH_API_BASE/me/drive/items/$rootFolderId/search(q='$escaped')"
            }

            val phase1Items = fetchItems(phase1Url, accessToken)
                ?: return@withContext Result.failure(IOException("Search failed"))

            // Direct audio file hits
            phase1Items
                .filter { it.file != null && it.name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS }
                .forEach { if (seen.add(it.id)) candidates.add(it.toOneDriveFile()) }

            // Phase 2: expand matching folders via children endpoint (correctly scoped).
            // Use compound "driveId:itemId" for remote folders so listChildren hits the right endpoint.
            val folderHits = phase1Items.filter { it.folder != null || it.remoteItem?.folder != null }.take(5)
            for (artistFolder in folderHits) {
                val browserId = if (artistFolder.remoteItem?.folder != null) {
                    artistFolder.toRemoteOneDriveFolder().id
                } else {
                    artistFolder.id
                }
                val level1 = listChildren(browserId, accessToken) ?: continue

                level1.filter { it.file != null && it.name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS }
                    .forEach { if (seen.add(it.id)) candidates.add(it.toOneDriveFile()) }

                val subFolders = level1.filter { it.folder != null }.take(50)
                for (albumFolder in subFolders) {
                    val level2 = listChildren(albumFolder.id, accessToken) ?: continue
                    level2.filter { it.file != null && it.name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS }
                        .forEach { if (seen.add(it.id)) candidates.add(it.toOneDriveFile()) }
                }
            }

            // Final filter: all keywords must appear somewhere in path + filename
            val results = if (keywords.size == 1) {
                candidates
            } else {
                candidates.filter { file ->
                    val searchable = "${file.parentPath ?: ""} ${file.name}".lowercase()
                    keywords.all { kw -> kw.lowercase() in searchable }
                }
            }

            Log.d(TAG, "Search '$query': ${candidates.size} candidates → ${results.size} after keyword filter")
            Result.success(results.take(50))
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for '$query'", e)
            Result.failure(e)
        }
    }

    // ── Profile file I/O ──────────────────────────────────────────────────────

    /**
     * Upload [content] as a UTF-8 text file named [fileName] into the OneDrive folder [folderId].
     * Uses the Graph API PUT upload (simple upload, max 4 MB).
     * If a file with the same name already exists it will be overwritten.
     */
    suspend fun uploadTextFile(folderId: String, fileName: String, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val accessToken = authManager.getAccessToken()
                    ?: return@withContext Result.failure(Exception("Not authenticated"))

                val encodedName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
                val url = "$GRAPH_API_BASE/me/drive/items/$folderId:/$encodedName:/content"
                val body = content.toByteArray(Charsets.UTF_8)
                    .toRequestBody("text/plain; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .put(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IOException("Upload failed: ${response.code} ${response.message}")
                        )
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading file: $fileName", e)
                Result.failure(e)
            }
        }

    /**
     * Download the text content of a OneDrive file by [itemId].
     */
    suspend fun downloadFileContent(itemId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            val url = "$GRAPH_API_BASE/me/drive/items/$itemId/content"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            // OkHttp follows redirects by default, so this will fetch the actual content
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Download failed: ${response.code} ${response.message}")
                    )
                }
                val text = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response body"))
                Result.success(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file: $itemId", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a OneDrive item (file or folder) by [itemId].
     */
    suspend fun deleteFile(itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            val url = "$GRAPH_API_BASE/me/drive/items/$itemId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                // 204 No Content is the success response for DELETE
                if (!response.isSuccessful && response.code != 204) {
                    return@withContext Result.failure(
                        IOException("Delete failed: ${response.code} ${response.message}")
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: $itemId", e)
            Result.failure(e)
        }
    }

    /**
     * List file names in a OneDrive folder. Returns only files (not sub-folders).
     */
    suspend fun listFilesInFolder(folderId: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val accessToken = authManager.getAccessToken()
                    ?: return@withContext Result.failure(Exception("Not authenticated"))
                val items = fetchItems("$GRAPH_API_BASE/me/drive/items/$folderId/children", accessToken)
                    ?: return@withContext Result.failure(IOException("Failed to list folder"))
                val names = items.filter { it.file != null }.map { it.name }
                Result.success(names)
            } catch (e: Exception) {
                Log.e(TAG, "Error listing files in folder: $folderId", e)
                Result.failure(e)
            }
        }

    /**
     * List files in a OneDrive folder as (name, itemId) pairs. Returns only files (not sub-folders).
     */
    suspend fun listFilesInFolderWithIds(folderId: String): Result<List<Pair<String, String>>> =
        withContext(Dispatchers.IO) {
            try {
                val accessToken = authManager.getAccessToken()
                    ?: return@withContext Result.failure(Exception("Not authenticated"))
                val items = fetchItems("$GRAPH_API_BASE/me/drive/items/$folderId/children", accessToken)
                    ?: return@withContext Result.failure(IOException("Failed to list folder"))
                val pairs = items.filter { it.file != null }.map { it.name to it.id }
                Result.success(pairs)
            } catch (e: Exception) {
                Log.e(TAG, "Error listing files (with IDs) in folder: $folderId", e)
                Result.failure(e)
            }
        }

    /**
     * Create a new subfolder named [folderName] inside [parentFolderId].
     * Returns the new folder's id and name on success.
     */
    suspend fun createFolder(parentFolderId: String?, folderName: String): Result<OneDriveFolder> =
        withContext(Dispatchers.IO) {
            try {
                val accessToken = authManager.getAccessToken()
                    ?: return@withContext Result.failure(Exception("Not authenticated"))

                val url = if (parentFolderId == null) {
                    "$GRAPH_API_BASE/me/drive/root/children"
                } else {
                    "$GRAPH_API_BASE/me/drive/items/$parentFolderId/children"
                }

                val json = JSONObject().apply {
                    put("name", folderName)
                    put("folder", JSONObject())
                    put("@microsoft.graph.conflictBehavior", "rename")
                }.toString()

                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IOException("Create folder failed: ${response.code} ${response.message}")
                        )
                    }
                    val responseJson = JSONObject(response.body?.string() ?: "{}")
                    val newFolder = OneDriveFolder(
                        id = responseJson.optString("id"),
                        name = responseJson.optString("name"),
                        childCount = 0
                    )
                    Result.success(newFolder)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating folder: $folderName", e)
                Result.failure(e)
            }
        }

    /** Fetch the direct children of a folder. Blocking — call from Dispatchers.IO only.
     *  Supports compound "driveId:itemId" IDs for remote/shared folders. */
    private fun listChildren(folderId: String, accessToken: String): List<DriveItem>? {
        val url = if (folderId.contains(':')) {
            val (driveId, itemId) = folderId.split(':', limit = 2)
            "$GRAPH_API_BASE/drives/$driveId/items/$itemId/children"
        } else {
            "$GRAPH_API_BASE/me/drive/items/$folderId/children"
        }
        return fetchItems(url, accessToken)
    }

    /** Fetch drive items from [url]. Blocking — call from Dispatchers.IO only. */
    private fun fetchItems(url: String, accessToken: String): List<DriveItem>? {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "fetchItems failed: ${response.code} $url")
                return null
            }
            val body = response.body?.string() ?: return null
            return gson.fromJson(body, DriveItemsResponse::class.java).value
        }
    }

    /**
     * Fetch item metadata (download URL + eTag) for a OneDrive file.
     * Used by the cache manager for URL caching and eTag-based file content validation.
     */
    suspend fun getItemMetadata(fileId: String): Result<ItemMetadata> = withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            val url = if (fileId.contains(':')) {
                val (driveId, itemId) = fileId.split(':', limit = 2)
                "$GRAPH_API_BASE/drives/$driveId/items/$itemId"
            } else {
                "$GRAPH_API_BASE/me/drive/items/$fileId"
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Get item failed: ${response.code} ${response.message}")
                    )
                }
                val body = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response"))
                val item = gson.fromJson(body, DriveItem::class.java)
                val downloadUrl = item.downloadUrl
                    ?: return@withContext Result.failure(IOException("No @microsoft.graph.downloadUrl in response"))
                Result.success(ItemMetadata(downloadUrl, item.eTag))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting item metadata for $fileId", e)
            Result.failure(e)
        }
    }

    /**
     * Get a temporary download URL for a OneDrive file.
     * Uses the `@microsoft.graph.downloadUrl` property from item metadata — avoids
     * redirect-following issues with HEAD requests and works with OkHttp defaults.
     */
    suspend fun getDownloadUrl(fileId: String): Result<String> =
        getItemMetadata(fileId).map { it.downloadUrl }

    /**
     * Download the binary content of a OneDrive file by [itemId].
     * Used by the cache layer to store files on disk for offline playback.
     */
    suspend fun downloadFileBytes(itemId: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            val url = if (itemId.contains(':')) {
                val (driveId, id) = itemId.split(':', limit = 2)
                "$GRAPH_API_BASE/drives/$driveId/items/$id/content"
            } else {
                "$GRAPH_API_BASE/me/drive/items/$itemId/content"
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Download failed: ${response.code} ${response.message}")
                    )
                }
                val bytes = response.body?.bytes()
                    ?: return@withContext Result.failure(IOException("Empty response body"))
                Result.success(bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading bytes for $itemId", e)
            Result.failure(e)
        }
    }
}

/**
 * Metadata returned by [OneDriveService.getItemMetadata].
 * Contains both the temporary download URL and the eTag for cache validation.
 */
data class ItemMetadata(
    val downloadUrl: String,
    val eTag: String?
)

/**
 * Represents a file in OneDrive
 */
data class OneDriveFile(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String?,
    val downloadUrl: String? = null,
    val webUrl: String,
    /** Parent folder path from Graph API, e.g. "/drive/root:/Music/The Beatles/Abbey Road" */
    val parentPath: String? = null,
    /** ETag for cache validation; changes when file content changes. */
    val eTag: String? = null
)

/**
 * Represents a folder in OneDrive
 */
data class OneDriveFolder(
    val id: String,
    val name: String,
    val childCount: Int
)

/**
 * Contents of a OneDrive folder
 */
data class OneDriveFolderContents(
    val folders: List<OneDriveFolder>,
    val audioFiles: List<OneDriveFile>
)

// Microsoft Graph API response models
private data class DriveItemsResponse(
    val value: List<DriveItem>
)

private data class DriveItem(
    val id: String,
    val name: String,
    val size: Long?,
    @SerializedName("@microsoft.graph.downloadUrl")
    val downloadUrl: String?,
    val webUrl: String,
    val eTag: String?,
    val file: FileProperties?,   // Present if it's a file (not folder)
    val folder: FolderProperties?, // Present if it's a folder (not file)
    val parentReference: ParentReference?,
    val remoteItem: RemoteItemProperties? // Present for shared/remote folders integrated into this drive
)

private data class ParentReference(
    val path: String? // e.g. "/drive/root:/Music/The Beatles/Abbey Road"
)

private data class FileProperties(
    val mimeType: String?
)

private data class FolderProperties(
    val childCount: Int
)

/** Facet present on items that live in another drive (e.g. shared folders added via "Add shortcut"). */
private data class RemoteItemProperties(
    val id: String,
    val folder: FolderProperties?,
    val file: FileProperties?,
    val parentReference: RemoteParentReference?
)

private data class RemoteParentReference(
    val driveId: String?
)

private fun DriveItem.toOneDriveFile() = OneDriveFile(
    id = id,
    name = name,
    size = size ?: 0,
    mimeType = file?.mimeType,
    downloadUrl = downloadUrl,
    webUrl = webUrl,
    parentPath = parentReference?.path,
    eTag = eTag
)

private fun DriveItem.toOneDriveFolder() = OneDriveFolder(
    id = id,
    name = name,
    childCount = folder?.childCount ?: 0
)

/**
 * Converts a remote/shared DriveItem (one that has a [remoteItem] facet) to an [OneDriveFolder].
 * The folder ID is encoded as "driveId:itemId" so the browse/listing code can use the correct
 * `/drives/{driveId}/items/{itemId}/children` endpoint when the user navigates into it.
 */
private fun DriveItem.toRemoteOneDriveFolder(): OneDriveFolder {
    val remote = remoteItem!!
    val remoteDriveId = remote.parentReference?.driveId
    val compoundId = if (remoteDriveId != null) "$remoteDriveId:${remote.id}" else remote.id
    return OneDriveFolder(
        id = compoundId,
        name = name,
        childCount = remote.folder?.childCount ?: 0
    )
}
