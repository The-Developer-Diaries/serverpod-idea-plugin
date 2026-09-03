package dev.serverpod.idea.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import dev.serverpod.idea.cli.CliVersions
import dev.serverpod.idea.cli.ServerpodCommand
import dev.serverpod.idea.cli.ServerpodCommandRunner
import dev.serverpod.idea.cli.ServerpodFeature
import dev.serverpod.idea.project.ServerpodLayout
import dev.serverpod.idea.project.ServerpodProjectService
import kotlinx.coroutines.launch

/**
 * Base for the actions that shell out to a CLI. Visibility is driven by the
 * cached workspace layout and the cached CLI version, so `update()` never
 * touches the file system or starts a process.
 */
abstract class ServerpodAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val layout = ServerpodProjectService.layoutOf(e.project)
        e.presentation.isEnabledAndVisible = layout != null &&
            isAvailable(layout) &&
            requiredFeature?.let { CliVersions.getInstance().supports(it) } != false
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val layout = ServerpodProjectService.layoutOf(project) ?: return

        e.coroutineScope.launch {
            ServerpodCommandRunner.run(project, command(layout))
        }
    }

    protected abstract fun command(layout: ServerpodLayout): ServerpodCommand

    protected open fun isAvailable(layout: ServerpodLayout): Boolean = true

    /** Set when the command only exists on some Serverpod releases. */
    protected open val requiredFeature: ServerpodFeature? = null
}
