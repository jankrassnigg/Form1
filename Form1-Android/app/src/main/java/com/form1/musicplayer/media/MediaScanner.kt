package com.form1.musicplayer.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

/**
 * Scans device storage for audio files using MediaStore API
 */
class MediaScanner(private val context: Context) {

    /**
     * Scan all audio files from device storage
     * Returns list of AudioFile objects with metadata
     */
    fun scanAudioFiles(): List<AudioFile> {
        val audioFiles = mutableListOf<AudioFile>()

        // Define columns we want to retrieve
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATA // File path
        )

        // Query only music files (exclude ringtones, notifications, etc.)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        // Sort by title by default
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn) ?: "Unknown"
                    val title = cursor.getString(titleColumn) ?: displayName
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)
                    val filePath = cursor.getString(dataColumn) ?: ""

                    // Build content URI for this audio file
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    audioFiles.add(
                        AudioFile(
                            id = id,
                            uri = contentUri,
                            displayName = displayName,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            size = size,
                            dateModified = dateModified,
                            filePath = filePath
                        )
                    )
                }
            }

            Log.d("MediaScanner", "Found ${audioFiles.size} audio files")
        } catch (e: Exception) {
            Log.e("MediaScanner", "Error scanning audio files", e)
        }

        return audioFiles
    }
}

/**
 * Represents an audio file with metadata
 */
data class AudioFile(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,  // milliseconds
    val size: Long,      // bytes
    val dateModified: Long,  // unix timestamp
    val filePath: String
)
