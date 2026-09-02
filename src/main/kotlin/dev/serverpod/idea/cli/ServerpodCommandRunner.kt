package dev.serverpod.idea.cli

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import dev.serverpod.idea.ServerpodNotifications
import dev.serverpod.idea.project.ServerpodProjectService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

object ServerpodCommandRunner {

    const val FAILED_TO_START = -1
    const val CANCELLED = -2

    /**
     * Runs [command], streaming output to the Serverpod tool window. The caller
     * supplies the coroutine lifetime. [onSuccess] is invoked on a background
     * thread only when the process exits with code 0; [onFinished] always is, so
     * a caller can release a lock.
     */
    suspend fun run(
        project: Project,
        command: ServerpodCommand,
        onSuccess: (() -> Unit)? = null,
        onFinished: ((Int) -> Unit)? = null,
    ) {
        var exitCode = FAILED_TO_START
        try {
            withBackgroundProgress(project, command.title) {
                try {
                    exitCode = runSync(project, command)
                } finally {
                    refreshWorkspaceAfterCommand(project)
                }

                when {
                    exitCode == 0 -> {
                        command.successMessage?.let { ServerpodNotifications.info(project, command.title, it) }
                        onSuccess?.invoke()
                    }

                    exitCode != CANCELLED -> reportFailure(project, command, exitCode)
                }
            }
        } catch (e: CancellationException) {
            exitCode = CANCELLED
            throw e
        } finally {
            onFinished?.invoke(exitCode)
        }
    }

    /**
     * Runs [commands] in order in a single background coroutine, stopping at the
     * first failure so a broken step never hides behind a later one.
     */
    suspend fun runSequence(
        project: Project,
        title: String,
        commands: List<ServerpodCommand>,
        successMessage: String? = null,
    ) {
        withBackgroundProgress(project, title) {
            try {
                for (command in commands) {
                    val exitCode = runSync(project, command)
                    if (exitCode != 0) {
                        if (exitCode != CANCELLED) reportFailure(project, command, exitCode)
                        return@withBackgroundProgress
                    }
                }

                successMessage?.let { ServerpodNotifications.info(project, title, it) }
            } finally {
                refreshWorkspaceAfterCommand(project)
            }
        }
    }

    /**
     * Runs [command] on the calling coroutine and returns its exit code, or
     * [FAILED_TO_START]. Cancellation destroys the process and is propagated to
     * the caller.
     */
    suspend fun runSync(project: Project, command: ServerpodCommand): Int = withContext(Dispatchers.IO) {
        val commandLine = command.toCommandLine() ?: run {
            ServerpodNotifications.missingTool(project, command.tool)
            return@withContext FAILED_TO_START
        }

        val console = ServerpodConsoleService.getInstance(project)
        console.beginCommand(command.title, commandLine.commandLineString)

        val handler = try {
            OSProcessHandler(commandLine)
        } catch (e: ExecutionException) {
            val message = e.message ?: "Failed to start the process."
            console.println(message, ConsoleViewContentType.ERROR_OUTPUT)
            ServerpodNotifications.error(project, command.title, message)
            return@withContext FAILED_TO_START
        }

        console.attachToProcess(handler)
        handler.startNotify()

        try {
            val exitCode = handler.awaitTermination()
            console.endCommand(command.title, exitCode)
            exitCode
        } catch (e: CancellationException) {
            console.println("Cancelled.", ConsoleViewContentType.SYSTEM_OUTPUT)
            throw e
        }
    }

    fun reportFailure(project: Project, command: ServerpodCommand, exitCode: Int) {
        ServerpodNotifications.error(
            project,
            command.title,
            "Exited with code $exitCode. See the Serverpod tool window for the full output.",
        )
    }

    suspend fun refreshWorkspace(project: Project) {
        withContext(Dispatchers.IO) {
            if (project.isDisposed) return@withContext

            project.basePath
                ?.let { LocalFileSystem.getInstance().refreshAndFindFileByPath(it) }
                ?.let { VfsUtil.markDirtyAndRefresh(false, true, true, it) }

            ServerpodProjectService.getInstance(project).detectNow()
        }
    }

    private suspend fun refreshWorkspaceAfterCommand(project: Project) {
        withContext(NonCancellable) {
            refreshWorkspace(project)
        }
    }
}

internal data class CapturedProcessOutput(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/** Starts [commandLine] and captures its output without synchronously waiting on a worker. */
internal suspend fun captureProcess(commandLine: GeneralCommandLine): CapturedProcessOutput =
    withContext(Dispatchers.IO) {
        val stdout = StringBuffer()
        val stderr = StringBuffer()
        val handler = OSProcessHandler(commandLine)
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                when (outputType) {
                    ProcessOutputTypes.STDOUT -> stdout.append(event.text)
                    ProcessOutputTypes.STDERR -> stderr.append(event.text)
                }
            }
        })
        handler.startNotify()

        CapturedProcessOutput(handler.awaitTermination(), stdout.toString(), stderr.toString())
    }

/**
 * Waits for process-handler completion through its listener API. If the owning
 * coroutine is cancelled, the child process is destroyed without polling.
 */
internal suspend fun ProcessHandler.awaitTermination(): Int =
    try {
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            lateinit var listener: ProcessListener

            fun complete(exitCode: Int) {
                if (completed.compareAndSet(false, true)) {
                    removeProcessListener(listener)
                    continuation.resume(exitCode)
                }
            }

            listener = object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) = complete(event.exitCode)
            }
            addProcessListener(listener)

            if (isProcessTerminated) complete(exitCode ?: ServerpodCommandRunner.FAILED_TO_START)

            continuation.invokeOnCancellation {
                if (completed.compareAndSet(false, true)) removeProcessListener(listener)
            }
        }
    } catch (e: CancellationException) {
        if (!isProcessTerminated) destroyProcess()
        throw e
    }
