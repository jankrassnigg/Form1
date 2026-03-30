package com.form1.musicplayer.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "profile_config")

enum class StorageType { LOCAL, ONEDRIVE }

class ProfileConfig(private val context: Context) {

    companion object {
        private val KEY_STORAGE_TYPE = stringPreferencesKey("storage_type")
        private val KEY_ONEDRIVE_FOLDER_ID = stringPreferencesKey("onedrive_folder_id")
        private val KEY_ONEDRIVE_FOLDER_PATH = stringPreferencesKey("onedrive_folder_path")
    }

    val storageType: Flow<StorageType> = context.profileDataStore.data.map { prefs ->
        if (prefs[KEY_STORAGE_TYPE] == StorageType.ONEDRIVE.name) StorageType.ONEDRIVE
        else StorageType.LOCAL
    }

    val oneDriveFolderId: Flow<String?> = context.profileDataStore.data
        .map { it[KEY_ONEDRIVE_FOLDER_ID] }

    val oneDriveFolderPath: Flow<String?> = context.profileDataStore.data
        .map { it[KEY_ONEDRIVE_FOLDER_PATH] }

    suspend fun setLocal() {
        context.profileDataStore.edit { prefs ->
            prefs[KEY_STORAGE_TYPE] = StorageType.LOCAL.name
            prefs.remove(KEY_ONEDRIVE_FOLDER_ID)
            prefs.remove(KEY_ONEDRIVE_FOLDER_PATH)
        }
    }

    suspend fun setOneDrive(folderId: String, folderPath: String) {
        context.profileDataStore.edit { prefs ->
            prefs[KEY_STORAGE_TYPE] = StorageType.ONEDRIVE.name
            prefs[KEY_ONEDRIVE_FOLDER_ID] = folderId
            prefs[KEY_ONEDRIVE_FOLDER_PATH] = folderPath
        }
    }
}
