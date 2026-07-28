package dev.serverpod.idea.wizard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ServerpodNamingTest {

    @Test
    fun `suggests a dart package name from a display name`() {
        assertEquals("my_app", ServerpodNaming.suggestPackageName("My App"))
        assertEquals("my_app", ServerpodNaming.suggestPackageName("  My   App  "))
        assertEquals("todo_list", ServerpodNaming.suggestPackageName("Todo-List"))
        assertEquals("untitled", ServerpodNaming.suggestPackageName("untitled"))
    }

    @Test
    fun `prefixes names that would start with a digit`() {
        assertEquals("app_2048", ServerpodNaming.suggestPackageName("2048"))
    }

    @Test
    fun `falls back when nothing usable remains`() {
        assertEquals("my_app", ServerpodNaming.suggestPackageName("---"))
        assertEquals("my_app", ServerpodNaming.suggestPackageName(""))
    }

    @Test
    fun `accepts valid package names`() {
        assertNull(ServerpodNaming.packageNameError("blog"))
        assertNull(ServerpodNaming.packageNameError("my_app2"))
    }

    @Test
    fun `rejects names serverpod or dart would refuse`() {
        assertNotNull(ServerpodNaming.packageNameError(""))
        assertNotNull(ServerpodNaming.packageNameError("MyApp"))
        assertNotNull(ServerpodNaming.packageNameError("my-app"))
        assertNotNull(ServerpodNaming.packageNameError("2048"))
        assertNotNull(ServerpodNaming.packageNameError("class"))
        assertNotNull(ServerpodNaming.packageNameError("blog_server"))
    }
}
