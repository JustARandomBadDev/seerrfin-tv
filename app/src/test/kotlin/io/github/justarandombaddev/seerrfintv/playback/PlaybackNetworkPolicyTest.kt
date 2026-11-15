package io.github.justarandombaddev.seerrfintv.playback

import io.github.justarandombaddev.seerrfintv.domain.model.PlaybackMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackNetworkPolicyTest {
    @Test
    fun `playback response timeout allows bounded transcoder startup`() {
        PlaybackMethod.entries.forEach { method ->
            assertEquals(60_000, playbackReadTimeoutMs(method))
        }
    }
}
