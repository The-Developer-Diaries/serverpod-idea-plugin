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

    private val runModeCombo = ComboBox(ServerpodRunMode.entries.toTypedArray())
    private val applyMigrationsCheckBox = JBCheckBox("Apply database migrations on start")
    private val applyRepairMigrationCheckBox = JBCheckBox("Apply repair migration on start")
    private val extraArgumentsField = JBTextField()

    private val editor = panel {
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
        row("Additional arguments:") {
            cell(extraArgumentsField)
                .align(AlignX.FILL)
                .comment("Passed straight through, for example <code>--server-id 1 --logging verbose</code>.")
        }
    }

    override fun createEditor(): JComponent = editor

    override fun resetEditorFrom(configuration: ServerpodRunConfiguration) {
        val options = configuration.options
        serverDirField.text = options.serverDir.orEmpty()
        runModeCombo.selectedItem = ServerpodRunMode.from(options.runMode)
        applyMigrationsCheckBox.isSelected = options.applyMigrations
        applyRepairMigrationCheckBox.isSelected = options.applyRepairMigration
        extraArgumentsField.text = options.extraArguments.orEmpty()
    }

    override fun applyEditorTo(configuration: ServerpodRunConfiguration) {
        val options = configuration.options
        options.serverDir = serverDirField.text.trim().takeIf { it.isNotEmpty() }
        options.runMode = (runModeCombo.selectedItem as? ServerpodRunMode ?: ServerpodRunMode.DEVELOPMENT).cliValue
        options.applyMigrations = applyMigrationsCheckBox.isSelected
        options.applyRepairMigration = applyRepairMigrationCheckBox.isSelected
        options.extraArguments = extraArgumentsField.text.trim().takeIf { it.isNotEmpty() }
    }
}
