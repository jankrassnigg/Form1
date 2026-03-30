package com.form1.musicplayer.music

/**
 * Abstraction for a music source (OneDrive, local storage, etc.)
 */
interface MusicSource {
    val name: String
    suspend fun listFolder(folderId: String?): Result<FolderContents>
    suspend fun search(query: String, rootFolderId: String?): Result<List<AudioFileRef>>
    suspend fun getPlaybackUrl(fileId: String): Result<String>
}

data class FolderContents(
    val folders: List<FolderItem>,
    val audioFiles: List<AudioFileRef>
)

data class FolderItem(
    val id: String,
    val name: String,
    val childCount: Int
)

data class AudioFileRef(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String?
)
