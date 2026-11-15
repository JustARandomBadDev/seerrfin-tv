package io.github.justarandombaddev.seerrfintv.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSeekTest {
    @Test
    fun `seek target stays inside known media bounds`() {
        assertEquals(0, boundedSeekTarget(currentMs = 3_000, offsetMs = -10_000, durationMs = 60_000))
        assertEquals(60_000, boundedSeekTarget(currentMs = 58_000, offsetMs = 10_000, durationMs = 60_000))
        assertEquals(30_000, boundedSeekTarget(currentMs = 20_000, offsetMs = 10_000, durationMs = 60_000))
    }

    @Test
    fun `unknown duration only applies the lower bound`() {
        assertEquals(30_000, boundedSeekTarget(currentMs = 20_000, offsetMs = 10_000, durationMs = 0))
        assertEquals(0, boundedSeekTarget(currentMs = 2_000, offsetMs = -10_000, durationMs = 0))
    }
}
