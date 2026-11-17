package io.github.justarandombaddev.seerrfintv

import android.content.Context
import android.util.Log
import io.github.justarandombaddev.seerrfintv.data.jellyfin.SdkJellyfinRepository
import io.github.justarandombaddev.seerrfintv.data.jellyfin.ServerUrlNormalizer
import io.github.justarandombaddev.seerrfintv.data.session.EncryptedJellyfinSessionStore
import io.github.justarandombaddev.seerrfintv.data.session.EncryptedSeerrSessionStore
import io.github.justarandombaddev.seerrfintv.data.seerr.OkHttpSeerrRepository
import io.github.justarandombaddev.seerrfintv.data.settings.DataStoreSeerrSettingsRepository
import io.github.justarandombaddev.seerrfintv.data.settings.DataStoreServerSettingsRepository
import io.github.justarandombaddev.seerrfintv.data.settings.serverSettingsDataStore
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinRepository
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrApiRepository
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrRepository
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrSettingsRepository
import io.github.justarandombaddev.seerrfintv.domain.repository.ServerSettingsRepository
import io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionStore
import io.github.justarandombaddev.seerrfintv.domain.session.SeerrSessionStore
import io.github.justarandombaddev.seerrfintv.playback.AndroidPlaybackCapabilitiesDetector
import io.github.justarandombaddev.seerrfintv.playback.Media3PlaybackEngine
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    val serverUrlNormalizer = ServerUrlNormalizer()
    private val playbackCapabilities =
        AndroidPlaybackCapabilitiesDetector(applicationContext).detect().also { capabilities ->
            Log.i(
                "SeerrfinPlayback",
                "Detected video=${capabilities.videoCodecs.joinToString { codec ->
                    "${codec.codec}:${codec.maxWidth}x${codec.maxHeight}:L${codec.maxLevel ?: "n/a"}"
                }} " +
                    "audio=${capabilities.audioCodecs.joinToString()} " +
                    "channels=${capabilities.maxAudioChannels} bitrate=${capabilities.maxStreamingBitrate}",
            )
        }

    val jellyfinRepository: JellyfinRepository =
        SdkJellyfinRepository(
            jellyfin =
                createJellyfin {
                    this.context = context.applicationContext
                    clientInfo = ClientInfo(name = "Seerrfin TV", version = "0.1.0")
                },
            playbackCapabilities = playbackCapabilities,
        )

    val serverSettingsRepository: ServerSettingsRepository =
        DataStoreServerSettingsRepository(context.serverSettingsDataStore)

    val jellyfinSessionStore: JellyfinSessionStore =
        EncryptedJellyfinSessionStore(applicationContext)

    private val seerrHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

    private val seerrClient = OkHttpSeerrRepository(seerrHttpClient)

    val seerrRepository: SeerrRepository = seerrClient

    val seerrApiRepository: SeerrApiRepository = seerrClient

    val seerrSettingsRepository: SeerrSettingsRepository =
        DataStoreSeerrSettingsRepository(context.serverSettingsDataStore)

    val seerrSessionStore: SeerrSessionStore = EncryptedSeerrSessionStore(applicationContext)

    fun createPlaybackEngine(): Media3PlaybackEngine = Media3PlaybackEngine(applicationContext)
}
