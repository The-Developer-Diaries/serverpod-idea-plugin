package dev.serverpod.idea.cli

import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Installs the Serverpod CLI the way Serverpod documents it, with
 * `dart pub global activate`.
 *
 * Pub puts the binary in `~/.pub-cache/bin`, which [CliTool.SERVERPOD] already
 * searches, so the plugin finds it afterwards even when that directory is not on
 * `PATH`. Editing the user's shell profile to extend `PATH` is deliberately left
 * to them; see [PATH_HINT].
 */
object ServerpodCliInstaller {

    const val PACKAGE_NAME = "serverpod_cli"

    const val COMMAND = "dart pub global activate $PACKAGE_NAME"

    const val PATH_HINT =
        "Add <code>~/.pub-cache/bin</code> to your <code>PATH</code> to use the CLI from a terminal too."

    sealed interface Result {
        data class Success(val path: Path) : Result
        data object Cancelled : Result
        data class Failed(val message: String) : Result
    }

    /**
     * Runs the install modally, so it can be triggered from the New Project
     * wizard where no project exists yet. Must be called on the EDT.
     */
    fun install(project: Project?): Result {
        val commandLine = ServerpodCommand.commandLine(
            CliTool.DART,
            Path.of(System.getProperty("user.home")),
            listOf("pub", "global", "activate", PACKAGE_NAME),
        ) ?: return Result.Failed(
            "The Dart SDK was not found, so the CLI cannot be installed. " +
                "Install Dart or Flutter first, or set the SDK path in Settings | Tools | Serverpod.",
        )

        val outcome = try {
            runWithModalProgressBlocking(
                project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess(),
                "Installing the Serverpod CLI",
            ) {
                val output = captureProcess(commandLine)
                val installed = if (output.exitCode == 0) {
                    withContext(Dispatchers.IO) { CliTool.SERVERPOD.resolve() }
                } else {
                    null
                }
                InstallOutcome(output, installed)
            }
        } catch (_: ProcessCanceledException) {
            return Result.Cancelled
        } catch (_: CancellationException) {
            return Result.Cancelled
        } catch (e: ExecutionException) {
            return Result.Failed(e.message ?: "The process could not be started.")
        }

        if (outcome.output.exitCode != 0) {
            val details = outcome.output.stderr.ifBlank { outcome.output.stdout }.trim().takeLast(MAX_ERROR_CHARS)
            return Result.Failed(details.ifBlank { "$COMMAND exited with code ${outcome.output.exitCode}." })
        }

        // Activation can succeed while the binary still fails to resolve, for
        // example when a custom PUB_CACHE puts it somewhere we do not search.
        val installed = outcome.installed
            ?: return Result.Failed(
                "$COMMAND succeeded but the executable was not found afterwards. " +
                    "Locate it manually, or set its path in Settings | Tools | Serverpod.",
            )

        return Result.Success(installed)
    }

    private data class InstallOutcome(
        val output: CapturedProcessOutput,
        val installed: Path?,
    )

    private const val MAX_ERROR_CHARS = 1000
}
