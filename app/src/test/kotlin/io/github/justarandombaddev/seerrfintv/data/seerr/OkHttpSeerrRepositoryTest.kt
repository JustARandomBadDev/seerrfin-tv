package io.github.justarandombaddev.seerrfintv.data.seerr

import io.github.justarandombaddev.seerrfintv.domain.model.SeerrSession
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrQuickConnectCheckResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrQuickConnectInitiationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrSessionValidationResult
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpSeerrRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: OkHttpSeerrRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = OkHttpSeerrRepository(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `initiate quick connect uses the documented endpoint and keeps secret out of strings`() =
        runTest {
            server.enqueue(jsonResponse("""{"code":"135790","secret":"a1b2c3d4e5f6"}"""))

            val result = repository.initiateQuickConnect(server.url("/").toString())

            val ready = result as SeerrQuickConnectInitiationResult.Ready
            assertEquals("135790", ready.challenge.code)
            assertEquals("a1b2c3d4e5f6", ready.challenge.secret)
            assertFalse(ready.challenge.toString().contains("a1b2c3d4e5f6"))
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/auth/jellyfin/quickconnect/initiate", request.target)
        }

    @Test
    fun `quick connect check sends secret as encoded query and reports authorization`() =
        runTest {
            server.enqueue(jsonResponse("""{"authenticated":true}"""))

            val result = repository.checkQuickConnect(server.url("/").toString(), "a1b2c3d4")

            assertEquals(SeerrQuickConnectCheckResult.Authorized, result)
            assertEquals(
                "/api/v1/auth/jellyfin/quickconnect/check?secret=a1b2c3d4",
                server.takeRequest().target,
            )
        }

    @Test
    fun `authenticate captures only Seerr session cookie then verifies current user and permissions`() =
        runTest {
            server.enqueue(
                jsonResponse("""{"id":7}""")
                    .newBuilder()
                    .addHeader(
                        "Set-Cookie",
                        "connect.sid=s%3Aprivate-cookie.signature; Path=/; HttpOnly; Max-Age=2592000; SameSite=Lax",
                    ).build(),
            )
            server.enqueue(
                jsonResponse(
                    """{"id":7,"email":"alice@example.test","username":"Alice","permissions":786464}""",
                ),
            )

            val result =
                repository.authenticateQuickConnect(
                    serverUrl = server.url("/").toString(),
                    secret = "a1b2c3d4",
                )

            val authenticated = result as SeerrAuthenticationResult.Authenticated
            assertEquals(7L, authenticated.user.id)
            assertEquals("Alice", authenticated.user.displayName)
            assertTrue(authenticated.user.permissions.canRequestMovies)
            assertTrue(authenticated.user.permissions.canRequestSeries)
            assertEquals("connect.sid", authenticated.session.cookieName)
            assertEquals("s%3Aprivate-cookie.signature", authenticated.session.cookieValue)
            assertFalse(authenticated.session.toString().contains("private-cookie"))

            val authenticationRequest = server.takeRequest()
            assertEquals("POST", authenticationRequest.method)
            assertEquals(
                "{\"secret\":\"a1b2c3d4\"}",
                requireNotNull(authenticationRequest.body).utf8(),
            )
            val currentUserRequest = server.takeRequest()
            assertEquals("GET", currentUserRequest.method)
            assertEquals("/api/v1/auth/me", currentUserRequest.target)
            assertEquals(
                "connect.sid=s%3Aprivate-cookie.signature",
                currentUserRequest.headers["Cookie"],
            )
        }

    @Test
    fun `authentication rejects a successful response without session cookie`() =
        runTest {
            server.enqueue(jsonResponse("""{"id":7}"""))

            val result =
                repository.authenticateQuickConnect(
                    serverUrl = server.url("/").toString(),
                    secret = "a1b2c3d4",
                )

            assertEquals(SeerrAuthenticationResult.InvalidResponse, result)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `current user requires server permission mask`() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """{"id":7,"email":"alice@example.test","username":"Alice"}""",
                ),
            )
            val session = testSession(server.url("/").toString())

            val result = repository.validateSession(session)

            assertEquals(SeerrSessionValidationResult.InvalidResponse, result)
        }

    @Test
    fun `current user unauthorized response expires the Seerr session`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(401).build())

            val result = repository.validateSession(testSession(server.url("/").toString()))

            assertEquals(SeerrSessionValidationResult.Expired, result)
        }

    @Test
    fun `quick connect forbidden response is reported as unavailable`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(403).build())

            val result = repository.initiateQuickConnect(server.url("/").toString())

            assertEquals(SeerrQuickConnectInitiationResult.Unavailable, result)
        }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    private fun testSession(serverUrl: String) =
        SeerrSession(
            serverUrl = serverUrl,
            cookieName = "connect.sid",
            cookieValue = "s%3Astored-cookie.signature",
            expiresAtEpochMillis = System.currentTimeMillis() + 60_000L,
        )
}
