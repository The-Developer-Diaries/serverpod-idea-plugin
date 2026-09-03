package dev.serverpod.idea.cli

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerpodFeatureTest {

    private val three = ServerpodVersion(3, 4, 13)
    private val fourRc = ServerpodVersion(4, 0, 0, "rc.1")
    private val four = ServerpodVersion(4, 0, 0)

    @Test
    fun `gates a Serverpod 4 command on a Serverpod 4 CLI`() {
        assertFalse(ServerpodFeature.START.isSupportedBy(three))
        assertTrue(ServerpodFeature.START.isSupportedBy(four))
    }

    @Test
    fun `offers Serverpod 4 commands to a pre-release, which already has them`() {
        assertTrue(ServerpodFeature.START.isSupportedBy(fourRc))
        assertTrue(ServerpodFeature.AGENT_TOOLING.isSupportedBy(fourRc))
    }

    @Test
    fun `withdraws a feature the CLI dropped`() {
        assertTrue(ServerpodFeature.MINI_TEMPLATE.isSupportedBy(three))
        assertFalse(ServerpodFeature.MINI_TEMPLATE.isSupportedBy(fourRc))
        assertFalse(ServerpodFeature.MINI_TEMPLATE.isSupportedBy(four))
    }

    @Test
    fun `falls back to the long-standing surface when the version is unknown`() {
        // Nothing has run `serverpod version` yet, so no Serverpod 4 command may
        // be offered, while what has always existed stays available.
        assertFalse(ServerpodFeature.START.isSupportedBy(null))
        assertFalse(ServerpodFeature.EMBEDDED_DATABASE.isSupportedBy(null))
        assertTrue(ServerpodFeature.MINI_TEMPLATE.isSupportedBy(null))
    }
}
