package dev.serverpod.idea.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerpodVersionTest {

    @Test
    fun `parses the line the CLI prints`() {
        assertEquals(ServerpodVersion(3, 4, 13), ServerpodVersion.parse("Serverpod version: 3.4.13"))
    }

    @Test
    fun `parses a pre-release`() {
        assertEquals(
            ServerpodVersion(4, 0, 0, "rc.1"),
            ServerpodVersion.parse("Serverpod version: 4.0.0-rc.1"),
        )
    }

    @Test
    fun `drops build metadata, which carries no ordering`() {
        assertEquals(ServerpodVersion(4, 0, 0), ServerpodVersion.parse("4.0.0+build.7"))
    }

    @Test
    fun `returns null for output with no version in it`() {
        assertNull(ServerpodVersion.parse("command not found"))
        assertNull(ServerpodVersion.parse(null))
    }

    @Test
    fun `orders by the numeric parts first`() {
        assertTrue(ServerpodVersion(3, 4, 13) < ServerpodVersion(4, 0, 0))
        assertTrue(ServerpodVersion(3, 4, 2) < ServerpodVersion(3, 4, 13))
        assertTrue(ServerpodVersion(3, 10, 0) > ServerpodVersion(3, 9, 9))
    }

    @Test
    fun `orders a pre-release below the release it leads to`() {
        assertTrue(ServerpodVersion(4, 0, 0, "rc.1") < ServerpodVersion(4, 0, 0))
        assertTrue(ServerpodVersion(4, 0, 0, "beta.1") < ServerpodVersion(4, 0, 0, "rc.1"))
        assertTrue(ServerpodVersion(4, 0, 0, "rc.2") > ServerpodVersion(4, 0, 0, "rc.1"))
        assertTrue(ServerpodVersion(4, 0, 0, "rc.10") > ServerpodVersion(4, 0, 0, "rc.9"))
    }

    @Test
    fun `treats a pre-release as already carrying its release line's surface`() {
        val four = ServerpodVersion(4, 0, 0)

        assertTrue(ServerpodVersion(4, 0, 0, "beta.1").hasSurfaceOf(four))
        assertTrue(ServerpodVersion(4, 0, 0, "rc.1").hasSurfaceOf(four))
        assertTrue(ServerpodVersion(4, 1, 0).hasSurfaceOf(four))
        assertFalse(ServerpodVersion(3, 4, 13).hasSurfaceOf(four))
    }

    @Test
    fun `renders back to what the CLI printed`() {
        assertEquals("4.0.0-rc.1", ServerpodVersion(4, 0, 0, "rc.1").toString())
        assertEquals("3.4.13", ServerpodVersion(3, 4, 13).toString())
    }
}
