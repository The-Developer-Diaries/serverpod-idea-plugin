package dev.serverpod.idea.wizard

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.GitNewProjectWizardStep
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import dev.serverpod.idea.cli.CliTool
import dev.serverpod.idea.cli.ServerpodCliInstaller
import dev.serverpod.idea.settings.ServerpodSettings
import javax.swing.JLabel

class ServerpodWizardStep(private val parent: GitNewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

    private val properties = PropertiesComponent.getInstance()

    private val templateProperty = propertyGraph.property(
        ServerpodTemplate.fromCliValue(properties.getValue(TEMPLATE_KEY)) ?: ServerpodTemplate.SERVER
    )
    private val packageNameProperty =
        propertyGraph.lazyProperty { ServerpodNaming.suggestPackageName(parent.name) }
    private val startDockerProperty = propertyGraph.property(properties.getBoolean(START_DOCKER_KEY, false))
    private val createRunConfigurationProperty =
        propertyGraph.property(properties.getBoolean(RUN_CONFIGURATION_KEY, true))
    private val applyMigrationsProperty =
        propertyGraph.property(properties.getBoolean(APPLY_MIGRATIONS_KEY, true))
    private val openEntryPointProperty =
        propertyGraph.property(properties.getBoolean(OPEN_ENTRY_POINT_KEY, true))

    private var template by templateProperty
    private var packageName by packageNameProperty
    private var startDocker by startDockerProperty
    private var createRunConfiguration by createRunConfigurationProperty
    private var applyMigrations by applyMigrationsProperty
    private var openEntryPoint by openEntryPointProperty

    /** Migrations only exist for templates with a database, and only a run configuration can apply them. */
    private val canApplyMigrationsProperty = propertyGraph.lazyProperty { canApplyMigrations() }

    private val cliReadyProperty = propertyGraph.property(CliTool.SERVERPOD.resolve() != null)
    private val cliMissingProperty = cliReadyProperty.transform { !it }
    private val dartAvailableProperty = propertyGraph.property(CliTool.DART.resolve() != null)

    init {
        packageNameProperty.dependsOn(parent.nameProperty) { ServerpodNaming.suggestPackageName(parent.name) }
        canApplyMigrationsProperty.dependsOn(templateProperty) { canApplyMigrations() }
        canApplyMigrationsProperty.dependsOn(createRunConfigurationProperty) { canApplyMigrations() }
    }

    private fun canApplyMigrations() = template.hasDatabase && createRunConfiguration

    private fun cliStatusText(): String = when {
        CliTool.SERVERPOD.resolve() != null -> "Installed."
        CliTool.DART.resolve() == null -> "Not found, and neither is the Dart SDK it installs through."
        else -> "Not found."
    }

    private fun installCli(status: JLabel) {
        val result = ServerpodCliInstaller.install(null)
        refreshCliStatus(status)

        if (result is ServerpodCliInstaller.Result.Failed) {
            Messages.showErrorDialog(result.message, "Could Not Install the Serverpod CLI")
        }
    }

    private fun locateCli(status: JLabel) {
        val chosen = FileChooser.chooseFile(
            FileChooserDescriptorFactory.singleFile().withTitle("Select the Serverpod CLI Executable"),
            null,
            null,
        ) ?: return

        ServerpodSettings.getInstance().serverpodPath = chosen.path
        refreshCliStatus(status)

        if (CliTool.SERVERPOD.resolve() == null) {
            Messages.showErrorDialog(
                "${chosen.path} is not an executable file.",
                "Not a Serverpod CLI Executable",
            )
        }
    }

    private fun refreshCliStatus(status: JLabel) {
        status.text = cliStatusText()
        cliReadyProperty.set(CliTool.SERVERPOD.resolve() != null)
        dartAvailableProperty.set(CliTool.DART.resolve() != null)
    }

    override fun setupUI(builder: Panel) {
        with(builder) {
            // The whole row is hidden once the CLI resolves, so a working setup
            // costs no height and only a problem is ever surfaced.
            row("Serverpod CLI:") {
                val status = label(cliStatusText())
                    .validationOnApply {
                        if (CliTool.SERVERPOD.resolve() == null) {
                            error("The Serverpod CLI is required to create the project.")
                        } else {
                            null
                        }
                    }

                button("Install\u2026") { installCli(status.component) }
                    .enabledIf(dartAvailableProperty)

                button("Locate\u2026") { locateCli(status.component) }
            }
                .rowComment("Runs <code>${ServerpodCliInstaller.COMMAND}</code>. ${ServerpodCliInstaller.PATH_HINT}")
                .visibleIf(cliMissingProperty)

            row("Template:") {
                // Only the selected description is shown; listing all three kept
                // three permanent lines in a dialog that is already tall.
                val combo = comboBox(ServerpodTemplate.entries)
                    .bindItem(templateProperty)
                    .comment(template.description)

                templateProperty.afterChange { selected ->
                    combo.comment?.text = selected.description
                }
            }

            row("Package name:") {
                textField()
                    .bindText(packageNameProperty)
                    .align(AlignX.FILL)
                    .validationOnInput { field ->
                        ServerpodNaming.packageNameError(field.text)?.let { error(it) }
                    }
                    .comment("Serverpod appends <code>_server</code>, <code>_client</code>, and <code>_flutter</code>.")
            }

            collapsibleGroup("After Creating the Project") {
                row {
                    checkBox("Start Docker containers")
                        .bindSelected(startDockerProperty)
                        .enabledIf(templateProperty.transform { it.hasDatabase })
                        .comment("Runs <code>docker compose up --detach</code> for PostgreSQL and Redis.")
                }

                row {
                    checkBox("Create a run configuration for the server")
                        .bindSelected(createRunConfigurationProperty)
                }

                indent {
                    row {
                        checkBox("Apply migrations on the first run")
                            .bindSelected(applyMigrationsProperty)
                            .enabledIf(canApplyMigrationsProperty)
                            .comment("Passes <code>--apply-migrations</code> so the initial schema is created.")
                    }
                }

                row {
                    checkBox("Open bin/main.dart in the editor")
                        .bindSelected(openEntryPointProperty)
                }
            }.apply {
                expanded = properties.getBoolean(OPTIONS_EXPANDED_KEY, false)
                addExpandedListener { properties.setValue(OPTIONS_EXPANDED_KEY, it, false) }
            }
        }
    }

    override fun setupProject(project: Project) {
        properties.setValue(TEMPLATE_KEY, template.cliValue)
        properties.setValue(START_DOCKER_KEY, startDocker, false)
        properties.setValue(RUN_CONFIGURATION_KEY, createRunConfiguration, true)
        properties.setValue(APPLY_MIGRATIONS_KEY, applyMigrations, true)
        properties.setValue(OPEN_ENTRY_POINT_KEY, openEntryPoint, true)

        ServerpodProjectGenerator.schedule(
            project,
            ServerpodCreateRequest(
                packageName = packageName,
                template = template,
                startDocker = startDocker && template.hasDatabase,
                createRunConfiguration = createRunConfiguration,
                applyMigrations = applyMigrations && canApplyMigrations(),
                openEntryPoint = openEntryPoint,
            ),
        )
    }

    private companion object {
        const val TEMPLATE_KEY = "dev.serverpod.idea.wizard.template"
        const val START_DOCKER_KEY = "dev.serverpod.idea.wizard.startDocker"
        const val RUN_CONFIGURATION_KEY = "dev.serverpod.idea.wizard.createRunConfiguration"
        const val APPLY_MIGRATIONS_KEY = "dev.serverpod.idea.wizard.applyMigrations"
        const val OPEN_ENTRY_POINT_KEY = "dev.serverpod.idea.wizard.openEntryPoint"
        const val OPTIONS_EXPANDED_KEY = "dev.serverpod.idea.wizard.optionsExpanded"
    }
}
