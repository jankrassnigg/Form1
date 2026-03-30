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

            // Build URL for folder contents
            val url = if (folderId == null) {
                "$GRAPH_API_BASE/me/drive/root/children"
            } else {
                "$GRAPH_API_BASE/me/drive/items/$folderId/children"
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
                            // It's a folder
                            folders.add(item.toOneDriveFolder())
                        }
                        item.file != null -> {
                            // It's a file - check if it's an audio file
                            val extension = item.name.substringAfterLast('.', "").lowercase()
                            if (extension in AUDIO_EXTENSIONS) {
                                audioFiles.add(item.toOneDriveFile())
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
            val phase1Url = if (rootFolderId.isNullOrEmpty()) {
                "$GRAPH_API_BASE/me/drive/root/search(q='$escaped')"
            } else {
                "$GRAPH_API_BASE/me/drive/items/$rootFolderId/search(q='$escaped')"
            }

            val phase1Items = fetchItems(phase1Url, accessToken)
                ?: return@withContext Result.failure(IOException("Search failed"))

            // Direct audio file hits
            phase1Items
                .filter { it.file != null && it.name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS }
                .forEach { if (seen.add(it.id)) candidates.add(it.toOneDriveFile()) }

            // Phase 2: expand matching folders via children endpoint (correctly scoped)
            val folderHits = phase1Items.filter { it.folder != null }.take(5)
            for (artistFolder in folderHits) {
                val level1 = listChildren(artistFolder.id, accessToken) ?: continue

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

    /** Fetch the direct children of a folder. Blocking — call from Dispatchers.IO only. */
    private fun listChildren(folderId: String, accessToken: String): List<DriveItem>? =
        fetchItems("$GRAPH_API_BASE/me/drive/items/$folderId/children", accessToken)

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
     * Get download URL for a file
     */
    suspend fun getDownloadUrl(fileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getAccessToken()
            if (accessToken == null) {
                return@withContext Result.failure(Exception("Not authenticated"))
            }

            val url = "$GRAPH_API_BASE/me/drive/items/$fileId/content"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .head() // HEAD request to get redirect URL without downloading
                .build()

            client.newCall(request).execute().use { response ->
                // Microsoft Graph returns a 302 redirect to the actual download URL
                val downloadUrl = response.header("Location")
                if (downloadUrl != null) {
                    Result.success(downloadUrl)
                } else {
                    Result.failure(IOException("No download URL found"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting download URL", e)
            Result.failure(e)
        }
    }
}

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
    val parentPath: String? = null
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
    val file: FileProperties?,   // Present if it's a file (not folder)
    val folder: FolderProperties?, // Present if it's a folder (not file)
    val parentReference: ParentReference?
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

private fun DriveItem.toOneDriveFile() = OneDriveFile(
    id = id,
    name = name,
    size = size ?: 0,
    mimeType = file?.mimeType,
    downloadUrl = downloadUrl,
    webUrl = webUrl,
    parentPath = parentReference?.path
)

private fun DriveItem.toOneDriveFolder() = OneDriveFolder(
    id = id,
    name = name,
    childCount = folder?.childCount ?: 0
)
