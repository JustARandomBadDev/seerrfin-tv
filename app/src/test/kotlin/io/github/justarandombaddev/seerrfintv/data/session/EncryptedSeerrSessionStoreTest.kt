package io.github.justarandombaddev.seerrfintv.data.session

import io.github.justarandombaddev.seerrfintv.domain.model.SeerrSession
import io.github.justarandombaddev.seerrfintv.domain.session.SeerrSessionRestoreResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EncryptedSeerrSessionStoreTest {
    @Test
    fun `Seerr session round trips without cookie or server address in storage`() =
        runTest {
            val storage = TestEncryptedSessionStorage()
            val store = EncryptedSeerrSessionStore(storage, BoundSessionCipher())
            val session = testSession()

            store.save(session)

            assertEquals(SeerrSessionRestoreResult.Restored(session), store.restore())
            val persisted = requireNotNull(storage.value)
            assertFalse(persisted.contains(session.cookieValue))
            assertFalse(persisted.contains(session.serverUrl))
        }

    @Test
    fun `Seerr record cannot be restored as a Jellyfin session`() =
        runTest {
            val storage = TestEncryptedSessionStorage()
            val cipher = BoundSessionCipher()
            EncryptedSeerrSessionStore(storage, cipher).save(testSession())

            val result = EncryptedJellyfinSessionStore(storage, cipher).restore()

            assertEquals(
                io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionRestoreResult.Unreadable,
                result,
            )
            assertNull(storage.value)
        }

    @Test
    fun `malformed Seerr session is cleared and reported unreadable`() =
        runTest {
            val storage = TestEncryptedSessionStorage(value = "not-a-session")
            val store = EncryptedSeerrSessionStore(storage, BoundSessionCipher())

            assertEquals(SeerrSessionRestoreResult.Unreadable, store.restore())
            assertNull(storage.value)
            assertEquals(1, storage.clearCalls)
        }

    @Test
    fun `clear removes only the Seerr record`() =
        runTest {
            val storage = TestEncryptedSessionStorage()
            val store = EncryptedSeerrSessionStore(storage, BoundSessionCipher())
            store.save(testSession())

            store.clear()

            assertEquals(SeerrSessionRestoreResult.Missing, store.restore())
        }

    private fun testSession() =
        SeerrSession(
            serverUrl = "https://requests.example.test/",
            cookieName = "connect.sid",
            cookieValue = "s%3Atest-cookie.signature",
            expiresAtEpochMillis = 4_102_444_800_000L,
            secure = true,
        )
}

private class TestEncryptedSessionStorage(
    var value: String? = null,
) : EncryptedSessionStorage {
    var clearCalls = 0
        private set

    override fun read(): String? = value

    override fun write(value: String) {
        this.value = value
    }

    override fun clear() {
        value = null
        clearCalls += 1
    }
}

private class BoundSessionCipher : SessionCipher {
    override fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): EncryptedSessionPayload =
        EncryptedSessionPayload(
            initializationVector = associatedData.copyOf(),
            ciphertext = plaintext.reversedArray(),
        )

    override fun decrypt(
        payload: EncryptedSessionPayload,
        associatedData: ByteArray,
    ): ByteArray {
        require(payload.initializationVector.contentEquals(associatedData))
        return payload.ciphertext.reversedArray()
    }
}
