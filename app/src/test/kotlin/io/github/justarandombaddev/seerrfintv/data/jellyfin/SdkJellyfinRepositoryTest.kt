package io.github.justarandombaddev.seerrfintv.data.jellyfin

import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinConnectionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinLibrariesResult
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinLibrary
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession
import java.util.concurrent.CancellationException
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SdkJellyfinRepositoryTest {
    @Test
    fun `valid Jellyfin identity is accepted`() =
        runTest {
            val repository =
                repositoryWithProbe {
                    PublicSystemInfo(
                        version = "12.4.0",
                        productName = "Jellyfin Server",
                    )
                }

            assertEquals(
                JellyfinConnectionResult.Connected("https://media.example.test/"),
                repository.connect("https://media.example.test/"),
            )
        }

    @Test
    fun `empty public system information is rejected`() =
        runTest {
            val repository = repositoryWithProbe { PublicSystemInfo() }

            assertEquals(
                JellyfinConnectionResult.InvalidResponse,
                repository.connect("https://not-jellyfin.example.test/"),
            )
        }

    @Test
    fun `valid authentication response becomes app session`() =
        runTest {
            val repository =
                repositoryWithAuthenticator { serverUrl, username, password ->
                    assertEquals("https://media.example.test/", serverUrl)
                    assertEquals("alice", username)
                    assertEquals("", password)
                    SdkAuthenticationSession(
                        accessToken = "test-access-token",
                        userId = "00000000-0000-0000-0000-000000000001",
                        serverId = "test-server-id",
                        displayName = "Alice",
                    )
                }

            val result =
                repository.authenticate(
                    serverUrl = "https://media.example.test/",
                    username = "alice",
                    password = "",
                )

            assertEquals(
                JellyfinAuthenticationResult.Authenticated(
                    session =
                        io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession(
                            accessToken = "test-access-token",
                            userId = "00000000-0000-0000-0000-000000000001",
                            serverId = "test-server-id",
                        ),
                    displayName = "Alice",
                ),
                result,
            )
        }

    @Test
    fun `incomplete authentication response is rejected`() =
        runTest {
            val incompleteResponses =
                listOf(
                    SdkAuthenticationSession("", "user-id", "server-id", "Alice"),
                    SdkAuthenticationSession("token", "", "server-id", "Alice"),
                    SdkAuthenticationSession("token", "user-id", "", "Alice"),
                    SdkAuthenticationSession("token", "user-id", "server-id", ""),
                )

            incompleteResponses.forEach { response ->
                val repository = repositoryWithAuthenticator { _, _, _ -> response }

                assertEquals(
                    JellyfinAuthenticationResult.InvalidResponse,
                    repository.authenticate("https://media.example.test/", "alice", "password"),
                )
            }
        }

    @Test
    fun `authentication failures map to app errors`() =
        runTest {
            val cases =
                listOf(
                    InvalidStatusException(401) to JellyfinAuthenticationResult.InvalidCredentials,
                    TimeoutException() to JellyfinAuthenticationResult.Unreachable,
                    SecureConnectionException() to JellyfinAuthenticationResult.SecureConnectionFailed,
                    InvalidStatusException(503) to JellyfinAuthenticationResult.ServerRejected,
                    InvalidContentException() to JellyfinAuthenticationResult.InvalidResponse,
                    ApiClientException() to JellyfinAuthenticationResult.UnexpectedFailure,
                )

            cases.forEach { (failure, expected) ->
                val repository = repositoryWithAuthenticator { _, _, _ -> throw failure }

                assertEquals(
                    expected,
                    repository.authenticate("https://media.example.test/", "alice", "password"),
                )
            }
        }

    @Test
    fun `authentication cancellation is rethrown`() =
        runTest {
            val repository = repositoryWithAuthenticator { _, _, _ -> throw CancellationException() }

            try {
                repository.authenticate("https://media.example.test/", "alice", "password")
                fail("CancellationException expected")
            } catch (_: CancellationException) {
                // Expected: structured concurrency cancellation must not become a UI error.
            }
        }

    @Test
    fun `restored session fetches and maps accessible user views`() =
        runTest {
            val calls = mutableListOf<LibraryCall>()
            val repository =
                repositoryWithLibraries { serverUrl, accessToken, userId ->
                    calls += LibraryCall(serverUrl, accessToken, userId)
                    listOf(
                        SdkJellyfinLibrary(
                            id = "00000000-0000-0000-0000-000000000010",
                            name = "Films",
                            collectionType = "MOVIES",
                        ),
                        SdkJellyfinLibrary(
                            id = "00000000-0000-0000-0000-000000000011",
                            name = "Séries",
                            collectionType = "TVSHOWS",
                        ),
                    )
                }
            val session = testSession()

            val result = repository.fetchLibraries("https://media.example.test/", session)

            assertEquals(
                listOf(
                    LibraryCall(
                        serverUrl = "https://media.example.test/",
                        accessToken = "test-access-token",
                        userId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    ),
                ),
                calls,
            )
            assertEquals(
                JellyfinLibrariesResult.Success(
                    listOf(
                        JellyfinLibrary(
                            id = "00000000-0000-0000-0000-000000000010",
                            name = "Films",
                            collectionType = "MOVIES",
                        ),
                        JellyfinLibrary(
                            id = "00000000-0000-0000-0000-000000000011",
                            name = "Séries",
                            collectionType = "TVSHOWS",
                        ),
                    ),
                ),
                result,
            )
        }

    @Test
    fun `invalid restored session is rejected before SDK call`() =
        runTest {
            var calls = 0
            val repository =
                repositoryWithLibraries { _, _, _ ->
                    calls += 1
                    emptyList()
                }

            val result =
                repository.fetchLibraries(
                    "https://media.example.test/",
                    testSession().copy(userId = "not-a-uuid"),
                )

            assertEquals(JellyfinLibrariesResult.InvalidSession, result)
            assertEquals(0, calls)
        }

    @Test
    fun `incomplete user view is rejected`() =
        runTest {
            val repository =
                repositoryWithLibraries { _, _, _ ->
                    listOf(SdkJellyfinLibrary(id = "", name = "Films", collectionType = "MOVIES"))
                }

            assertEquals(
                JellyfinLibrariesResult.InvalidResponse,
                repository.fetchLibraries("https://media.example.test/", testSession()),
            )
        }

    @Test
    fun `library failures distinguish revoked session from retryable errors`() =
        runTest {
            val cases =
                listOf(
                    InvalidStatusException(401) to JellyfinLibrariesResult.InvalidSession,
                    TimeoutException() to JellyfinLibrariesResult.Unreachable,
                    SecureConnectionException() to JellyfinLibrariesResult.SecureConnectionFailed,
                    InvalidStatusException(503) to JellyfinLibrariesResult.ServerRejected,
                    InvalidContentException() to JellyfinLibrariesResult.InvalidResponse,
                    ApiClientException() to JellyfinLibrariesResult.UnexpectedFailure,
                )

            cases.forEach { (failure, expected) ->
                val repository = repositoryWithLibraries { _, _, _ -> throw failure }

                assertEquals(
                    expected,
                    repository.fetchLibraries("https://media.example.test/", testSession()),
                )
            }
        }

    @Test
    fun `library cancellation is rethrown`() =
        runTest {
            val repository = repositoryWithLibraries { _, _, _ -> throw CancellationException() }

            try {
                repository.fetchLibraries("https://media.example.test/", testSession())
                fail("CancellationException expected")
            } catch (_: CancellationException) {
                // Expected: structured concurrency cancellation must not become a UI error.
            }
        }

    private fun repositoryWithAuthenticator(
        authenticator: JellyfinUserAuthenticator,
    ): SdkJellyfinRepository =
        SdkJellyfinRepository(
            probe = JellyfinServerProbe { PublicSystemInfo() },
            authenticator = authenticator,
            libraryProvider = JellyfinLibraryProvider { _, _, _ -> emptyList() },
        )

    private fun repositoryWithProbe(
        probe: JellyfinServerProbe,
    ): SdkJellyfinRepository =
        SdkJellyfinRepository(
            probe = probe,
            authenticator = JellyfinUserAuthenticator { _, _, _ -> error("Not used by connection tests") },
            libraryProvider = JellyfinLibraryProvider { _, _, _ -> emptyList() },
        )

    private fun repositoryWithLibraries(
        provider: JellyfinLibraryProvider,
    ): SdkJellyfinRepository =
        SdkJellyfinRepository(
            probe = JellyfinServerProbe { PublicSystemInfo() },
            authenticator = JellyfinUserAuthenticator { _, _, _ -> error("Not used by library tests") },
            libraryProvider = provider,
        )

    private fun testSession() =
        JellyfinSession(
            accessToken = "test-access-token",
            userId = "00000000-0000-0000-0000-000000000001",
            serverId = "test-server-id",
        )
}

private data class LibraryCall(
    val serverUrl: String,
    val accessToken: String,
    val userId: UUID,
)
