package dev.serverpod.idea.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.serverpod.idea.ServerpodNotifications

class ServerpodStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val layout = ServerpodProjectService.getInstance(project).detectNow() ?: return

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
