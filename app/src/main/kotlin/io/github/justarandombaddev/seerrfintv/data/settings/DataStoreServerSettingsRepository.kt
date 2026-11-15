package io.github.justarandombaddev.seerrfintv.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.justarandombaddev.seerrfintv.domain.repository.ServerSettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

val Context.serverSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "server_settings",
)

class DataStoreServerSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : ServerSettingsRepository {
    override val serverUrl: Flow<String?> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }.map { preferences -> preferences[SERVER_URL] }

    override suspend fun saveServerUrl(serverUrl: String) {
        dataStore.edit { preferences -> preferences[SERVER_URL] = serverUrl }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("jellyfin_server_url")
    }
}
