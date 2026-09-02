package dev.serverpod.idea.project

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Watches for the two kinds of change worth reacting to: a file that could alter
 * the workspace shape, and a model the generated code is derived from. Most VFS
 * traffic is neither, so the filter is deliberately narrow.
 */
class ServerpodVfsListener(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) : AsyncFileListener {

    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        if (project.isDisposed) return null
        val basePath = project.basePath ?: return null
        val changes = classify(events, basePath) ?: return null

        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                // This callback runs under the VFS write action, so only enqueue the
                // existing background refresh and generation decisions here.
                coroutineScope.launch {
                    if (project.isDisposed) return@launch

                    if (changes.layoutChanged) {
                        ServerpodProjectService.getInstance(project).scheduleRefresh()
                    }
                    if (changes.modelsChanged) {
                        ServerpodModelWatcher.getInstance(project).onModelFilesChanged()
                    }
                }
            }
        }
    }

    private fun classify(events: List<VFileEvent>, basePath: String): RelevantChanges? =
        classifyPaths(
            events.map { event ->
                ProgressManager.checkCanceled()
                event.path
            },
            basePath,
        )

    companion object {
        /**
         * Narrows VFS paths to the project tree before applying the model and
         * workspace-layout rules. Kept separate from VFS events for focused tests.
         */
        internal fun classifyPaths(paths: Iterable<String>, basePath: String): RelevantChanges? {
            val normalizedBasePath = normalizePath(basePath)
            var layoutChanged = false
            var modelsChanged = false

            for (path in paths) {
                val relativePath = relativePath(path, normalizedBasePath) ?: continue

                if (ServerpodModelWatcher.isModelFile(relativePath)) {
                    modelsChanged = true
                } else if (changesLayout(relativePath)) {
                    layoutChanged = true
                }

                if (layoutChanged && modelsChanged) break
            }

            return RelevantChanges(layoutChanged, modelsChanged).takeIf {
                it.layoutChanged || it.modelsChanged
            }
        }

        private fun relativePath(path: String, basePath: String): String? {
            val normalizedPath = normalizePath(path)
            if (basePath == "/") return normalizedPath.removePrefix("/")
            if (normalizedPath == basePath) return ""
            if (!normalizedPath.startsWith("$basePath/")) return null
            return normalizedPath.removePrefix("$basePath/")
        }

        private fun normalizePath(path: String): String =
            path.replace('\\', '/').trimEnd('/').ifEmpty { "/" }

        private fun changesLayout(relativePath: String): Boolean =
            relativePath.endsWith("pubspec.yaml") ||
                relativePath.endsWith("generator.yaml") ||
                relativePath.endsWith("docker-compose.yaml") ||
                relativePath.endsWith("docker-compose.yml") ||
                // A direct child of the project root, so a package appearing or going away.
                relativePath.contains('/').not()

        internal data class RelevantChanges(
            val layoutChanged: Boolean,
            val modelsChanged: Boolean,
        )
    }
}
