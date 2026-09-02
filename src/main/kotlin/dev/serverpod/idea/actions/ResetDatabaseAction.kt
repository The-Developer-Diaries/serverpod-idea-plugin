package dev.serverpod.idea.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.MessageDialogBuilder
import dev.serverpod.idea.cli.CliTool
import dev.serverpod.idea.cli.ServerpodCommand
import dev.serverpod.idea.cli.ServerpodCommandRunner
import dev.serverpod.idea.project.ServerpodProjectService
import kotlinx.coroutines.launch

/**
 * Recreates the Compose volumes.
 *
 * PostgreSQL only applies `POSTGRES_PASSWORD` when it initialises an empty data
 * directory, so a volume left over from an earlier project of the same name keeps
 * its original password and rejects the one in `config/passwords.yaml`.
 */
class ResetDatabaseAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ServerpodProjectService.layoutOf(e.project)?.hasDocker == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val layout = ServerpodProjectService.layoutOf(project) ?: return

        val confirmed = MessageDialogBuilder
            .yesNo(
                "Reset Serverpod Containers?",
                "This deletes the PostgreSQL and Redis volumes for '${layout.projectName}' and recreates " +
                    "the containers.\n\n" +
                    "Everything in the development and test databases will be lost. Use this when the " +
                    "containers were initialised with a different password than the one now in " +
                    "config/passwords.yaml, which shows up as 'password authentication failed'.",
            )
            .yesText("Reset")
            .noText("Cancel")
            .asWarning()
            .ask(project)

        if (!confirmed) return

        e.coroutineScope.launch {
            ServerpodCommandRunner.runSequence(
                project,
                "Resetting Serverpod containers",
                listOf(
                    ServerpodCommand(
                        title = "docker compose down --volumes",
                        tool = CliTool.DOCKER,
                        workDir = layout.serverDir,
                        arguments = listOf("compose", "down", "--volumes"),
                    ),
                    ServerpodCommand(
                        title = "docker compose up --detach",
                        tool = CliTool.DOCKER,
                        workDir = layout.serverDir,
                        arguments = listOf("compose", "up", "--detach"),
                    ),
                ),
                successMessage = "Recreated the containers using the passwords in config/passwords.yaml.",
            )
        }
    }
}
