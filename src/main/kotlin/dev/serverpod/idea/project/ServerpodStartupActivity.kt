package dev.serverpod.idea.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.serverpod.idea.ServerpodNotifications
import dev.serverpod.idea.wizard.ServerpodProjectGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ServerpodStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        // A project the wizard just created has nothing on disk to detect yet, and
        // this is the first point at which the CLI can run against it.
        if (ServerpodProjectGenerator.runPending(project)) return

        val layout = withContext(Dispatchers.IO) {
            ServerpodProjectService.getInstance(project).detectNow()
        } ?: return

        // A workspace cloned from source, or created before the plugin handled
        // this, arrives without an SDK and so without any analysis.
        if (ServerpodDartSupport.isConfigured(project)) return

        ServerpodNotifications.actionable(
            project,
            "Dart SDK not configured",
            "This Serverpod workspace has no Dart SDK, so its code is not being analysed.",
            "Configure" to { ServerpodDartSupport.configureInBackground(project, layout) },
        )
    }
}
