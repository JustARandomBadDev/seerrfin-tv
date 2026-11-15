package io.github.justarandombaddev.seerrfintv.domain.repository

import kotlinx.coroutines.flow.Flow

interface ServerSettingsRepository {
    val serverUrl: Flow<String?>

    suspend fun saveServerUrl(serverUrl: String)
}
