package dev.icedtea.kodex.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionCompareTest {
    @Test
    fun numericPartsCompareNumerically() {
        assertTrue(compareVersions("1.10.0", "1.9.3") > 0)
        assertTrue(compareVersions("v2.0", "1.99.99") > 0)
        assertEquals(0, compareVersions("1.2", "1.2.0"))
    }

    @Test
    fun finalOutranksPrerelease() {
        assertTrue(compareVersions("1.2.0", "1.2.0-beta.1") > 0)
        assertTrue(compareVersions("1.2.0-beta.2", "1.2.0-beta.1") > 0)
        assertTrue(compareVersions("1.2.1-rc.1", "1.2.0") > 0)
    }

    @Test
    fun channelParse() {
        assertEquals(UpdateChannel.DEV, UpdateChannel.parse(" Dev "))
        assertEquals(null, UpdateChannel.parse(""))
    }
}
