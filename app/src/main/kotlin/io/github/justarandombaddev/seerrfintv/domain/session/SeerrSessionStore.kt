package io.github.justarandombaddev.seerrfintv.domain.session

import io.github.justarandombaddev.seerrfintv.domain.model.SeerrSession

sealed interface SeerrSessionRestoreResult {
    data object Missing : SeerrSessionRestoreResult

    data object Unreadable : SeerrSessionRestoreResult

    data class Restored(val session: SeerrSession) : SeerrSessionRestoreResult
}

interface SeerrSessionStore {
    suspend fun save(session: SeerrSession)

    suspend fun restore(): SeerrSessionRestoreResult

    suspend fun clear()
}
