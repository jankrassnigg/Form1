package com.form1.musicplayer.onedrive

import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

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
            "mp3", "m4a", "flac", "wav", "ogg",
            "aac", "wma", "opus", "ape", "alac"
        )
    }

    /**
     * List all audio files in OneDrive (search globally)
     */
    suspend fun listAudioFiles(): Result<List<OneDriveFile>> = withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getAccessToken()
            if (accessToken == null) {
                return@withContext Result.failure(Exception("Not authenticated"))
            }

            // Search for audio files in OneDrive
            val url = "$GRAPH_API_BASE/me/drive/root/search(q='.mp3 OR .m4a OR .flac')"

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
                val audioFiles = apiResponse.value
                    .filter { it.file != null } // Only files, not folders
                    .filter { item ->
                        // Filter by audio extensions
                        val extension = item.name.substringAfterLast('.', "").lowercase()
                        extension in AUDIO_EXTENSIONS
                    }
                    .map { it.toOneDriveFile() }

                Log.d(TAG, "Found ${audioFiles.size} audio files")
                Result.success(audioFiles)
            }
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
    val webUrl: String
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
    val file: FileProperties?, // Present if it's a file (not folder)
    val folder: FolderProperties? // Present if it's a folder (not file)
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
    webUrl = webUrl
)

private fun DriveItem.toOneDriveFolder() = OneDriveFolder(
    id = id,
    name = name,
    childCount = folder?.childCount ?: 0
)
