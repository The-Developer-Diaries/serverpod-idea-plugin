package dev.serverpod.idea.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import dev.serverpod.idea.cli.CliVersions
import dev.serverpod.idea.cli.ServerpodCommand
import dev.serverpod.idea.cli.ServerpodCommandRunner
import dev.serverpod.idea.cli.ServerpodFeature
import dev.serverpod.idea.cli.ServerpodIde
import dev.serverpod.idea.project.ServerpodProjectService
import kotlinx.coroutines.launch
import javax.swing.JComponent

/**
 * Installs Serverpod 4's agent skills and registers its MCP servers, by
 * re-running `serverpod create` over the existing project.
 *
 * That is the route the Serverpod docs prescribe, and it keeps the plugin out of
 * the business of writing each editor's config format. It also means the command
 * can overwrite files the user wrote by hand, so it asks first and names them.
 */
class InstallAgentToolingAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ServerpodProjectService.layoutOf(e.project) != null &&
            CliVersions.getInstance().supports(ServerpodFeature.AGENT_TOOLING)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val layout = ServerpodProjectService.layoutOf(project) ?: return

        val dialog = EditorSelectionDialog(project)
        if (!dialog.showAndGet()) return

        val selected = dialog.selectedIdes()
        if (selected.isEmpty()) return

        e.coroutineScope.launch {
            ServerpodCommandRunner.run(project, ServerpodCommand.installAgentTooling(layout, selected))
        }
    }

    private class EditorSelectionDialog(project: Project) : DialogWrapper(project) {

        private val selections = ServerpodIde.entries
            .filter { it != ServerpodIde.NONE }
            .associateWith { it in ServerpodIde.DEFAULTS }
            .toMutableMap()

        init {
            title = "Install Serverpod Agent Skills"
            setOKButtonText("Install")
            init()
        }

        fun selectedIdes(): List<ServerpodIde> = selections.filterValues { it }.keys.toList()

        override fun createCenterPanel(): JComponent = panel {
            row {
                text(
                    "Runs <code>serverpod create .</code>, which installs the Serverpod agent skills " +
                        "and registers the Serverpod and Dart MCP servers for the editors below.",
                )
            }
            row {
                text(
                    "Your source code is left alone, but each editor's own configuration file " +
                        "(for example <code>.mcp.json</code> or <code>.cursor/mcp.json</code>) is rewritten. " +
                        "Commit anything you hand-edited there first.",
                )
            }

            group("Editors") {
                selections.keys.forEach { ide ->
                    row {
                        checkBox(ide.displayName).applyToComponent {
                            isSelected = selections.getValue(ide)
                            addActionListener { selections[ide] = isSelected }
                        }
                    }
                }
            }
        }
    }
}
