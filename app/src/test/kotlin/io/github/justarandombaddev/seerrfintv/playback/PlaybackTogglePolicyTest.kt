package io.github.justarandombaddev.seerrfintv.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTogglePolicyTest {
    @Test
    fun `buffering playback intent toggles to pause`() {
        assertEquals(
            PlaybackToggleAction.Pause,
            playbackToggleAction(playWhenReady = true, playbackEnded = false),
        )
    }

    @Test
    fun `paused and ended playback toggle to play`() {
        assertEquals(
            PlaybackToggleAction.Play,
            playbackToggleAction(playWhenReady = false, playbackEnded = false),
        )
        assertEquals(
            PlaybackToggleAction.Play,
            playbackToggleAction(playWhenReady = true, playbackEnded = true),
        )
    }
}
