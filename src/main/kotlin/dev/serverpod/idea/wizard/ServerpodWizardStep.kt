package dev.serverpod.idea.wizard

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.GitNewProjectWizardStep
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import dev.serverpod.idea.cli.CliTool
import dev.serverpod.idea.cli.CliVersions
import dev.serverpod.idea.cli.ServerpodCliInstaller
import dev.serverpod.idea.cli.ServerpodFeature
import dev.serverpod.idea.cli.ServerpodIde
import dev.serverpod.idea.cli.ServerpodVersion
import dev.serverpod.idea.cli.onEdt
import dev.serverpod.idea.settings.ServerpodSettings
import kotlinx.coroutines.Job
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel

class ServerpodWizardStep(private val parent: GitNewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

    private val properties = PropertiesComponent.getInstance()

    /**
     * What `serverpod create` will accept depends on the CLI on this machine, and
     * reading that costs a process launch. The step opens with whatever is
     * already cached and rewires itself in [onVersionDetected] when the answer
     * arrives, so the dialog never blocks on it.
     */
    private val versionProperty = propertyGraph.property(CliVersions.getInstance().serverpodVersion())
    private var detectionJob: Job? = null
    private var templateCombo: ComboBox<ServerpodTemplate>? = null

    private val templateProperty = propertyGraph.property(
        ServerpodTemplate.restore(properties.getValue(TEMPLATE_KEY), versionProperty.get())
    )
    private val packageNameProperty =
        propertyGraph.lazyProperty { ServerpodNaming.suggestPackageName(parent.name) }

    private val databaseProperty = propertyGraph.property(properties.getBoolean(DATABASE_KEY, true))
    private val redisProperty = propertyGraph.property(properties.getBoolean(REDIS_KEY, true))
    private val authProperty = propertyGraph.property(properties.getBoolean(AUTH_KEY, true))
    private val ideProperties = ServerpodIde.entries
        .filter { it != ServerpodIde.NONE }
        .associateWith { ide ->
            propertyGraph.property(properties.getBoolean(ideKey(ide), ide in ServerpodIde.DEFAULTS))
        }

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

    /** Serverpod 4 turned the database and its companions into `create` flags. */
    private val hasCreateFlagsProperty =
        versionProperty.transform { ServerpodFeature.CREATE_FEATURE_FLAGS.isSupportedBy(it) }
    private val hasAgentToolingProperty =
        versionProperty.transform { ServerpodFeature.AGENT_TOOLING.isSupportedBy(it) }

    /** Compose is only worth offering to a CLI that has no embedded database. */
    private val offersDockerProperty = propertyGraph.lazyProperty { offersDocker() }

    /** Migrations only exist for a project with a database, and only a run configuration can apply them. */
    private val canApplyMigrationsProperty = propertyGraph.lazyProperty { canApplyMigrations() }

    private val cliReadyProperty = propertyGraph.property(CliTool.SERVERPOD.resolve() != null)
    private val cliMissingProperty = cliReadyProperty.transform { !it }
    private val dartAvailableProperty = propertyGraph.property(CliTool.DART.resolve() != null)

    init {
        packageNameProperty.dependsOn(parent.nameProperty) { ServerpodNaming.suggestPackageName(parent.name) }

        listOf(templateProperty, createRunConfigurationProperty, databaseProperty, versionProperty)
            .forEach { canApplyMigrationsProperty.dependsOn(it) { canApplyMigrations() } }
        listOf(templateProperty, versionProperty)
            .forEach { offersDockerProperty.dependsOn(it) { offersDocker() } }

        // Detection outlives a cancelled dialog otherwise, and would then call
        // back into components nobody is looking at.
        Disposer.register(context.disposable) { detectionJob?.cancel() }
        detectVersion()
    }

    /** True when the project will actually be created with a database. */
    private fun hasDatabase(): Boolean = template.supportsDatabase &&
        (!ServerpodFeature.CREATE_FEATURE_FLAGS.isSupportedBy(versionProperty.get()) || databaseProperty.get())

    private fun canApplyMigrations() = hasDatabase() && createRunConfiguration

    private fun offersDocker() = hasDatabase() &&
        !ServerpodFeature.EMBEDDED_DATABASE.isSupportedBy(versionProperty.get())

    /** Reads the CLI version off the EDT, then rebuilds whatever depends on it. */
    private fun detectVersion() {
        detectionJob?.cancel()
        detectionJob = CliVersions.getInstance().detectAllAsync {
            onEdt { onVersionDetected(CliVersions.getInstance().serverpodVersion()) }
        }
    }

    private fun onVersionDetected(version: ServerpodVersion?) {
        if (version == versionProperty.get()) return
        versionProperty.set(version)

        val available = ServerpodTemplate.availableFor(version)
        val selected = ServerpodTemplate.restore(properties.getValue(TEMPLATE_KEY), version)

        // Swapping the model clears the selection and would push a null through
        // the binding, so the property is restored immediately afterwards.
        templateCombo?.model = DefaultComboBoxModel(available.toTypedArray())
        template = selected
    }

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

        // A newly installed or newly pointed-at CLI is very likely a different
        // release, so the version-gated parts of the dialog have to be redone.
        CliVersions.getInstance().invalidate()
        detectVersion()
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
                // Only the selected description is shown; listing them all kept
                // permanent lines in a dialog that is already tall.
                val combo = comboBox(ServerpodTemplate.availableFor(versionProperty.get()))
                    .bindItem(templateProperty)
                    .comment(template.descriptionFor(versionProperty.get()))

                templateCombo = combo.component

                templateProperty.afterChange { selected ->
                    combo.comment?.text = selected.descriptionFor(versionProperty.get())
                }
                versionProperty.afterChange { version ->
                    combo.comment?.text = templateProperty.get().descriptionFor(version)
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

            collapsibleGroup("Project Features") {
                row {
                    checkBox("Database")
                        .bindSelected(databaseProperty)
                        .enabledIf(templateProperty.transform { it.supportsDatabase })
                        .comment("PostgreSQL, run from the project directory with no Docker needed.")
                }
                row {
                    checkBox("Redis caching")
                        .bindSelected(redisProperty)
                }
                row {
                    checkBox("Authentication")
                        .bindSelected(authProperty)
                        .enabledIf(databaseProperty.and(templateProperty.transform { it.supportsDatabase }))
                        .comment("Email and social sign-in. Requires a database.")
                }
            }
                .apply {
                    expanded = properties.getBoolean(FEATURES_EXPANDED_KEY, false)
                    addExpandedListener { properties.setValue(FEATURES_EXPANDED_KEY, it, false) }
                }
                .visibleIf(hasCreateFlagsProperty)

            collapsibleGroup("AI Agent Skills") {
                row {
                    comment(
                        "Serverpod installs skills and registers its MCP servers for the editors you " +
                            "pick, writing each one's own configuration file into the project.",
                    )
                }
                ideProperties.forEach { (ide, property) ->
                    row {
                        checkBox(ide.displayName).bindSelected(property)
                    }
                }
            }
                .apply {
                    expanded = properties.getBoolean(AGENTS_EXPANDED_KEY, false)
                    addExpandedListener { properties.setValue(AGENTS_EXPANDED_KEY, it, false) }
                }
                .visibleIf(hasAgentToolingProperty)

            collapsibleGroup("After Creating the Project") {
                row {
                    checkBox("Start Docker containers")
                        .bindSelected(startDockerProperty)
                        .comment("Runs <code>docker compose up --detach</code> for PostgreSQL and Redis.")
                }.visibleIf(offersDockerProperty)

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
        detectionJob?.cancel()

        properties.setValue(TEMPLATE_KEY, template.cliValue)
        properties.setValue(DATABASE_KEY, databaseProperty.get(), true)
        properties.setValue(REDIS_KEY, redisProperty.get(), true)
        properties.setValue(AUTH_KEY, authProperty.get(), true)
        ideProperties.forEach { (ide, property) ->
            properties.setValue(ideKey(ide), property.get(), ide in ServerpodIde.DEFAULTS)
        }
        properties.setValue(START_DOCKER_KEY, startDocker, false)
        properties.setValue(RUN_CONFIGURATION_KEY, createRunConfiguration, true)
        properties.setValue(APPLY_MIGRATIONS_KEY, applyMigrations, true)
        properties.setValue(OPEN_ENTRY_POINT_KEY, openEntryPoint, true)

        ServerpodProjectGenerator.schedule(
            project,
            ServerpodCreateRequest(
                packageName = packageName,
                template = template,
                features = createFeatures(),
                startDocker = startDocker && offersDocker(),
                createRunConfiguration = createRunConfiguration,
                applyMigrations = applyMigrations && canApplyMigrations(),
                openEntryPoint = openEntryPoint,
            ),
        )
    }

    private fun createFeatures(): ServerpodCreateFeatures? {
        if (!ServerpodFeature.CREATE_FEATURE_FLAGS.isSupportedBy(versionProperty.get())) return null

        return ServerpodCreateFeatures(
            database = template.supportsDatabase && databaseProperty.get(),
            redis = redisProperty.get(),
            auth = authProperty.get(),
            ides = ideProperties.filterValues { it.get() }.keys.toList(),
        )
    }

    private companion object {
        const val TEMPLATE_KEY = "dev.serverpod.idea.wizard.template"
        const val DATABASE_KEY = "dev.serverpod.idea.wizard.database"
        const val REDIS_KEY = "dev.serverpod.idea.wizard.redis"
        const val AUTH_KEY = "dev.serverpod.idea.wizard.auth"
        const val START_DOCKER_KEY = "dev.serverpod.idea.wizard.startDocker"
        const val RUN_CONFIGURATION_KEY = "dev.serverpod.idea.wizard.createRunConfiguration"
        const val APPLY_MIGRATIONS_KEY = "dev.serverpod.idea.wizard.applyMigrations"
        const val OPEN_ENTRY_POINT_KEY = "dev.serverpod.idea.wizard.openEntryPoint"
        const val OPTIONS_EXPANDED_KEY = "dev.serverpod.idea.wizard.optionsExpanded"
        const val FEATURES_EXPANDED_KEY = "dev.serverpod.idea.wizard.featuresExpanded"
        const val AGENTS_EXPANDED_KEY = "dev.serverpod.idea.wizard.agentsExpanded"

        fun ideKey(ide: ServerpodIde) = "dev.serverpod.idea.wizard.ide.${ide.cliValue}"
    }
}
