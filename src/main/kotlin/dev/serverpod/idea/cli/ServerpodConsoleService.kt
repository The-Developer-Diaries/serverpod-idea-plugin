package dev.serverpod.idea.cli

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import dev.serverpod.idea.toolwindow.ServerpodToolWindowFactory

/**
 * Owns the single console shown in the Serverpod tool window. Every command the
 * plugin runs streams here so users can see exactly what was executed.
 */
@Service(Service.Level.PROJECT)
class ServerpodConsoleService(private val project: Project) : Disposable {

    private val lock = Any()
    private var consoleView: ConsoleView? = null

    val console: ConsoleView
        get() {
            synchronized(lock) { consoleView?.let { return it } }

            return computeOnEdt {
                synchronized(lock) {
                    consoleView ?: TextConsoleBuilderFactory.getInstance()
                        .createBuilder(project)
                        .console
                        .also {
                            Disposer.register(this, it)
                            consoleView = it
                        }
                }
            }
        }

    fun beginCommand(title: String, commandLine: String) {
        activateToolWindow()
        console.print("\n$title\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        console.print("> $commandLine\n\n", ConsoleViewContentType.USER_INPUT)
    }

    fun endCommand(title: String, exitCode: Int) {
        val type = if (exitCode == 0) ConsoleViewContentType.SYSTEM_OUTPUT else ConsoleViewContentType.ERROR_OUTPUT
        val outcome = if (exitCode == 0) "finished successfully" else "failed with exit code $exitCode"
        console.print("\n$title $outcome.\n", type)
    }

    fun println(text: String, type: ConsoleViewContentType = ConsoleViewContentType.SYSTEM_OUTPUT) {
        console.print("$text\n", type)
    }

    fun attachToProcess(handler: ProcessHandler) {
        console.attachToProcess(handler)
    }

    fun clear() {
        console.clear()
    }

    private fun activateToolWindow() {
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            ToolWindowManager.getInstance(project)
                .getToolWindow(ServerpodToolWindowFactory.ID)
                ?.show(null)
        }, project.disposed)
    }

    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): ServerpodConsoleService = project.service()
    }
}
