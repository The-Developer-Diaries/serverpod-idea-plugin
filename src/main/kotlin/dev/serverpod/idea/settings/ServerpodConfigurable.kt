package dev.serverpod.idea.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import dev.serverpod.idea.cli.CliTool
import dev.serverpod.idea.cli.CliVersions
import dev.serverpod.idea.cli.ServerpodCliInstaller

class ServerpodConfigurable : BoundConfigurable("Serverpod") {

    private val settings get() = ServerpodSettings.getInstance()

    private val fields = mutableMapOf<CliTool, Cell<TextFieldWithBrowseButton>>()

    override fun createPanel(): DialogPanel = panel {
        group("Executables") {
            row("Serverpod CLI:") {
                executableField(CliTool.SERVERPOD, { settings.serverpodPath }, { settings.serverpodPath = it })
            }
            row("") {
                // Doubles as an upgrade path, since activate always fetches the latest release.
                button("Install or Update\u2026") { installCli() }
                    .comment("Runs <code>${ServerpodCliInstaller.COMMAND}</code>. ${ServerpodCliInstaller.PATH_HINT}")
            }
            row("Dart SDK:") {
                executableField(CliTool.DART, { settings.dartPath }, { settings.dartPath = it })
            }
            row("Flutter SDK:") {
                executableField(CliTool.FLUTTER, { settings.flutterPath }, { settings.flutterPath = it })
            }
            row("Docker:") {
                executableField(CliTool.DOCKER, { settings.dockerPath }, { settings.dockerPath = it })
            }
            row {
                comment(
                    "Leave a field empty to look the tool up on <code>PATH</code> and in the usual install locations."
                )
            }
        }
    }.also { detectVersions() }

    override fun disposeUIResources() {
        fields.clear()
        super.disposeUIResources()
    }

    private fun installCli() {
        when (val result = ServerpodCliInstaller.install(null)) {
            is ServerpodCliInstaller.Result.Success -> {
                CliVersions.getInstance().invalidate()
                detectVersions()
                Messages.showInfoMessage(
                    "The Serverpod CLI is installed at ${result.path}.",
                    "Serverpod CLI Ready",
                )
            }

            is ServerpodCliInstaller.Result.Failed ->
                Messages.showErrorDialog(result.message, "Could Not Install the Serverpod CLI")

            ServerpodCliInstaller.Result.Cancelled -> Unit
        }
    }

    /** Each version costs a process launch, so the labels fill in once the answers arrive. */
    private fun detectVersions() {
        ApplicationManager.getApplication().executeOnPooledThread {
            CliVersions.getInstance().detectAll()

            ApplicationManager.getApplication().invokeLater(
                { fields.forEach { (tool, field) -> field.comment?.text = detectedLabel(tool) } },
                ModalityState.any(),
            )
        }
    }

    private fun Row.executableField(
        tool: CliTool,
        get: () -> String?,
        set: (String) -> Unit,
    ): Cell<TextFieldWithBrowseButton> =
        textFieldWithBrowseButton(
            FileChooserDescriptorFactory.singleFile().withTitle("Select ${tool.displayName} Executable")
        )
            .bindText({ get().orEmpty() }, set)
            .align(AlignX.FILL)
            .comment(detectedLabel(tool))
            .also { fields[tool] = it }

    private fun detectedLabel(tool: CliTool): String {
        val resolved = tool.resolve()
            ?: return "Not found. ${tool.executableName} is required for this feature."

        return CliVersions.getInstance().cached(tool)
            ?.let { "Detected: $resolved (version $it)" }
            ?: "Detected: $resolved"
    }
}
