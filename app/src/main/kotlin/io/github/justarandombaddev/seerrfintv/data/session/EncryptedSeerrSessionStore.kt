package io.github.justarandombaddev.seerrfintv.data.session

import android.content.Context
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrSession
import io.github.justarandombaddev.seerrfintv.domain.session.SeerrSessionRestoreResult
import io.github.justarandombaddev.seerrfintv.domain.session.SeerrSessionStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class EncryptedSeerrSessionStore internal constructor(
    private val storage: EncryptedSessionStorage,
    private val cipher: SessionCipher,
) : SeerrSessionStore {
    constructor(context: Context) : this(
        storage = SharedPreferencesSeerrSessionStorage(context.applicationContext),
        cipher = AndroidKeystoreSessionCipher(SEERR_KEY_ALIAS),
    )

    override suspend fun save(session: SeerrSession) {
        val plaintext = SeerrSessionCodec.encode(session)
        val encrypted = cipher.encrypt(plaintext, ASSOCIATED_DATA)
        storage.write(EncryptedSessionRecord.encode(encrypted))
    }

    override suspend fun restore(): SeerrSessionRestoreResult =
        try {
            val record = storage.read() ?: return SeerrSessionRestoreResult.Missing
            val encrypted = EncryptedSessionRecord.decode(record)
            val plaintext = cipher.decrypt(encrypted, ASSOCIATED_DATA)
            SeerrSessionRestoreResult.Restored(SeerrSessionCodec.decode(plaintext))
        } catch (_: Exception) {
            try {
                storage.clear()
            } catch (_: Exception) {
                // Never return corrupt session material, even if cleanup fails.
            }
            SeerrSessionRestoreResult.Unreadable
        }

    override suspend fun clear() {
        storage.clear()
    }

    private companion object {
        const val SEERR_KEY_ALIAS = "seerrfin_tv_seerr_session_v1"
        val ASSOCIATED_DATA =
            "io.github.justarandombaddev.seerrfintv:seerr-session:v1".encodeToByteArray()
    }
}

private class SharedPreferencesSeerrSessionStorage(
    context: Context,
) : EncryptedSessionStorage {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(SESSION_RECORD_KEY, null)

    override fun write(value: String) {
        if (!preferences.edit().putString(SESSION_RECORD_KEY, value).commit()) {
            throw IOException("Unable to persist encrypted Seerr session")
        }
    }

    override fun clear() {
        if (!preferences.edit().remove(SESSION_RECORD_KEY).commit()) {
            throw IOException("Unable to clear encrypted Seerr session")
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "secure_seerr_session"
        const val SESSION_RECORD_KEY = "encrypted_session_v1"
    }
}

private object SeerrSessionCodec {
    private const val MAGIC = 0x53465352
    private const val VERSION = 1
    private const val MAX_FIELD_BYTES = 64 * 1024
    private const val SESSION_COOKIE_NAME = "connect.sid"

    fun encode(session: SeerrSession): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                writeField(output, session.serverUrl)
                writeField(output, session.cookieName)
                writeField(output, session.cookieValue)
                output.writeLong(session.expiresAtEpochMillis)
                output.writeBoolean(session.secure)
            }
            bytes.toByteArray()
        }

    fun decode(bytes: ByteArray): SeerrSession =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC)
            require(input.readInt() == VERSION)
            val session =
                SeerrSession(
                    serverUrl = readField(input),
                    cookieName = readField(input),
                    cookieValue = readField(input),
                    expiresAtEpochMillis = input.readLong(),
                    secure = input.readBoolean(),
                )
            require(input.available() == 0)
            require(
                session.serverUrl.isNotBlank() &&
                    session.cookieName == SESSION_COOKIE_NAME &&
                    session.cookieValue.isNotBlank() &&
                    session.expiresAtEpochMillis > 0L,
            )
            session
        }

    private fun writeField(
        output: DataOutputStream,
        value: String,
    ) {
        val bytes = value.encodeToByteArray()
        require(bytes.size in 1..MAX_FIELD_BYTES)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readField(input: DataInputStream): String {
        val length = input.readInt()
        require(length in 1..MAX_FIELD_BYTES)
        return ByteArray(length).also(input::readFully).decodeToString()
    }
}
