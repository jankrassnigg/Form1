package com.form1.musicplayer.music

import com.form1.musicplayer.onedrive.OneDriveService

class OneDriveMusicSource(private val service: OneDriveService) : MusicSource {

    override val name = "OneDrive"

    override suspend fun listFolder(folderId: String?): Result<FolderContents> {
        return service.listFolderContents(folderId).map { contents ->
            FolderContents(
                folders = contents.folders.map { FolderItem(it.id, it.name, it.childCount) },
                audioFiles = contents.audioFiles.map { AudioFileRef(it.id, it.name, it.size, it.mimeType) }
            )
        }
    }

    override suspend fun search(query: String, rootFolderId: String?): Result<List<AudioFileRef>> {
        // Implemented in Step 2
        return Result.success(emptyList())
    }

    override suspend fun getPlaybackUrl(fileId: String): Result<String> {
        return service.getDownloadUrl(fileId)
    }
}
