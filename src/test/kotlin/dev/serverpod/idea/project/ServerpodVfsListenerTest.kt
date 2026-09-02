package dev.serverpod.idea.project

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerpodVfsListenerTest {

    private val projectRoot = "/projects/demo"

    @Test
    fun `classifies model and workspace layout paths inside the project`() {
        val changes = requireNotNull(
            ServerpodVfsListener.classifyPaths(
                listOf(
                    "$projectRoot/demo_server/lib/src/protocol/user.spy.yaml",
                    "$projectRoot/demo_server/docker-compose.yml",
                ),
                projectRoot,
            ),
        )

        assertTrue(changes.modelsChanged)
        assertTrue(changes.layoutChanged)
    }

    @Test
    fun `ignores ordinary nested files`() {
        assertNull(
            ServerpodVfsListener.classifyPaths(
                listOf(
                    "$projectRoot/demo_server/lib/main.dart",
                    "$projectRoot/demo_server/lib/src/protocol/user.yaml",
                    "$projectRoot/.idea/workspace.xml",
                ),
                projectRoot,
            ),
        )
    }

    @Test
    fun `does not cross a project path prefix boundary`() {
        assertNull(
            ServerpodVfsListener.classifyPaths(
                listOf(
                    "$projectRoot-copy/demo_server/lib/src/protocol/user.spy.yaml",
                    "$projectRoot-backup/docker-compose.yml",
                ),
                projectRoot,
            ),
        )
    }

    @Test
    fun `recognizes direct children with a trailing root separator`() {
        val changes = requireNotNull(
            ServerpodVfsListener.classifyPaths(
                listOf("$projectRoot/new-package"),
                "$projectRoot/",
            ),
        )

        assertTrue(changes.layoutChanged)
        assertFalse(changes.modelsChanged)
    }
}
