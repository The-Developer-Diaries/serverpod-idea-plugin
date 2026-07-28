package dev.serverpod.idea.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import dev.serverpod.idea.cli.ServerpodCommand
import dev.serverpod.idea.project.ServerpodLayout
import dev.serverpod.idea.project.ServerpodProjectService

/**
 * Offers whatever the project defines under `serverpod/scripts`, so the menu
 * grows with the workspace instead of being fixed at build time.
 */
class ServerpodScriptsActionGroup : ActionGroup(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val layout = ServerpodProjectService.layoutOf(e.project)
        e.presentation.isEnabledAndVisible = layout != null && layout.scripts.isNotEmpty()
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val layout = ServerpodProjectService.layoutOf(e?.project) ?: return EMPTY_ARRAY

        return layout.scripts.map(::RunScriptAction).toTypedArray()
    }

    private class RunScriptAction(private val script: String) : ServerpodAction() {

        init {
            templatePresentation.text = script
            templatePresentation.description = "Run the $script script defined in pubspec.yaml"
        }

        override fun command(layout: ServerpodLayout) = ServerpodCommand.runScript(layout, script)
    }
}
