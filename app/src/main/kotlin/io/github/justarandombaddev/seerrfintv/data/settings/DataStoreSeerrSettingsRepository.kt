package io.github.justarandombaddev.seerrfintv.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrSettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreSeerrSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SeerrSettingsRepository {
    override val serverUrl: Flow<String?> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }.map { preferences -> preferences[SEERR_SERVER_URL] }

    override suspend fun saveServerUrl(serverUrl: String) {
        dataStore.edit { preferences -> preferences[SEERR_SERVER_URL] = serverUrl }
    }

    private companion object {
        val SEERR_SERVER_URL = stringPreferencesKey("seerr_server_url")
    }
}
