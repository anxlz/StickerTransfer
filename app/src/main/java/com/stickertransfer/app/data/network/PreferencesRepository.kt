package com.stickertransfer.app.data.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesRepository(private val context: Context) {

    companion object {
        val BOT_TOKEN_KEY = stringPreferencesKey("telegram_bot_token")
        val ZIP_PATH_KEY = stringPreferencesKey("zip_download_path")
    }

    val botTokenFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[BOT_TOKEN_KEY] ?: ""
    }

    val zipPathFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ZIP_PATH_KEY] ?: "Downloads"
    }

    suspend fun saveBotToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[BOT_TOKEN_KEY] = token
        }
    }

    suspend fun saveZipPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[ZIP_PATH_KEY] = path
        }
    }
}
