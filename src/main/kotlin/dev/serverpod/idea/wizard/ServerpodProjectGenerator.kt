package dev.serverpod.idea.wizard

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import dev.serverpod.idea.ServerpodNotifications
import dev.serverpod.idea.cli.CliTool
import dev.serverpod.idea.cli.ServerpodCommand
import dev.serverpod.idea.cli.ServerpodCommandRunner
import dev.serverpod.idea.cli.ServerpodConsoleService
import dev.serverpod.idea.cli.onEdt
import dev.serverpod.idea.project.ServerpodDartSupport
import dev.serverpod.idea.project.ServerpodLayout
import dev.serverpod.idea.project.ServerpodProjectService
import dev.serverpod.idea.run.ServerpodRunConfigurations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.name

/**
 * Drives `serverpod create` for a project the New Project wizard has already
 * opened.
 *
 * The CLI is happy to populate a directory that already exists, so the common
 * case runs in place and leaves `.dart_tool` package paths pointing at the real
 * location. When the project directory name does not match the Dart package
 * name the CLI would create the wrong folder, so generation happens in a
 * scratch directory next to the project and the result is moved in.
 */
object ServerpodProjectGenerator {

    /**
     * Records what the wizard asked for. The CLI cannot run until the project has
     * finished opening, so the work itself starts from [runPending].
     */
    fun schedule(project: Project, request: ServerpodCreateRequest) {
        ServerpodPendingCreate.getInstance(project).submit(request)
    }

    /**
     * Starts generation for whatever the wizard recorded, reporting whether there
     * was anything to do so the caller can skip the checks meant for an existing
     * workspace.
     */
    suspend fun runPending(project: Project): Boolean {
        val request = ServerpodPendingCreate.getInstance(project).consume() ?: return false

        withBackgroundProgress(project, "Creating Serverpod project") {
            withContext(Dispatchers.IO) {
                generate(project, request)
            }
        }

        return true
    }

    private suspend fun generate(project: Project, request: ServerpodCreateRequest) {
        val console = ServerpodConsoleService.getInstance(project)

        val projectRoot = project.basePath?.let { Path.of(it) } ?: run {
            ServerpodNotifications.error(
                project,
                "Creating Serverpod project",
                "The project has no directory on disk, so the Serverpod CLI cannot run.",
            )
            return
        }

        val generatesInPlace = projectRoot.name == request.packageName
        val workDir = if (generatesInPlace) {
            projectRoot.parent
        } else {
            projectRoot.parent.resolve(".serverpod-create-${UUID.randomUUID()}")
        }

        try {
            Files.createDirectories(workDir)
        } catch (e: IOException) {
            ServerpodNotifications.error(
                project,
                "Creating Serverpod project",
                "Could not prepare ${workDir}: ${e.message}",
            )
            return
        }

        try {
            val createCommand = ServerpodCommand(
                title = "serverpod create ${request.packageName}",
                tool = CliTool.SERVERPOD,
                workDir = workDir,
                arguments = ServerpodCommand.NON_INTERACTIVE + listOf(
                    "create",
                    "--name", request.packageName,
                    "--template", request.template.cliValue,
                ),
            )

            val exitCode = ServerpodCommandRunner.runSync(project, createCommand)
            if (exitCode != 0) {
                if (exitCode != ServerpodCommandRunner.CANCELLED) {
                    ServerpodCommandRunner.reportFailure(project, createCommand, exitCode)
                }
                return
            }

            if (!generatesInPlace) {
                val generated = workDir.resolve(request.packageName)
                try {
                    WorkspaceRelocator.mergeInto(generated, projectRoot)
                } catch (e: IOException) {
                    ServerpodNotifications.error(
                        project,
                        "Creating Serverpod project",
                        "Generated the project but could not move it into place: ${e.message}",
                    )
                    return
                }

                // Pub writes absolute paths into .dart_tool, so it has to be redone
                // after the move or the analyzer resolves nothing.
                resolveDependencies(project, projectRoot)
            }

            ServerpodCommandRunner.refreshWorkspace(project)
            val layout = ServerpodProjectService.getInstance(project).detectNow()

            if (layout == null) {
                console.println(
                    "Serverpod created the project but the workspace layout was not recognised.",
                    ConsoleViewContentType.ERROR_OUTPUT,
                )
                return
            }

            // Before anything else, because the wizard leaves the project without a
            // module and nothing here is analysed until one exists.
            when (val dart = ServerpodDartSupport.configure(project, layout)) {
                is ServerpodDartSupport.Result.Configured ->
                    console.println(ServerpodDartSupport.describe(dart), ConsoleViewContentType.SYSTEM_OUTPUT)

                ServerpodDartSupport.Result.NoSdkFound -> console.println(
                    "No Dart SDK was found, so the project has none configured. " +
                        "Set the Dart or Flutter path in Settings | Tools | Serverpod.",
                    ConsoleViewContentType.ERROR_OUTPUT,
                )
            }

            if (request.createRunConfiguration) {
                ServerpodRunConfigurations.createDefault(project, layout, request.applyMigrations)
            }

            if (request.startDocker && layout.dockerComposeFile != null) {
                ServerpodCommandRunner.runSync(
                    project,
                    ServerpodCommand(
                        title = "docker compose up",
                        tool = CliTool.DOCKER,
                        workDir = layout.serverDir,
                        arguments = listOf("compose", "up", "--detach"),
                    ),
                )
            }

            if (request.openEntryPoint) {
                openEntryPoint(project, layout)
            }

            ServerpodNotifications.info(
                project,
                "Serverpod project created",
                "${request.packageName} is ready. Use the Serverpod tool window to generate code and run the server.",
            )
        } finally {
            if (!generatesInPlace) WorkspaceRelocator.deleteRecursively(workDir)
        }
    }

    private suspend fun openEntryPoint(project: Project, layout: ServerpodLayout) {
        val file = LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(layout.serverEntryPoint.toString())
            ?: return

        onEdt {
            FileEditorManager.getInstance(project).openFile(file, true)
        }
    }

    private suspend fun resolveDependencies(project: Project, projectRoot: Path) {
        WorkspaceRelocator.deleteStalePubCaches(projectRoot)

        ServerpodCommandRunner.runSync(
            project,
            ServerpodCommand(
                title = "dart pub get",
                tool = CliTool.DART,
                workDir = projectRoot,
                arguments = listOf("pub", "get"),
            ),
        )
    }

}
