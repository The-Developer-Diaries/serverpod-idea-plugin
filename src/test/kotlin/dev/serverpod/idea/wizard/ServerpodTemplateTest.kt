package dev.serverpod.idea.wizard

import dev.serverpod.idea.cli.ServerpodVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ServerpodTemplateTest {

    private val three = ServerpodVersion(3, 4, 13)
    private val four = ServerpodVersion(4, 0, 0, "rc.1")

    @Test
    fun `offers the templates a Serverpod 3 CLI accepts`() {
        assertEquals(
            listOf(ServerpodTemplate.SERVER, ServerpodTemplate.MINI, ServerpodTemplate.MODULE),
            ServerpodTemplate.availableFor(three),
        )
    }

    @Test
    fun `swaps mini for fullstack on Serverpod 4`() {
        assertEquals(
            listOf(ServerpodTemplate.FULLSTACK, ServerpodTemplate.SERVER, ServerpodTemplate.MODULE),
            ServerpodTemplate.availableFor(four),
        )
    }

    @Test
    fun `follows the CLI's own default`() {
        assertEquals(ServerpodTemplate.SERVER, ServerpodTemplate.defaultFor(three))
        assertEquals(ServerpodTemplate.FULLSTACK, ServerpodTemplate.defaultFor(four))
    }

    @Test
    fun `keeps a remembered choice the CLI still accepts`() {
        assertEquals(ServerpodTemplate.MODULE, ServerpodTemplate.restore("module", four))
        assertEquals(ServerpodTemplate.MINI, ServerpodTemplate.restore("mini", three))
    }

    @Test
    fun `replaces a remembered choice the CLI has dropped`() {
        assertEquals(ServerpodTemplate.FULLSTACK, ServerpodTemplate.restore("mini", four))
        assertEquals(ServerpodTemplate.SERVER, ServerpodTemplate.restore("fullstack", three))
        assertEquals(ServerpodTemplate.SERVER, ServerpodTemplate.restore(null, three))
    }

    @Test
    fun `describes server differently once it stops meaning everything`() {
        assertNotEquals(
            ServerpodTemplate.SERVER.descriptionFor(three),
            ServerpodTemplate.SERVER.descriptionFor(four),
        )
    }
}
