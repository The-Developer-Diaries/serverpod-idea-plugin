package dev.serverpod.idea.cli

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import dev.serverpod.idea.ServerpodNotifications
import dev.serverpod.idea.project.ServerpodProjectService

object ServerpodCommandRunner {

    private const val POLL_INTERVAL_MS = 200L

    const val FAILED_TO_START = -1
    const val CANCELLED = -2

    /**
     * Runs [command] in the background, streaming output to the Serverpod tool
     * window. [onSuccess] is invoked on a background thread only when the process
     * exits with code 0; [onFinished] always is, so a caller can release a lock.
     */
    fun run(
        project: Project,
        command: ServerpodCommand,
        onSuccess: (() -> Unit)? = null,
        onFinished: ((Int) -> Unit)? = null,
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, command.title, true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                var exitCode = FAILED_TO_START
                try {
                    exitCode = runSync(project, command, indicator)
                    refreshWorkspace(project)

                    when {
                        exitCode == 0 -> {
                            command.successMessage?.let { ServerpodNotifications.info(project, command.title, it) }
                            onSuccess?.invoke()
                        }

                        exitCode != CANCELLED -> reportFailure(project, command, exitCode)
                    }
                } finally {
                    onFinished?.invoke(exitCode)
                }
            }
        })
    }

    /**
     * Runs [commands] in order in a single background task, stopping at the
     * first failure so a broken step never hides behind a later one.
     */
    fun runSequence(
        project: Project,
        title: String,
        commands: List<ServerpodCommand>,
        successMessage: String? = null,
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                for (command in commands) {
                    indicator.text = command.title

                    val exitCode = runSync(project, command, indicator)
                    if (exitCode != 0) {
                        refreshWorkspace(project)
                        if (exitCode != CANCELLED) reportFailure(project, command, exitCode)
                        return
                    }
                }

                refreshWorkspace(project)
                successMessage?.let { ServerpodNotifications.info(project, title, it) }
            }
        })
    }

    /**
     * Runs [command] on the calling thread and returns its exit code, or
     * [FAILED_TO_START] / [CANCELLED]. Must not be called on the EDT.
     */
    fun runSync(project: Project, command: ServerpodCommand, indicator: ProgressIndicator?): Int {
        val commandLine = command.toCommandLine() ?: run {
            ServerpodNotifications.missingTool(project, command.tool)
            return FAILED_TO_START
        }

        val console = ServerpodConsoleService.getInstance(project)
        console.beginCommand(command.title, commandLine.commandLineString)

        val handler = try {
            OSProcessHandler(commandLine)
        } catch (e: ExecutionException) {
            val message = e.message ?: "Failed to start the process."
            console.println(message, ConsoleViewContentType.ERROR_OUTPUT)
            ServerpodNotifications.error(project, command.title, message)
            return FAILED_TO_START
        }

        console.attachToProcess(handler)
        handler.startNotify()

        while (!handler.waitFor(POLL_INTERVAL_MS)) {
            if (indicator?.isCanceled == true) {
                handler.destroyProcess()
                console.println("Cancelled.", ConsoleViewContentType.SYSTEM_OUTPUT)
                return CANCELLED
            }
        }

        val exitCode = handler.exitCode ?: FAILED_TO_START
        console.endCommand(command.title, exitCode)
        return exitCode
    }

    fun reportFailure(project: Project, command: ServerpodCommand, exitCode: Int) {
        ServerpodNotifications.error(
            project,
            command.title,
            "Exited with code $exitCode. See the Serverpod tool window for the full output.",
        )
    }

    fun refreshWorkspace(project: Project) {
        if (project.isDisposed) return

        project.basePath
            ?.let { LocalFileSystem.getInstance().refreshAndFindFileByPath(it) }
            ?.let { VfsUtil.markDirtyAndRefresh(false, true, true, it) }

        ServerpodProjectService.getInstance(project).detectNow()
    }
}
