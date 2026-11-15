package io.github.justarandombaddev.seerrfintv.data.jellyfin

import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.seconds
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.api.PublicSystemInfo
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinConnectionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinRepository

fun interface JellyfinServerProbe {
    suspend fun getPublicSystemInfo(serverUrl: String): PublicSystemInfo
}

class SdkJellyfinRepository internal constructor(
    private val probe: JellyfinServerProbe,
) : JellyfinRepository {
    constructor(jellyfin: Jellyfin) : this(
        probe =
            JellyfinServerProbe { serverUrl ->
                val api =
                    jellyfin.createApi(
                        baseUrl = serverUrl,
                        httpClientOptions =
                            HttpClientOptions(
                                connectTimeout = 6.seconds,
                                requestTimeout = 15.seconds,
                                socketTimeout = 15.seconds,
                            ),
                    )
                api.systemApi.getPublicSystemInfo().content
            },
    )

    override suspend fun connect(serverUrl: String): JellyfinConnectionResult =
        try {
            val systemInfo = probe.getPublicSystemInfo(serverUrl)
            if (systemInfo.isJellyfinServer()) {
                JellyfinConnectionResult.Connected(serverUrl)
            } else {
                JellyfinConnectionResult.InvalidResponse
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: TimeoutException) {
            JellyfinConnectionResult.Unreachable
        } catch (_: SecureConnectionException) {
            JellyfinConnectionResult.SecureConnectionFailed
        } catch (_: InvalidStatusException) {
            JellyfinConnectionResult.ServerRejected
        } catch (_: InvalidContentException) {
            JellyfinConnectionResult.InvalidResponse
        } catch (_: ApiClientException) {
            JellyfinConnectionResult.UnexpectedFailure
        }

    private fun PublicSystemInfo.isJellyfinServer(): Boolean =
        productName.equals(JELLYFIN_PRODUCT_NAME, ignoreCase = true) && !version.isNullOrBlank()

    private companion object {
        const val JELLYFIN_PRODUCT_NAME = "Jellyfin Server"
    }
}
