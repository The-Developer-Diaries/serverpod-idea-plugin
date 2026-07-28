package dev.serverpod.idea.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.serverpod.idea.project.ServerpodProjectService

class ServerpodToolWindowFactory : ToolWindowFactory, DumbAware {

    /**
     * The tool window stays registered in every project so that it can appear the
     * moment a Serverpod workspace shows up, but the stripe button is hidden
     * until then.
     */
    override fun shouldBeAvailable(project: Project): Boolean =
        ServerpodProjectService.getInstance(project).layout() != null

    override fun init(toolWindow: ToolWindow) {
        val project = toolWindow.project

        project.messageBus
            .connect(toolWindow.disposable)
            .subscribe(
                ServerpodProjectService.TOPIC,
                ServerpodProjectService.LayoutListener { layout ->
                    ApplicationManager.getApplication().invokeLater(
                        { toolWindow.isAvailable = layout != null },
                        project.disposed,
                    )
                },
            )
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ServerpodPanel(project, toolWindow.disposable)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        const val ID = "Serverpod"
    }
}
