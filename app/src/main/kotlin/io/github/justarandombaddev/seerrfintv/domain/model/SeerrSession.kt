package io.github.justarandombaddev.seerrfintv.domain.model

data class SeerrSession(
    val serverUrl: String,
    val cookieName: String,
    val cookieValue: String,
    val expiresAtEpochMillis: Long,
    val secure: Boolean = false,
) {
    fun isExpired(nowEpochMillis: Long): Boolean = expiresAtEpochMillis <= nowEpochMillis

    override fun toString(): String =
        "SeerrSession(serverUrl=<redacted>, cookie=<redacted>, expiresAtEpochMillis=$expiresAtEpochMillis, secure=$secure)"
}

data class SeerrUser(
    val id: Long,
    val displayName: String,
    val permissions: SeerrPermissions,
) {
    override fun toString(): String =
        "SeerrUser(identity=<redacted>, permissions=${permissions.value})"
}

data class SeerrPermissions(
    val value: Long,
) {
    val canRequestMovies: Boolean
        get() = hasAny(REQUEST, REQUEST_MOVIE)

    val canRequestSeries: Boolean
        get() = hasAny(REQUEST, REQUEST_TV)

    val canRequest4kMovies: Boolean
        get() = hasAny(REQUEST_4K, REQUEST_4K_MOVIE)

    val canRequest4kSeries: Boolean
        get() = hasAny(REQUEST_4K, REQUEST_4K_TV)

    private fun hasAny(vararg permissions: Long): Boolean =
        value and ADMIN != 0L || permissions.any { permission -> value and permission != 0L }

    private companion object {
        const val ADMIN = 2L
        const val REQUEST = 32L
        const val REQUEST_4K = 1_024L
        const val REQUEST_4K_MOVIE = 2_048L
        const val REQUEST_4K_TV = 4_096L
        const val REQUEST_MOVIE = 262_144L
        const val REQUEST_TV = 524_288L
    }
}
