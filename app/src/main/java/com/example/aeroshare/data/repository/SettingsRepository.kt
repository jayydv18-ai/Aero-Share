package com.example.aeroshare.data.repository

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aeroshare_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        val KEY_AVATAR_ID = stringPreferencesKey("avatar_id")
        val KEY_THEME = stringPreferencesKey("theme_preference")
        val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch_completed")
        val KEY_PRO_PURCHASED = booleanPreferencesKey("pro_purchased")
        val KEY_STORAGE_FOLDER = stringPreferencesKey("storage_folder")
    }

    val defaultDeviceName: String
        get() = Build.MODEL ?: "Your Phone"

    val deviceNameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEVICE_NAME] ?: defaultDeviceName
    }

    val avatarIdFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_AVATAR_ID] ?: "avatar_1"
    }

    val themePreferenceFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_THEME] ?: "system"
    }

    val isFirstLaunchCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FIRST_LAUNCH] ?: false
    }

    val isProPurchasedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_PRO_PURCHASED] ?: false
    }

    suspend fun saveProfile(name: String, avatarId: String) {
        val cleanName = name.trim().ifEmpty { defaultDeviceName }
        context.dataStore.edit { preferences ->
            preferences[KEY_DEVICE_NAME] = cleanName
            preferences[KEY_AVATAR_ID] = avatarId
            preferences[KEY_FIRST_LAUNCH] = true
        }
    }

    suspend fun setThemePreference(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME] = theme
        }
    }

    suspend fun setProPurchased(purchased: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PRO_PURCHASED] = purchased
        }
    }
}
