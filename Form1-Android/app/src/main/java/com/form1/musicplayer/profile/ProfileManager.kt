package com.form1.musicplayer.profile

import android.content.Context
import android.util.Log
import com.form1.musicplayer.onedrive.OneDriveCacheManager
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Manages reading and writing of profile files (e.g. .f2pl playlists).
 *
 * Files can reside either in the app's local files directory or in a designated OneDrive folder.
 * The active location is stored in [ProfileConfig].
 *
 * All operations are suspend functions — safe to call from a coroutine on any dispatcher.
 */
class ProfileManager(
    private val context: Context,
    private val profileConfig: ProfileConfig,
    private val cache: OneDriveCacheManager
) {

    companion object {
        private const val TAG = "ProfileManager"
        private const val LOCAL_PROFILE_DIR = "profile"
    }

    // ── Local helpers ──────────────────────────────────────────────────────────

    private val localDir: File
        get() = File(context.filesDir, LOCAL_PROFILE_DIR).also { it.mkdirs() }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Invalidate the cached file listing for the current profile folder.
     * Call this before [listFiles] when a fresh network fetch is desired (e.g. on manual retry).
     */
    suspend fun invalidateCache() {
        val storageType = profileConfig.storageType.first()
        if (storageType == StorageType.ONEDRIVE) {
            val folderId = profileConfig.oneDriveFolderId.first() ?: return
            cache.invalidateFileList(folderId)
        }
    }

    /**
     * List profile files with the given [extension] (e.g. "f2pl").
     * Returns file names only (not full paths).
     */
    suspend fun listFiles(extension: String): Result<List<String>> {
        val storageType = profileConfig.storageType.first()
        return when (storageType) {
            StorageType.LOCAL -> listLocalFiles(extension)
            StorageType.ONEDRIVE -> {
                val folderId = profileConfig.oneDriveFolderId.first()
                    ?: return Result.failure(Exception("No OneDrive profile folder configured"))
                listOneDriveFiles(folderId, extension)
            }
        }
    }

    /**
     * Read a profile file by name. Returns the text content.
     */
    suspend fun readFile(name: String): Result<String> {
        val storageType = profileConfig.storageType.first()
        return when (storageType) {
            StorageType.LOCAL -> readLocalFile(name)
            StorageType.ONEDRIVE -> {
                val folderId = profileConfig.oneDriveFolderId.first()
                    ?: return Result.failure(Exception("No OneDrive profile folder configured"))
                readOneDriveFile(folderId, name)
            }
        }
    }

    /**
     * Write [content] to a profile file with the given [name].
     * Creates the file if it does not exist; overwrites if it does.
     */
    suspend fun writeFile(name: String, content: String): Result<Unit> {
        val storageType = profileConfig.storageType.first()
        return when (storageType) {
            StorageType.LOCAL -> writeLocalFile(name, content)
            StorageType.ONEDRIVE -> {
                val folderId = profileConfig.oneDriveFolderId.first()
                    ?: return Result.failure(Exception("No OneDrive profile folder configured"))
                writeOneDriveFile(folderId, name, content)
            }
        }
    }

    /**
     * Delete a profile file by name.
     */
    suspend fun deleteFile(name: String): Result<Unit> {
        val storageType = profileConfig.storageType.first()
        return when (storageType) {
            StorageType.LOCAL -> deleteLocalFile(name)
            StorageType.ONEDRIVE -> {
                val folderId = profileConfig.oneDriveFolderId.first()
                    ?: return Result.failure(Exception("No OneDrive profile folder configured"))
                deleteOneDriveFile(folderId, name)
            }
        }
    }

    // ── Location switching with migration ──────────────────────────────────────

    /**
     * Switch the profile storage to local device storage.
     * Copies any existing OneDrive profile files to the local directory.
     * Old OneDrive files are NOT deleted (per spec).
     */
    suspend fun switchToLocal(): Result<Unit> {
        val current = profileConfig.storageType.first()
        if (current == StorageType.LOCAL) return Result.success(Unit)

        val folderId = profileConfig.oneDriveFolderId.first()
        if (folderId != null) {
            migrateOneDriveToLocal(folderId)
        }

        profileConfig.setLocal()
        Log.i(TAG, "Switched profile storage to LOCAL")
        return Result.success(Unit)
    }

    /**
     * Switch the profile storage to the given OneDrive folder.
     * Copies any existing local profile files to OneDrive.
     * Old local files are NOT deleted (per spec).
     */
    suspend fun switchToOneDrive(folderId: String, folderPath: String): Result<Unit> {
        val current = profileConfig.storageType.first()
        if (current == StorageType.ONEDRIVE &&
            profileConfig.oneDriveFolderId.first() == folderId
        ) return Result.success(Unit)

        val migrationResult = migrateLocalToOneDrive(folderId)
        if (migrationResult.isFailure) return migrationResult

        profileConfig.setOneDrive(folderId, folderPath)
        Log.i(TAG, "Switched profile storage to OneDrive folder: $folderPath")
        return Result.success(Unit)
    }

    // ── Local implementation ───────────────────────────────────────────────────

    private fun listLocalFiles(extension: String): Result<List<String>> {
        val files = localDir.listFiles { f -> f.extension == extension }?.map { it.name }
            ?: emptyList()
        return Result.success(files)
    }

    private fun readLocalFile(name: String): Result<String> {
        return try {
            val file = File(localDir, name)
            if (!file.exists()) return Result.failure(Exception("File not found: $name"))
            Result.success(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error reading local file: $name", e)
            Result.failure(e)
        }
    }

    private fun writeLocalFile(name: String, content: String): Result<Unit> {
        return try {
            File(localDir, name).writeText(content)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing local file: $name", e)
            Result.failure(e)
        }
    }

    private fun deleteLocalFile(name: String): Result<Unit> {
        return try {
            val file = File(localDir, name)
            if (file.exists()) file.delete()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting local file: $name", e)
            Result.failure(e)
        }
    }

    // ── OneDrive implementation ────────────────────────────────────────────────

    private suspend fun listOneDriveFiles(folderId: String, extension: String): Result<List<String>> {
        return cache.listFilesInFolderWithIds(folderId)
            .map { files -> files.map { it.first }.filter { it.endsWith(".$extension") } }
    }

    private suspend fun readOneDriveFile(folderId: String, name: String): Result<String> {
        val itemId = findOneDriveItemId(folderId, name)
            ?: return Result.failure(Exception("File not found on OneDrive: $name"))
        // Playlist files are immutable (identified by timestamp), so cache indefinitely
        return cache.getTextFileContent(itemId)
    }

    private suspend fun writeOneDriveFile(folderId: String, name: String, content: String): Result<Unit> {
        return cache.service.uploadTextFile(folderId, name, content).also { result ->
            if (result.isSuccess) cache.invalidateFileList(folderId)
        }
    }

    private suspend fun deleteOneDriveFile(folderId: String, name: String): Result<Unit> {
        val itemId = findOneDriveItemId(folderId, name)
            ?: return Result.success(Unit) // already gone
        return cache.service.deleteFile(itemId).also { result ->
            if (result.isSuccess) cache.invalidateFileList(folderId)
        }
    }

    private suspend fun findOneDriveItemId(folderId: String, name: String): String? {
        return cache.listFilesInFolderWithIds(folderId)
            .getOrNull()?.firstOrNull { it.first == name }?.second
    }

    // ── Migration helpers ──────────────────────────────────────────────────────

    private suspend fun migrateLocalToOneDrive(folderId: String): Result<Unit> {
        val localFiles = localDir.listFiles() ?: return Result.success(Unit)
        for (file in localFiles) {
            val content = try { file.readText() } catch (e: Exception) {
                Log.w(TAG, "Could not read local file for migration: ${file.name}", e)
                continue
            }
            val uploadResult = cache.service.uploadTextFile(folderId, file.name, content)
            if (uploadResult.isFailure) {
                Log.w(TAG, "Failed to upload ${file.name} during migration", uploadResult.exceptionOrNull())
            }
        }
        cache.invalidateFileList(folderId)
        return Result.success(Unit)
    }

    private suspend fun migrateOneDriveToLocal(folderId: String) {
        val files = cache.listFilesInFolderWithIds(folderId).getOrNull() ?: return
        for ((name, itemId) in files) {
            val contentResult = cache.service.downloadFileContent(itemId)
            if (contentResult.isFailure) {
                Log.w(TAG, "Failed to download $name during migration", contentResult.exceptionOrNull())
                continue
            }
            try {
                File(localDir, name).writeText(contentResult.getOrThrow())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write $name locally during migration", e)
            }
        }
    }
}
