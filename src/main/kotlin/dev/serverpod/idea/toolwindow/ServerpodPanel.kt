package dev.serverpod.idea.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import dev.serverpod.idea.cli.CliTool
import dev.serverpod.idea.cli.CliVersions
import dev.serverpod.idea.cli.ServerpodCommand
import dev.serverpod.idea.cli.ServerpodConsoleService
import dev.serverpod.idea.cli.captureProcess
import dev.serverpod.idea.cli.onEdt
import dev.serverpod.idea.project.ServerpodDatabaseMode
import dev.serverpod.idea.project.ServerpodLayout
import dev.serverpod.idea.project.ServerpodProjectService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.awt.BorderLayout
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JPanel

class ServerpodPanel(
    private val project: Project,
    parentDisposable: Disposable,
) : SimpleToolWindowPanel(true, true), UiDataProvider {

    private val panelDisposed = AtomicBoolean(false)

    private val panelScope = ServerpodProjectService.getInstance(project)
        .createChildScope("Serverpod tool window")

    @Volatile
    private var statusJob: Job? = null

    // Without insets the labels sit flush against the tool window edge, out of
    // line with the toolbar above them.
    private val infoContainer = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(6, 12, 8, 12)
    }

    @Volatile
    private var dockerStatus: String = "checking\u2026"

    @Volatile
    private var sdkVersions: String = "checking\u2026"

    init {
        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar(TOOLBAR_PLACE, buildToolbarGroup(), true)
        actionToolbar.targetComponent = this
        toolbar = actionToolbar.component

        setContent(
            JBSplitter(true, 0.35f).apply {
                firstComponent = JBScrollPane(infoContainer).apply { border = JBUI.Borders.empty() }
                secondComponent = ServerpodConsoleService.getInstance(project).console.component
            }
        )

        project.messageBus
            .connect(parentDisposable)
            .subscribe(
                ServerpodProjectService.TOPIC,
                ServerpodProjectService.LayoutListener { layout ->
                    panelScope.launch {
                        onEdt {
                            if (!isDisposed()) render(layout)
                        }
                    }
                },
            )

        Disposer.register(parentDisposable, object : Disposable {
            override fun dispose() {
                panelDisposed.set(true)
                statusJob?.cancel()
                panelScope.cancel()
            }
        })

        render(ServerpodProjectService.getInstance(project).layout())
        refreshStatus()
    }

    override fun uiDataSnapshot(sink: DataSink) {
        sink[CommonDataKeys.PROJECT] = project
    }

    private fun render(layout: ServerpodLayout?) {
        if (isDisposed()) return

        infoContainer.removeAll()
        infoContainer.add(buildInfoPanel(layout), BorderLayout.NORTH)
        infoContainer.revalidate()
        infoContainer.repaint()
    }

    private fun buildInfoPanel(layout: ServerpodLayout?): JComponent = panel {
        if (layout == null) {
            row {
                label("No Serverpod workspace was found in this project.")
            }
            row {
                comment("Create one with <b>File | New | Project</b> and pick <b>Serverpod</b>.")
            }
            return@panel
        }

        row("Project:") { label(layout.projectName) }
        row("Server:") { label(describe(layout.root, layout.serverDir)) }
        row("Client:") { label(describe(layout.root, layout.clientDir)) }
        row("Flutter app:") { label(describe(layout.root, layout.flutterDir)) }
            row("Migrations:") { label(describe(layout.root, layout.migrationsDir)) }
            row("SDKs:") {
                label(sdkVersions)
                comment("The versions the plugin runs commands with.")
            }
        row("Database:") {
            label(layout.databaseMode.displayName)
            comment(databaseComment(layout))
        }
        // An upgraded project keeps its Compose file even after moving to the
        // embedded database, so the row follows the file and the Database row
        // above says which one the server actually uses.
        if (layout.hasDocker) {
            row("Containers:") {
                label(dockerStatus)
                comment("From <code>${layout.serverDir.fileName}/docker-compose.yaml</code>.")
            }
        }
    }

    /** Says which command owns the database, since Serverpod 4 made that a choice. */
    private fun databaseComment(layout: ServerpodLayout): String = when (layout.databaseMode) {
        ServerpodDatabaseMode.EMBEDDED ->
            "Run by the server itself from <code>dataPath</code>, so Docker is not needed."

        ServerpodDatabaseMode.SQLITE -> "The SQLite dialect, from a file in the server package."
        ServerpodDatabaseMode.DOCKER -> "Brought up with Docker Compose."
        ServerpodDatabaseMode.EXTERNAL -> "Configured in <code>config/development.yaml</code>, hosted elsewhere."
        ServerpodDatabaseMode.NONE -> "This project is configured without one."
    }

    /**
     * Docker and the SDK versions each cost a process launch, so both are
     * resolved off the EDT and the panel is redrawn once the answers arrive.
     */
    private fun refreshStatus(scope: CoroutineScope = panelScope) {
        if (isDisposed()) return

        statusJob?.cancel()
        statusJob = scope.launch {
            if (isDisposed()) return@launch

            val layout = ServerpodProjectService.getInstance(project).layout()

            val updatedDockerStatus = withContext(Dispatchers.IO) {
                if (layout?.dockerComposeFile == null) {
                    "not applicable"
                } else {
                    queryDockerStatus(layout)
                }
            }

            val versions = CliVersions.getInstance()
            versions.detectAll()
            val updatedSdkVersions = versions.summary(listOf(CliTool.DART, CliTool.FLUTTER, CliTool.SERVERPOD))
                .ifBlank { "could not be determined" }

            onEdt {
                if (isDisposed()) return@onEdt

                dockerStatus = updatedDockerStatus
                sdkVersions = updatedSdkVersions
                render(ServerpodProjectService.getInstance(project).layout())
            }
        }
    }

    private suspend fun queryDockerStatus(layout: ServerpodLayout): String {
        val commandLine = ServerpodCommand.commandLine(
            CliTool.DOCKER,
            layout.serverDir,
            listOf("compose", "ps", "--quiet"),
        ) ?: return "Docker not found"

        val output = try {
            withTimeout(DOCKER_TIMEOUT_MS) { captureProcess(commandLine) }
        } catch (_: TimeoutCancellationException) {
            return "could not be determined"
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return "could not be determined"
        }

        return when {
            output.exitCode != 0 -> "unavailable (is Docker running?)"
            output.stdout.isBlank() -> "stopped"
            else -> "running (${output.stdout.trim().lines().size} containers)"
        }
    }

    private fun buildToolbarGroup() = DefaultActionGroup().apply {
        val actionManager = ActionManager.getInstance()
        TOOLBAR_ACTION_IDS.mapNotNull { actionManager.getAction(it) }.forEach { add(it) }
        addSeparator()
        add(RefreshAction())
    }

    private inner class RefreshAction : AnAction(
        "Refresh",
        "Re-detect the workspace layout and container status",
        AllIcons.Actions.Refresh,
    ), DumbAware {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun actionPerformed(e: AnActionEvent) {
            val actionScope = e.coroutineScope
            actionScope.launch {
                onEdt {
                    if (isDisposed()) return@onEdt

                    dockerStatus = "checking\u2026"
                    sdkVersions = "checking\u2026"
                }
                if (isDisposed()) return@launch

                CliVersions.getInstance().invalidate()
                withContext(Dispatchers.IO) {
                    if (!isDisposed()) {
                        ServerpodProjectService.getInstance(project).detectNow()
                    }
                }
                if (!isDisposed()) refreshStatus(actionScope)
            }
        }
    }

    private fun describe(root: Path, path: Path?): String =
        path?.let { runCatching { root.relativize(it).toString() }.getOrDefault(it.toString()) } ?: "\u2014"

    private fun isDisposed(): Boolean = project.isDisposed || panelDisposed.get()

    private companion object {
        const val TOOLBAR_PLACE = "ServerpodToolWindow"
        const val DOCKER_TIMEOUT_MS = 15_000L

        val TOOLBAR_ACTION_IDS = listOf(
            "Serverpod.StartStack",
            "Serverpod.RunServer",
            "Serverpod.StartEmbeddedDatabase",
            "Serverpod.Generate",
            "Serverpod.CreateMigration",
            "Serverpod.CreateRepairMigration",
            "Serverpod.DockerUp",
            "Serverpod.DockerDown",
            "Serverpod.ResetDatabase",
            "Serverpod.InstallAgentTooling",
        )
    }
}
