package dev.serverpod.idea.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

/**
 * Watches for the two kinds of change worth reacting to: a file that could alter
 * the workspace shape, and a model the generated code is derived from. Most VFS
 * traffic is neither, so the filter is deliberately narrow.
 */
class ServerpodVfsListener(private val project: Project) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        val basePath = project.basePath ?: return

        var layoutChanged = false
        var modelsChanged = false

        for (event in events) {
            val path = event.path
            if (!path.startsWith(basePath)) continue

            if (ServerpodModelWatcher.isModelFile(path)) {
                modelsChanged = true
            } else if (changesLayout(path, basePath)) {
                layoutChanged = true
            }

            if (layoutChanged && modelsChanged) break
        }

        if (layoutChanged) ServerpodProjectService.getInstance(project).scheduleRefresh()
        if (modelsChanged) ServerpodModelWatcher.getInstance(project).onModelFilesChanged()
    }

    private fun changesLayout(path: String, basePath: String): Boolean =
        path.endsWith("pubspec.yaml") ||
            path.endsWith("generator.yaml") ||
            path.endsWith("docker-compose.yaml") ||
            path.endsWith("docker-compose.yml") ||
            // A direct child of the project root, so a package appearing or going away.
            path.substringAfter(basePath).trim('/').contains('/').not()
}
