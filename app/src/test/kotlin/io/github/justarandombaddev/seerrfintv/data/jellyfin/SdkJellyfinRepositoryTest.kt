package io.github.justarandombaddev.seerrfintv.data.jellyfin

import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinConnectionResult
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class SdkJellyfinRepositoryTest {
    @Test
    fun `valid Jellyfin identity is accepted`() =
        runTest {
            val repository =
                SdkJellyfinRepository(
                    probe =
                        JellyfinServerProbe {
                            PublicSystemInfo(
                                version = "12.4.0",
                                productName = "Jellyfin Server",
                            )
                        },
                )

            assertEquals(
                JellyfinConnectionResult.Connected("https://media.example.test/"),
                repository.connect("https://media.example.test/"),
            )
        }

    @Test
    fun `empty public system information is rejected`() =
        runTest {
            val repository = SdkJellyfinRepository(probe = JellyfinServerProbe { PublicSystemInfo() })

            assertEquals(
                JellyfinConnectionResult.InvalidResponse,
                repository.connect("https://not-jellyfin.example.test/"),
            )
        }
}
