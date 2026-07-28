package dev.serverpod.idea.wizard

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.LocalFileSystem
import dev.serverpod.idea.ServerpodNotifications
import dev.serverpod.idea.cli.CliTool
import dev.serverpod.idea.cli.ServerpodCommand
import dev.serverpod.idea.cli.ServerpodCommandRunner
import dev.serverpod.idea.cli.ServerpodConsoleService
import dev.serverpod.idea.project.ServerpodDartSupport
import dev.serverpod.idea.project.ServerpodLayout
import dev.serverpod.idea.project.ServerpodProjectService
import dev.serverpod.idea.run.ServerpodRunConfigurations
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

    fun schedule(project: Project, request: ServerpodCreateRequest) {
        StartupManager.getInstance(project).runAfterOpened {
            ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, "Creating Serverpod project", true) {
                    override fun run(indicator: ProgressIndicator) {
                        indicator.isIndeterminate = true
                        generate(project, request, indicator)
                    }
                }
            )
        }
    }

    private fun generate(project: Project, request: ServerpodCreateRequest, indicator: ProgressIndicator) {
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

        indicator.text = "Running serverpod create"
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

        val exitCode = ServerpodCommandRunner.runSync(project, createCommand, indicator)
        if (exitCode != 0) {
            if (exitCode != ServerpodCommandRunner.CANCELLED) {
                ServerpodCommandRunner.reportFailure(project, createCommand, exitCode)
            }
            if (!generatesInPlace) WorkspaceRelocator.deleteRecursively(workDir)
            return
        }

        if (!generatesInPlace) {
            indicator.text = "Moving generated files into the project"
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
            } finally {
                WorkspaceRelocator.deleteRecursively(workDir)
            }

            // Pub writes absolute paths into .dart_tool, so it has to be redone
            // after the move or the analyzer resolves nothing.
            indicator.text = "Resolving dependencies"
            resolveDependencies(project, projectRoot, indicator)
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
        indicator.text = "Configuring the Dart SDK"
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
            indicator.text = "Starting Docker containers"
            ServerpodCommandRunner.runSync(
                project,
                ServerpodCommand(
                    title = "docker compose up",
                    tool = CliTool.DOCKER,
                    workDir = layout.serverDir,
                    arguments = listOf("compose", "up", "--detach"),
                ),
                indicator,
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
    }

    private fun openEntryPoint(project: Project, layout: ServerpodLayout) {
        val file = LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(layout.serverEntryPoint.toString())
            ?: return

        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) FileEditorManager.getInstance(project).openFile(file, true)
        }, project.disposed)
    }

    private fun resolveDependencies(project: Project, projectRoot: Path, indicator: ProgressIndicator) {
        WorkspaceRelocator.deleteStalePubCaches(projectRoot)

        ServerpodCommandRunner.runSync(
            project,
            ServerpodCommand(
                title = "dart pub get",
                tool = CliTool.DART,
                workDir = projectRoot,
                arguments = listOf("pub", "get"),
            ),
            indicator,
        )
    }

}
