package dev.serverpod.idea.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

/**
 * Re-runs layout detection when a file that could change the workspace shape
 * appears or disappears. Most VFS traffic is irrelevant here, so the filter is
 * deliberately narrow.
 */
class ServerpodVfsListener(private val project: Project) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        val basePath = project.basePath ?: return

        val relevant = events.any { event ->
            val path = event.path
            if (!path.startsWith(basePath)) return@any false

            path.endsWith("pubspec.yaml") ||
                path.endsWith("generator.yaml") ||
                path.endsWith("docker-compose.yaml") ||
                path.endsWith("docker-compose.yml") ||
                path.substringAfter(basePath).trim('/').contains('/').not()
        }

        if (relevant) {
            ServerpodProjectService.getInstance(project).scheduleRefresh()
        }
    }
}
