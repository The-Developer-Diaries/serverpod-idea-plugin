package dev.serverpod.idea.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerpodLaunchArgumentsTest {

    @Test
    fun `runs the entry point with the run mode`() {
        val arguments = ServerpodLaunchArguments.of(
            ServerpodLaunchMode.ENTRY_POINT,
            ServerpodRunMode.STAGING,
            applyMigrations = true,
        )

        assertEquals(
            listOf("run", "bin/main.dart", "--mode", "staging", "--apply-migrations"),
            arguments,
        )
    }

    @Test
    fun `passes server arguments to serverpod start after the separator`() {
        val arguments = ServerpodLaunchArguments.of(
            ServerpodLaunchMode.START,
            ServerpodRunMode.DEVELOPMENT,
            applyRepairMigration = true,
        )

        assertEquals(
            listOf(
                "--no-analytics", "--no-interactive", "start", "--no-tui",
                "--", "--mode", "development", "--apply-repair-migration",
            ),
            arguments,
        )
    }

    @Test
    fun `turns off the terminal UI, which the run console cannot render`() {
        val arguments = ServerpodLaunchArguments.of(ServerpodLaunchMode.START, ServerpodRunMode.DEVELOPMENT)

        assertTrue(arguments.contains("--no-tui"))
    }

    @Test
    fun `opts out of the Flutter apps only when asked`() {
        val withApps = ServerpodLaunchArguments.of(
            ServerpodLaunchMode.START,
            ServerpodRunMode.DEVELOPMENT,
            launchFlutterApps = true,
        )
        val withoutApps = ServerpodLaunchArguments.of(
            ServerpodLaunchMode.START,
            ServerpodRunMode.DEVELOPMENT,
            launchFlutterApps = false,
        )

        assertFalse(withApps.contains("--no-flutter"))
        assertTrue(withoutApps.contains("--no-flutter"))
        // The opt-out belongs to `start`, not to the server behind the separator.
        assertTrue(withoutApps.indexOf("--no-flutter") < withoutApps.indexOf("--"))
    }

    @Test
    fun `starts the embedded database without server flags`() {
        val arguments = ServerpodLaunchArguments.of(
            ServerpodLaunchMode.DATABASE,
            ServerpodRunMode.TEST,
            applyMigrations = true,
        )

        assertEquals(
            listOf("--no-analytics", "--no-interactive", "database", "start", "--mode", "test"),
            arguments,
        )
    }

    @Test
    fun `appends additional arguments last`() {
        val arguments = ServerpodLaunchArguments.of(
            ServerpodLaunchMode.START,
            ServerpodRunMode.DEVELOPMENT,
            extraArguments = listOf("--server-id", "1"),
        )

        assertEquals(listOf("--server-id", "1"), arguments.takeLast(2))
    }
}
