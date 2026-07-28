package dev.serverpod.idea.toolwindow

import com.intellij.execution.util.ExecUtil
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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import dev.serverpod.idea.cli.CliTool
import dev.serverpod.idea.cli.CliVersions
import dev.serverpod.idea.cli.ServerpodCommand
import dev.serverpod.idea.cli.ServerpodConsoleService
import dev.serverpod.idea.project.ServerpodLayout
import dev.serverpod.idea.project.ServerpodProjectService
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

class ServerpodPanel(
    private val project: Project,
    parentDisposable: Disposable,
) : SimpleToolWindowPanel(true, true), UiDataProvider {

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
                ServerpodProjectService.LayoutListener { onEdt { render(it) } },
            )

            render(ServerpodProjectService.getInstance(project).layout())
            refreshStatus()
    }

    override fun uiDataSnapshot(sink: DataSink) {
        sink[CommonDataKeys.PROJECT] = project
    }

    private fun render(layout: ServerpodLayout?) {
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
        row("Containers:") {
            label(dockerStatus)
            comment(
                if (layout.hasDocker) {
                    "From <code>${layout.serverDir.fileName}/docker-compose.yaml</code>."
                } else {
                    "This template has no Docker Compose setup."
                }
            )
        }
    }

    /**
     * Docker and the SDK versions each cost a process launch, so both are
     * resolved off the EDT and the panel is redrawn once the answers arrive.
     */
    private fun refreshStatus() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val layout = ServerpodProjectService.getInstance(project).layout()

            dockerStatus = if (layout?.dockerComposeFile == null) {
                "not applicable"
            } else {
                queryDockerStatus(layout)
            }

            val versions = CliVersions.getInstance()
            versions.detectAll()
            sdkVersions = versions.summary(listOf(CliTool.DART, CliTool.FLUTTER, CliTool.SERVERPOD))
                .ifBlank { "could not be determined" }

            onEdt { render(ServerpodProjectService.getInstance(project).layout()) }
        }
    }

    private fun queryDockerStatus(layout: ServerpodLayout): String {
        val commandLine = ServerpodCommand.commandLine(
            CliTool.DOCKER,
            layout.serverDir,
            listOf("compose", "ps", "--quiet"),
        ) ?: return "Docker not found"

        val output = runCatching { ExecUtil.execAndGetOutput(commandLine, DOCKER_TIMEOUT_MS) }
            .getOrElse { return "could not be determined" }

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
            dockerStatus = "checking\u2026"
            sdkVersions = "checking\u2026"
            CliVersions.getInstance().invalidate()

            ApplicationManager.getApplication().executeOnPooledThread {
                ServerpodProjectService.getInstance(project).detectNow()
                refreshStatus()
            }
        }
    }

    private fun onEdt(block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) block()
        }, project.disposed)
    }

    private fun describe(root: Path, path: Path?): String =
        path?.let { runCatching { root.relativize(it).toString() }.getOrDefault(it.toString()) } ?: "\u2014"

    private companion object {
        const val TOOLBAR_PLACE = "ServerpodToolWindow"
        const val DOCKER_TIMEOUT_MS = 15_000

        val TOOLBAR_ACTION_IDS = listOf(
            "Serverpod.RunServer",
            "Serverpod.Generate",
            "Serverpod.CreateMigration",
            "Serverpod.CreateRepairMigration",
            "Serverpod.DockerUp",
            "Serverpod.DockerDown",
            "Serverpod.ResetDatabase",
        )
    }
}
