package io.github.justarandombaddev.seerrfintv.domain.session

import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession

interface JellyfinSessionStore {
    suspend fun save(session: JellyfinSession)

    suspend fun restore(): JellyfinSessionRestoreResult

    suspend fun clear()
}

sealed interface JellyfinSessionRestoreResult {
    data class Restored(val session: JellyfinSession) : JellyfinSessionRestoreResult

    data object Missing : JellyfinSessionRestoreResult

    data object Unreadable : JellyfinSessionRestoreResult
}
