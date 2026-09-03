package dev.serverpod.idea.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import dev.serverpod.idea.cli.CliVersions
import dev.serverpod.idea.cli.ServerpodFeature
import dev.serverpod.idea.project.ServerpodProjectService
import dev.serverpod.idea.run.ServerpodLaunchMode
import dev.serverpod.idea.run.ServerpodRunConfigurations

/**
 * Serverpod 4's `serverpod start`, which replaces the `docker compose up` plus
 * `dart bin/main.dart` plus `flutter run` sequence with one hot-reloading
 * process.
 *
 * It goes through a run configuration rather than the plugin's console because
 * it is long-lived: the Run tool window is what gives it a stop button and a
 * restart, and the console does not.
 */
class StartStackAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ServerpodProjectService.layoutOf(e.project) != null &&
            CliVersions.getInstance().supports(ServerpodFeature.START)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val layout = ServerpodProjectService.layoutOf(project) ?: return

        ServerpodRunConfigurations.runServer(project, layout, ServerpodLaunchMode.START)
    }
}

/**
 * Runs the embedded PostgreSQL on its own, for working against the database
 * without a server, or with `psql`, which needs a Dart process holding the
 * cluster open.
 */
class StartEmbeddedDatabaseAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            ServerpodProjectService.layoutOf(e.project)?.hasEmbeddedDatabase == true &&
                CliVersions.getInstance().supports(ServerpodFeature.EMBEDDED_DATABASE)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val layout = ServerpodProjectService.layoutOf(project) ?: return

        ServerpodRunConfigurations.runServer(project, layout, ServerpodLaunchMode.DATABASE)
    }
}
