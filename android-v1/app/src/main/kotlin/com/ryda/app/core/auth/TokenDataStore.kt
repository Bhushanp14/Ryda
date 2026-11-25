package com.ryda.app.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ryda_prefs")

class TokenDataStore(private val context: Context) {

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_PERMISSIONS_EXPLAINED = booleanPreferencesKey("permissions_explained")
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        /** When true, WorkManager sync workers must NOT upload rides to the server. */
        val KEY_OFFLINE_ONLY = booleanPreferencesKey("offline_only_mode")

        @Volatile private var instance: TokenDataStore? = null

        fun getInstance(context: Context): TokenDataStore =
            instance ?: synchronized(this) {
                instance ?: TokenDataStore(context.applicationContext).also { instance = it }
            }
    }

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_TOKEN] }
    val usernameFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USERNAME] }
    val permissionsExplainedFlow: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PERMISSIONS_EXPLAINED] ?: false }
    val onboardingDoneFlow: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ONBOARDING_DONE] ?: false }
    /** Emits true while the user has explicitly enabled offline-only mode. */
    val offlineOnlyFlow: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_OFFLINE_ONLY] ?: false }

    suspend fun saveToken(token: String, username: String) {
        context.dataStore.edit {
            it[KEY_TOKEN] = token
            it[KEY_USERNAME] = username
        }
    }

    suspend fun clearToken() {
        context.dataStore.edit {
            it.remove(KEY_TOKEN)
            it.remove(KEY_USERNAME)
        }
    }

    suspend fun setPermissionsExplained(value: Boolean = true) {
        context.dataStore.edit { it[KEY_PERMISSIONS_EXPLAINED] = value }
    }

    suspend fun setOnboardingDone(value: Boolean = true) {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = value }
    }

    suspend fun setOfflineOnly(value: Boolean) {
        context.dataStore.edit { it[KEY_OFFLINE_ONLY] = value }
    }

    suspend fun getToken(): String? = tokenFlow.first()
    suspend fun isLoggedIn(): Boolean = getToken() != null
    suspend fun hasSeenPermissionExplainer(): Boolean = permissionsExplainedFlow.first()
    suspend fun isOfflineOnly(): Boolean = offlineOnlyFlow.first()
}
