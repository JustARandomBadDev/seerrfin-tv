package io.github.justarandombaddev.seerrfintv.domain.session

import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession

interface JellyfinSessionStore {
    suspend fun save(session: JellyfinSession)

    suspend fun load(): JellyfinSession?

    suspend fun clear()
}
