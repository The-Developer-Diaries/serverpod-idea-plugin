package dev.serverpod.idea.wizard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class WorkspaceRelocatorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `moves a generated workspace into the project directory`() {
        val scratch = generatedWorkspace("demoapp")
        val projectRoot = newFolder("project")

        WorkspaceRelocator.mergeInto(scratch, projectRoot)

        assertEquals("void main() {}", Files.readString(projectRoot.resolve("demoapp_server/bin/main.dart")))
        assertTrue(Files.isDirectory(projectRoot.resolve("demoapp_client")))
        assertTrue(Files.isRegularFile(projectRoot.resolve("pubspec.yaml")))
    }

    @Test
    fun `leaves files the IDE already created in place`() {
        val scratch = generatedWorkspace("demoapp")
        val projectRoot = newFolder("project")
        Files.createDirectories(projectRoot.resolve(".idea"))
        Files.writeString(projectRoot.resolve(".idea/misc.xml"), "<project/>")

        WorkspaceRelocator.mergeInto(scratch, projectRoot)

        assertEquals("<project/>", Files.readString(projectRoot.resolve(".idea/misc.xml")))
        assertTrue(Files.isDirectory(projectRoot.resolve("demoapp_server")))
    }

    @Test
    fun `merges into directories that already exist`() {
        val scratch = generatedWorkspace("demoapp")
        val projectRoot = newFolder("project")
        Files.createDirectories(projectRoot.resolve("demoapp_server/bin"))
        Files.writeString(projectRoot.resolve("demoapp_server/keep.txt"), "keep me")

        WorkspaceRelocator.mergeInto(scratch, projectRoot)

        assertEquals("keep me", Files.readString(projectRoot.resolve("demoapp_server/keep.txt")))
        assertEquals("void main() {}", Files.readString(projectRoot.resolve("demoapp_server/bin/main.dart")))
    }

    @Test
    fun `removes the scratch directory once everything has moved`() {
        val scratch = generatedWorkspace("demoapp")
        val projectRoot = newFolder("project")

        WorkspaceRelocator.mergeInto(scratch, projectRoot)

        assertFalse(Files.exists(scratch))
    }

    @Test
    fun `fails loudly when the CLI produced nothing`() {
        val projectRoot = newFolder("project")

        assertThrows<IOException> {
            WorkspaceRelocator.mergeInto(projectRoot.resolve("never_created"), projectRoot)
        }
    }

    @Test
    fun `clears pub caches at the workspace root and in every package`() {
        val projectRoot = newFolder("project")
        Files.createDirectories(projectRoot.resolve(".dart_tool"))
        Files.writeString(projectRoot.resolve(".dart_tool/package_config.json"), "{}")
        Files.createDirectories(projectRoot.resolve("demoapp_server/.dart_tool"))
        Files.writeString(projectRoot.resolve("demoapp_server/pubspec.yaml"), "name: demoapp_server")

        WorkspaceRelocator.deleteStalePubCaches(projectRoot)

        assertFalse(Files.exists(projectRoot.resolve(".dart_tool")))
        assertFalse(Files.exists(projectRoot.resolve("demoapp_server/.dart_tool")))
        assertTrue(Files.isRegularFile(projectRoot.resolve("demoapp_server/pubspec.yaml")))
    }

    private fun newFolder(name: String): Path = Files.createDirectories(tempDir.resolve(name))

    private fun generatedWorkspace(name: String): Path {
        val scratch = newFolder("scratch").resolve(name)
        Files.createDirectories(scratch.resolve("${name}_server/bin"))
        Files.writeString(scratch.resolve("${name}_server/bin/main.dart"), "void main() {}")
        Files.createDirectories(scratch.resolve("${name}_client/lib"))
        Files.writeString(scratch.resolve("pubspec.yaml"), "name: _")
        return scratch
    }
}
