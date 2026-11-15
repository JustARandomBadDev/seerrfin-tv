package io.github.justarandombaddev.seerrfintv

import android.content.Context
import io.github.justarandombaddev.seerrfintv.data.jellyfin.SdkJellyfinRepository
import io.github.justarandombaddev.seerrfintv.data.jellyfin.ServerUrlNormalizer
import io.github.justarandombaddev.seerrfintv.data.session.EncryptedJellyfinSessionStore
import io.github.justarandombaddev.seerrfintv.data.settings.DataStoreServerSettingsRepository
import io.github.justarandombaddev.seerrfintv.data.settings.serverSettingsDataStore
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinRepository
import io.github.justarandombaddev.seerrfintv.domain.repository.ServerSettingsRepository
import io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionStore
import io.github.justarandombaddev.seerrfintv.playback.Media3PlaybackEngine
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    val serverUrlNormalizer = ServerUrlNormalizer()

    val jellyfinRepository: JellyfinRepository =
        SdkJellyfinRepository(
            jellyfin =
                createJellyfin {
                    this.context = context.applicationContext
                    clientInfo = ClientInfo(name = "Seerrfin TV", version = "0.1.0")
                },
        )

    val serverSettingsRepository: ServerSettingsRepository =
        DataStoreServerSettingsRepository(context.serverSettingsDataStore)

    val jellyfinSessionStore: JellyfinSessionStore =
        EncryptedJellyfinSessionStore(applicationContext)

    fun createPlaybackEngine(): Media3PlaybackEngine = Media3PlaybackEngine(applicationContext)
}
