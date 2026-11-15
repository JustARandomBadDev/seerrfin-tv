package io.github.justarandombaddev.seerrfintv.domain.model

data class JellyfinSession(
    val accessToken: String,
    val userId: String,
    val serverId: String,
) {
    override fun toString(): String = "JellyfinSession(<redacted>)"
}
