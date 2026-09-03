package dev.serverpod.idea.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import dev.serverpod.idea.project.ServerpodProjectService
import dev.serverpod.idea.run.ServerpodLaunchMode
import dev.serverpod.idea.run.ServerpodRunConfigurations

/**
 * Runs the server on its own. On Serverpod 4 this is the narrow counterpart to
 * `serverpod start`: the entry point only, without code generation, the Flutter
 * apps, or a watch loop.
 */
class RunServerAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ServerpodProjectService.layoutOf(e.project) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val layout = ServerpodProjectService.layoutOf(project) ?: return

        ServerpodRunConfigurations.runServer(project, layout, ServerpodLaunchMode.ENTRY_POINT)
    }
}
