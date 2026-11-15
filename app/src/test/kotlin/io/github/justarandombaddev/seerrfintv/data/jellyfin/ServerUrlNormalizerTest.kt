package io.github.justarandombaddev.seerrfintv.data.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlNormalizerTest {
    private val normalizer = ServerUrlNormalizer()

    @Test
    fun `blank address is rejected`() {
        assertEquals(
            ServerUrlNormalization.Invalid(ServerUrlError.Empty),
            normalizer.normalize("   "),
        )
    }

    @Test
    fun `address without scheme gets http and trailing slash`() {
        assertEquals(
            ServerUrlNormalization.Valid("http://media.example.test/"),
            normalizer.normalize(" media.example.test "),
        )
    }

    @Test
    fun `https port and reverse proxy path are preserved`() {
        assertEquals(
            ServerUrlNormalization.Valid("https://media.example.test:8443/jellyfin/"),
            normalizer.normalize("https://media.example.test:8443/jellyfin"),
        )
    }

    @Test
    fun `encoded reverse proxy path is preserved without double encoding`() {
        val normalized = normalizer.normalize("https://media.example.test/media%20server%2Fproxy%25")

        assertEquals(
            ServerUrlNormalization.Valid("https://media.example.test/media%20server%2Fproxy%25/"),
            normalized,
        )
        assertEquals(
            normalized,
            normalizer.normalize((normalized as ServerUrlNormalization.Valid).url),
        )
    }

    @Test
    fun `valid port boundaries are accepted`() {
        assertEquals(
            ServerUrlNormalization.Valid("http://media.example.test:1/"),
            normalizer.normalize("media.example.test:1"),
        )
        assertEquals(
            ServerUrlNormalization.Valid("http://media.example.test:65535/"),
            normalizer.normalize("media.example.test:65535"),
        )
    }

    @Test
    fun `invalid ports are rejected`() {
        listOf("media.example.test:0", "media.example.test:65536", "media.example.test:not-a-port")
            .forEach { address ->
                assertEquals(
                    ServerUrlNormalization.Invalid(ServerUrlError.InvalidAddress),
                    normalizer.normalize(address),
                )
            }
    }

    @Test
    fun `existing trailing slash is not duplicated`() {
        assertEquals(
            ServerUrlNormalization.Valid("http://media.example.test/jellyfin/"),
            normalizer.normalize("http://media.example.test/jellyfin/"),
        )
    }

    @Test
    fun `unsupported scheme is rejected`() {
        assertEquals(
            ServerUrlNormalization.Invalid(ServerUrlError.UnsupportedScheme),
            normalizer.normalize("ftp://media.example.test"),
        )
    }

    @Test
    fun `address without host is rejected`() {
        assertEquals(
            ServerUrlNormalization.Invalid(ServerUrlError.InvalidAddress),
            normalizer.normalize("http:///jellyfin"),
        )
    }

    @Test
    fun `embedded credentials are rejected`() {
        assertEquals(
            ServerUrlNormalization.Invalid(ServerUrlError.CredentialsNotAllowed),
            normalizer.normalize("https://user:password@media.example.test"),
        )
    }

    @Test
    fun `query and fragment are rejected`() {
        assertEquals(
            ServerUrlNormalization.Invalid(ServerUrlError.QueryOrFragmentNotAllowed),
            normalizer.normalize("https://media.example.test/jellyfin?token=value#section"),
        )
    }
}
