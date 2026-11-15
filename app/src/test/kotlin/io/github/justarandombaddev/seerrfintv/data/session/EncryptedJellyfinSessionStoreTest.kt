package io.github.justarandombaddev.seerrfintv.data.session

import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession
import io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionRestoreResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedJellyfinSessionStoreTest {
    @Test
    fun `session round trips without plaintext in storage`() =
        runTest {
            val storage = FakeEncryptedSessionStorage()
            val store = EncryptedJellyfinSessionStore(storage, ReversingSessionCipher())
            val session = testSession()

            store.save(session)

            assertEquals(JellyfinSessionRestoreResult.Restored(session), store.restore())
            val persisted = requireNotNull(storage.value)
            assertFalse(persisted.contains(session.accessToken))
            assertFalse(persisted.contains(session.userId))
            assertFalse(persisted.contains(session.serverId))
        }

    @Test
    fun `session string representation redacts every identifier`() {
        val session = testSession()

        val rendered = session.toString()

        assertFalse(rendered.contains(session.accessToken))
        assertFalse(rendered.contains(session.userId))
        assertFalse(rendered.contains(session.serverId))
    }

    @Test
    fun `saving a new session replaces the previous record`() =
        runTest {
            val storage = FakeEncryptedSessionStorage()
            val store = EncryptedJellyfinSessionStore(storage, ReversingSessionCipher())
            store.save(testSession())
            val replacement =
                JellyfinSession(
                    accessToken = "replacement-token",
                    userId = "replacement-user",
                    serverId = "replacement-server",
                )

            store.save(replacement)

            assertEquals(JellyfinSessionRestoreResult.Restored(replacement), store.restore())
        }

    @Test
    fun `clear removes the encrypted record`() =
        runTest {
            val storage = FakeEncryptedSessionStorage()
            val store = EncryptedJellyfinSessionStore(storage, ReversingSessionCipher())
            store.save(testSession())

            store.clear()

            assertEquals(JellyfinSessionRestoreResult.Missing, store.restore())
            assertNull(storage.value)
        }

    @Test
    fun `missing record is distinguished from unreadable session`() =
        runTest {
            val store = EncryptedJellyfinSessionStore(FakeEncryptedSessionStorage(), ReversingSessionCipher())

            assertEquals(JellyfinSessionRestoreResult.Missing, store.restore())
        }

    @Test
    fun `malformed record is cleared and treated as no session`() =
        runTest {
            val storage = FakeEncryptedSessionStorage(value = "not-an-encrypted-session")
            val store = EncryptedJellyfinSessionStore(storage, ReversingSessionCipher())

            assertEquals(JellyfinSessionRestoreResult.Unreadable, store.restore())
            assertNull(storage.value)
            assertTrue(storage.clearCalls > 0)
        }

    @Test
    fun `decryption failure is cleared and treated as no session`() =
        runTest {
            val storage = FakeEncryptedSessionStorage(value = "v1:AQ==:Ag==")
            val store =
                EncryptedJellyfinSessionStore(
                    storage = storage,
                    cipher =
                        object : SessionCipher {
                            override fun encrypt(
                                plaintext: ByteArray,
                                associatedData: ByteArray,
                            ) = error("not used")

                            override fun decrypt(
                                payload: EncryptedSessionPayload,
                                associatedData: ByteArray,
                            ): ByteArray = throw SecurityException("cannot decrypt")
                        },
                )

            assertEquals(JellyfinSessionRestoreResult.Unreadable, store.restore())
            assertNull(storage.value)
            assertEquals(1, storage.clearCalls)
        }

    @Test
    fun `storage read failure is cleared and reported as unreadable`() =
        runTest {
            val storage = FakeEncryptedSessionStorage(readFailure = IllegalStateException("storage unavailable"))
            val store = EncryptedJellyfinSessionStore(storage, ReversingSessionCipher())

            assertEquals(JellyfinSessionRestoreResult.Unreadable, store.restore())
            assertEquals(1, storage.clearCalls)
        }

    private fun testSession() =
        JellyfinSession(
            accessToken = "test-access-token",
            userId = "00000000-0000-0000-0000-000000000001",
            serverId = "test-server-id",
        )
}

private class FakeEncryptedSessionStorage(
    var value: String? = null,
    private val readFailure: Exception? = null,
) : EncryptedSessionStorage {
    var clearCalls: Int = 0
        private set

    override fun read(): String? {
        readFailure?.let { throw it }
        return value
    }

    override fun write(value: String) {
        this.value = value
    }

    override fun clear() {
        value = null
        clearCalls += 1
    }
}

private class ReversingSessionCipher : SessionCipher {
    override fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): EncryptedSessionPayload =
        EncryptedSessionPayload(
            initializationVector = byteArrayOf(1, 2, 3),
            ciphertext = plaintext.reversedArray().map { byte -> (byte.toInt() xor 0x5A).toByte() }.toByteArray(),
        )

    override fun decrypt(
        payload: EncryptedSessionPayload,
        associatedData: ByteArray,
    ): ByteArray =
        payload.ciphertext
            .map { byte -> (byte.toInt() xor 0x5A).toByte() }
            .toByteArray()
            .reversedArray()
}
