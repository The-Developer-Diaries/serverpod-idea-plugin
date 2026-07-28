package dev.serverpod.idea.project

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ServerpodScriptsTest {

    @TempDir
    lateinit var tempDir: Path

    /** Shaped after a generated pubspec, where a script is a string or a per-OS map. */
    @Test
    fun `reads script names whatever shape the body takes`() {
        val names = namesIn(
            """
            name: demoapp_server
            dependencies:
              serverpod: 3.4.11

            serverpod:
              scripts:
                # Starts the server and applies migrations
                start: dart bin/main.dart --apply-migrations
                test: dart test
                flutter_build:
                  windows: >-
                    (if exist web\app rmdir /S /Q web\app)
                    & cd /D ..\demoapp_flutter
                  default: cd ../demoapp_flutter && flutter build web
            """.trimIndent()
        )

        assertEquals(listOf("start", "test", "flutter_build"), names)
    }

    @Test
    fun `reports nothing when the pubspec has no scripts`() {
        assertEquals(emptyList<String>(), namesIn("name: demoapp_server\n"))
        assertEquals(emptyList<String>(), namesIn("name: demoapp_server\nserverpod:\n  other: value\n"))
    }

    @Test
    fun `reports nothing for a pubspec that is missing or unreadable`() {
        assertEquals(emptyList<String>(), ServerpodScripts.namesIn(tempDir.resolve("absent.yaml")))

        // A half-typed file should not take the menu down with it.
        assertEquals(emptyList<String>(), namesIn("serverpod:\n  scripts:\n    start: [unclosed\n"))
    }

    private fun namesIn(pubspec: String): List<String> {
        val file = Files.createTempFile(tempDir, "pubspec", ".yaml")
        Files.writeString(file, pubspec)
        return ServerpodScripts.namesIn(file)
    }
}
