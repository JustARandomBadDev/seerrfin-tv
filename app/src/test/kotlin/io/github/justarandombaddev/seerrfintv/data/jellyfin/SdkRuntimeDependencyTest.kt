package io.github.justarandombaddev.seerrfintv.data.jellyfin

import org.junit.Assert.assertNotNull
import org.junit.Test

class SdkRuntimeDependencyTest {
    @Test
    fun `SLF4J LoggerFactory required by Jellyfin SDK is available at runtime`() {
        assertNotNull(Class.forName("org.slf4j.LoggerFactory"))
    }
}
