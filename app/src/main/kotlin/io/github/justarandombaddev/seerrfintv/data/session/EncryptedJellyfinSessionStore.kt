package io.github.justarandombaddev.seerrfintv.data.session

import android.content.Context
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession
import io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionStore
import io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionRestoreResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Base64

internal data class EncryptedSessionPayload(
    val initializationVector: ByteArray,
    val ciphertext: ByteArray,
)

internal interface SessionCipher {
    fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): EncryptedSessionPayload

    fun decrypt(
        payload: EncryptedSessionPayload,
        associatedData: ByteArray,
    ): ByteArray
}

internal interface EncryptedSessionStorage {
    fun read(): String?

    fun write(value: String)

    fun clear()
}

class EncryptedJellyfinSessionStore internal constructor(
    private val storage: EncryptedSessionStorage,
    private val cipher: SessionCipher,
) : JellyfinSessionStore {
    constructor(context: Context) : this(
        storage = SharedPreferencesEncryptedSessionStorage(context.applicationContext),
        cipher = AndroidKeystoreSessionCipher(),
    )

    override suspend fun save(session: JellyfinSession) {
        val plaintext = JellyfinSessionCodec.encode(session)
        val encrypted = cipher.encrypt(plaintext, ASSOCIATED_DATA)
        storage.write(EncryptedSessionRecord.encode(encrypted))
    }

    override suspend fun restore(): JellyfinSessionRestoreResult {
        return try {
            val record = storage.read() ?: return JellyfinSessionRestoreResult.Missing
            val encrypted = EncryptedSessionRecord.decode(record)
            val plaintext = cipher.decrypt(encrypted, ASSOCIATED_DATA)
            JellyfinSessionRestoreResult.Restored(JellyfinSessionCodec.decode(plaintext))
        } catch (_: Exception) {
            try {
                storage.clear()
            } catch (_: Exception) {
                // A corrupt session is never returned even when cleanup itself fails.
            }
            JellyfinSessionRestoreResult.Unreadable
        }
    }

    override suspend fun clear() {
        storage.clear()
    }

    private companion object {
        val ASSOCIATED_DATA =
            "io.github.justarandombaddev.seerrfintv:jellyfin-session:v1".encodeToByteArray()
    }
}

private class SharedPreferencesEncryptedSessionStorage(
    context: Context,
) : EncryptedSessionStorage {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(SESSION_RECORD_KEY, null)

    override fun write(value: String) {
        if (!preferences.edit().putString(SESSION_RECORD_KEY, value).commit()) {
            throw IOException("Unable to persist encrypted Jellyfin session")
        }
    }

    override fun clear() {
        if (!preferences.edit().remove(SESSION_RECORD_KEY).commit()) {
            throw IOException("Unable to clear encrypted Jellyfin session")
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "secure_jellyfin_session"
        const val SESSION_RECORD_KEY = "encrypted_session_v1"
    }
}

private object EncryptedSessionRecord {
    private const val PREFIX = "v1"
    private const val PART_COUNT = 3

    fun encode(payload: EncryptedSessionPayload): String =
        listOf(
            PREFIX,
            Base64.getEncoder().encodeToString(payload.initializationVector),
            Base64.getEncoder().encodeToString(payload.ciphertext),
        ).joinToString(":")

    fun decode(record: String): EncryptedSessionPayload {
        val parts = record.split(':')
        require(parts.size == PART_COUNT && parts[0] == PREFIX)
        val initializationVector = Base64.getDecoder().decode(parts[1])
        val ciphertext = Base64.getDecoder().decode(parts[2])
        require(initializationVector.isNotEmpty() && ciphertext.isNotEmpty())
        return EncryptedSessionPayload(initializationVector, ciphertext)
    }
}

private object JellyfinSessionCodec {
    private const val MAGIC = 0x53465456
    private const val VERSION = 1
    private const val FIELD_COUNT = 3
    private const val MAX_FIELD_BYTES = 64 * 1024

    fun encode(session: JellyfinSession): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                writeField(output, session.accessToken)
                writeField(output, session.userId)
                writeField(output, session.serverId)
            }
            bytes.toByteArray()
        }

    fun decode(bytes: ByteArray): JellyfinSession =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC)
            require(input.readInt() == VERSION)
            val fields = List(FIELD_COUNT) { readField(input) }
            require(input.available() == 0)
            JellyfinSession(
                accessToken = fields[0],
                userId = fields[1],
                serverId = fields[2],
            ).also { session ->
                require(
                    session.accessToken.isNotBlank() &&
                        session.userId.isNotBlank() &&
                        session.serverId.isNotBlank(),
                )
            }
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
