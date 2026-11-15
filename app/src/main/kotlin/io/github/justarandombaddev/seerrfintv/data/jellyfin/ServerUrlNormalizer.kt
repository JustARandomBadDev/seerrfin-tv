package io.github.justarandombaddev.seerrfintv.data.jellyfin

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

enum class ServerUrlError {
    Empty,
    InvalidAddress,
    UnsupportedScheme,
    CredentialsNotAllowed,
    QueryOrFragmentNotAllowed,
}

sealed interface ServerUrlNormalization {
    data class Valid(val url: String) : ServerUrlNormalization

    data class Invalid(val reason: ServerUrlError) : ServerUrlNormalization
}

class ServerUrlNormalizer {
    fun normalize(input: String): ServerUrlNormalization {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ServerUrlNormalization.Invalid(ServerUrlError.Empty)
        }

        val candidate =
            if (SCHEME_PREFIX.containsMatchIn(trimmed)) {
                trimmed
            } else {
                "http://$trimmed"
            }

        val uri =
            try {
                URI(candidate)
            } catch (_: URISyntaxException) {
                return ServerUrlNormalization.Invalid(ServerUrlError.InvalidAddress)
            }

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            return ServerUrlNormalization.Invalid(ServerUrlError.UnsupportedScheme)
        }
        if (uri.rawUserInfo != null) {
            return ServerUrlNormalization.Invalid(ServerUrlError.CredentialsNotAllowed)
        }
        if (uri.rawQuery != null || uri.rawFragment != null) {
            return ServerUrlNormalization.Invalid(ServerUrlError.QueryOrFragmentNotAllowed)
        }

        val host = uri.host?.lowercase(Locale.ROOT)
        if (host.isNullOrBlank()) {
            return ServerUrlNormalization.Invalid(ServerUrlError.InvalidAddress)
        }
        if (uri.port == 0 || uri.port > MAX_PORT) {
            return ServerUrlNormalization.Invalid(ServerUrlError.InvalidAddress)
        }

        val path = uri.rawPath.orEmpty()
        val normalizedPath =
            when {
                path.isEmpty() -> "/"
                path.endsWith('/') -> path
                else -> "$path/"
            }

        val port = if (uri.port == -1) "" else ":${uri.port}"
        val normalized = URI("$scheme://$host$port$normalizedPath")
        return ServerUrlNormalization.Valid(normalized.toASCIIString())
    }

    private companion object {
        const val MAX_PORT = 65_535
        val SCHEME_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    }
}
