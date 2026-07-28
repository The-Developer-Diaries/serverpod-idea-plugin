package dev.serverpod.idea.project

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ServerpodLayoutTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `detects a full server workspace from its root`() {
        val root = workspace("demoapp", withDocker = true, withMigrations = true, withFlutter = true)

        val layout = ServerpodLayout.detect(root)!!

        assertEquals("demoapp", layout.projectName)
        assertEquals(root, layout.root)
        assertEquals(root.resolve("demoapp_server"), layout.serverDir)
        assertEquals(root.resolve("demoapp_client"), layout.clientDir)
        assertEquals(root.resolve("demoapp_flutter"), layout.flutterDir)
        assertTrue(layout.hasDocker)
        assertTrue(layout.hasMigrations)
    }

    @Test
    fun `detects the workspace when the server package itself is opened`() {
        val root = workspace("demoapp", withDocker = true, withMigrations = true, withFlutter = true)

        val layout = ServerpodLayout.detect(root.resolve("demoapp_server"))!!

        assertEquals("demoapp", layout.projectName)
        assertEquals(root, layout.root)
    }

    @Test
    fun `reports the optional pieces a mini project does not have`() {
        val root = workspace("tiny", withDocker = false, withMigrations = false, withFlutter = false)

        val layout = ServerpodLayout.detect(root)!!

        assertNull(layout.flutterDir)
        assertFalse(layout.hasDocker)
        assertFalse(layout.hasMigrations)
        assertNotNull(layout.clientDir)
    }

    @Test
    fun `falls back to the pubspec when generator config is absent`() {
        val root = newFolder("fallback")
        val server = Files.createDirectories(root.resolve("fallback_server"))
        Files.writeString(server.resolve("pubspec.yaml"), "name: fallback_server\ndependencies:\n  serverpod: ^3.0.0\n")

        assertNotNull(ServerpodLayout.detect(root))
    }

    @Test
    fun `ignores directories that only look like a server package`() {
        val root = newFolder("decoy")
        val server = Files.createDirectories(root.resolve("decoy_server"))
        Files.writeString(server.resolve("pubspec.yaml"), "name: decoy_server\ndependencies:\n  shelf: ^1.0.0\n")

        assertNull(ServerpodLayout.detect(root))
    }

    @Test
    fun `picks up the scripts declared by the server package`() {
        val root = workspace("scripted", withDocker = false, withMigrations = false, withFlutter = false)
        Files.writeString(
            root.resolve("scripted_server/pubspec.yaml"),
            "name: scripted_server\nserverpod:\n  scripts:\n    start: dart bin/main.dart\n",
        )

        assertEquals(listOf("start"), ServerpodLayout.detect(root)!!.scripts)
    }

    @Test
    fun `ignores a plain directory`() {
        assertNull(ServerpodLayout.detect(newFolder("empty")))
    }

    private fun workspace(
        name: String,
        withDocker: Boolean,
        withMigrations: Boolean,
        withFlutter: Boolean,
    ): Path {
        val root = newFolder(name)
        Files.writeString(root.resolve("pubspec.yaml"), "name: _\nworkspace:\n  - ${name}_server\n")

        val server = Files.createDirectories(root.resolve("${name}_server"))
        Files.writeString(server.resolve("pubspec.yaml"), "name: ${name}_server\n")
        Files.createDirectories(server.resolve("config"))
        Files.writeString(server.resolve("config/generator.yaml"), "type: server\n")
        Files.createDirectories(server.resolve("bin"))
        Files.writeString(server.resolve("bin/main.dart"), "void main() {}\n")

        Files.createDirectories(root.resolve("${name}_client"))
        if (withFlutter) Files.createDirectories(root.resolve("${name}_flutter"))
        if (withMigrations) Files.createDirectories(server.resolve("migrations"))
        if (withDocker) Files.writeString(server.resolve("docker-compose.yaml"), "services: {}\n")

        return root
    }

    private fun newFolder(name: String): Path = Files.createDirectories(tempDir.resolve(name))
}
