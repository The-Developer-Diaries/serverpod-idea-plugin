package dev.serverpod.idea.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class ServerpodRunConfigurationEditor(project: Project) : SettingsEditor<ServerpodRunConfiguration>() {

    private val serverDirField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.singleDir().withTitle("Select the Serverpod Server Package"),
        )
    }

    // A mode the installed CLI cannot run is still listed, because a
    // configuration shared through version control has to round-trip intact;
    // `checkConfiguration` is what reports it.
    private val launchModeCombo = ComboBox(ServerpodLaunchMode.entries.toTypedArray())
    private val runModeCombo = ComboBox(ServerpodRunMode.entries.toTypedArray())
    private val applyMigrationsCheckBox = JBCheckBox("Apply database migrations on start")
    private val applyRepairMigrationCheckBox = JBCheckBox("Apply repair migration on start")
    private val launchFlutterAppsCheckBox = JBCheckBox("Launch the Flutter apps alongside the server")
    private val extraArgumentsField = JBTextField()

    private val editor = panel {
        row("Command:") {
            cell(launchModeCombo)
                .comment(launchModeComment(ServerpodLaunchMode.ENTRY_POINT))
                .apply {
                    launchModeCombo.addActionListener {
                        comment?.text = launchModeComment(selectedLaunchMode())
                        updateEnabledState()
                    }
                }
        }
        row("Server package:") {
            cell(serverDirField).align(AlignX.FILL)
        }
        row("Run mode:") {
            cell(runModeCombo)
                .comment("Selects the matching file in the server's <code>config</code> directory.")
        }
        row {
            cell(applyMigrationsCheckBox)
        }
        row {
            cell(applyRepairMigrationCheckBox)
                .comment("Repairs the live database by comparing it to the target state.")
        }
        row {
            cell(launchFlutterAppsCheckBox)
                .comment("Covers the apps marked <code>auto_launch</code> in the server's pubspec.")
        }
        row("Additional arguments:") {
            cell(extraArgumentsField)
                .align(AlignX.FILL)
                .comment("Passed straight through, for example <code>--server-id 1 --logging verbose</code>.")
        }
    }

    private fun selectedLaunchMode(): ServerpodLaunchMode =
        launchModeCombo.selectedItem as? ServerpodLaunchMode ?: ServerpodLaunchMode.ENTRY_POINT

    private fun launchModeComment(mode: ServerpodLaunchMode): String = when (mode) {
        ServerpodLaunchMode.ENTRY_POINT ->
            "Runs the server only. Start the database separately."

        ServerpodLaunchMode.START ->
            "Generates code, brings up the database, and hot-reloads the server on save. " +
                "The interactive terminal UI is off, because the run console is not a terminal."

        ServerpodLaunchMode.DATABASE ->
            "Runs the embedded PostgreSQL on its own, for a project that sets " +
                "<code>dataPath</code> on its database config."
    }

    /** A mode that does not run the server has nothing to migrate or hot-reload. */
    private fun updateEnabledState() {
        val mode = selectedLaunchMode()
        applyMigrationsCheckBox.isEnabled = mode.runsServer
        applyRepairMigrationCheckBox.isEnabled = mode.runsServer
        launchFlutterAppsCheckBox.isEnabled = mode == ServerpodLaunchMode.START
    }

    override fun createEditor(): JComponent = editor

    override fun resetEditorFrom(configuration: ServerpodRunConfiguration) {
        val options = configuration.options
        launchModeCombo.selectedItem = ServerpodLaunchMode.from(options.launchMode)
        serverDirField.text = options.serverDir.orEmpty()
        runModeCombo.selectedItem = ServerpodRunMode.from(options.runMode)
        applyMigrationsCheckBox.isSelected = options.applyMigrations
        applyRepairMigrationCheckBox.isSelected = options.applyRepairMigration
        launchFlutterAppsCheckBox.isSelected = options.launchFlutterApps
        extraArgumentsField.text = options.extraArguments.orEmpty()
        updateEnabledState()
    }

    override fun applyEditorTo(configuration: ServerpodRunConfiguration) {
        val options = configuration.options
        options.launchMode = selectedLaunchMode().id
        options.serverDir = serverDirField.text.trim().takeIf { it.isNotEmpty() }
        options.runMode = (runModeCombo.selectedItem as? ServerpodRunMode ?: ServerpodRunMode.DEVELOPMENT).cliValue
        options.applyMigrations = applyMigrationsCheckBox.isSelected
        options.applyRepairMigration = applyRepairMigrationCheckBox.isSelected
        options.launchFlutterApps = launchFlutterAppsCheckBox.isSelected
        options.extraArguments = extraArgumentsField.text.trim().takeIf { it.isNotEmpty() }
    }
}
