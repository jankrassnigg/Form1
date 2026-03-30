package com.form1.musicplayer.music

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.musicSourceDataStore: DataStore<Preferences> by preferencesDataStore(name = "music_source_config")

class MusicSourceConfig(private val context: Context) {

    companion object {
        private val KEY_ONEDRIVE_FOLDER_ID = stringPreferencesKey("onedrive_music_folder_id")
        private val KEY_ONEDRIVE_FOLDER_PATH = stringPreferencesKey("onedrive_music_folder_path")
    }

    val oneDriveMusicFolderId: Flow<String?> = context.musicSourceDataStore.data
        .map { it[KEY_ONEDRIVE_FOLDER_ID] }

    val oneDriveMusicFolderPath: Flow<String?> = context.musicSourceDataStore.data
        .map { it[KEY_ONEDRIVE_FOLDER_PATH] }

    suspend fun setOneDriveMusicFolder(folderId: String, folderPath: String) {
        context.musicSourceDataStore.edit { prefs ->
            prefs[KEY_ONEDRIVE_FOLDER_ID] = folderId
            prefs[KEY_ONEDRIVE_FOLDER_PATH] = folderPath
        }
    }

    suspend fun clearOneDriveMusicFolder() {
        context.musicSourceDataStore.edit { prefs ->
            prefs.remove(KEY_ONEDRIVE_FOLDER_ID)
            prefs.remove(KEY_ONEDRIVE_FOLDER_PATH)
        }
    }
}
