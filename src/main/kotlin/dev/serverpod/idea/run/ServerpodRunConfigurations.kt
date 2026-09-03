package dev.serverpod.idea.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import dev.serverpod.idea.cli.CliVersions
import dev.serverpod.idea.cli.ServerpodFeature
import dev.serverpod.idea.project.ServerpodLayout

object ServerpodRunConfigurations {

    /**
     * Reuses the configuration that starts this server package the same way, or
     * adds one. [applyMigrations] only affects a configuration that has to be
     * created.
     */
    fun findOrCreate(
        project: Project,
        layout: ServerpodLayout,
        launchMode: ServerpodLaunchMode = preferredLaunchMode(),
        applyMigrations: Boolean = layout.hasMigrations,
    ): RunnerAndConfigurationSettings {
        val runManager = RunManager.getInstance(project)
        val serverDir = layout.serverDir.toString()

        runManager.getConfigurationSettingsList(configurationType())
            .firstOrNull { settings ->
                val options = (settings.configuration as? ServerpodRunConfiguration)?.options
                options?.serverDir == serverDir && ServerpodLaunchMode.from(options.launchMode) == launchMode
            }
            ?.let { return it }

        val settings = runManager.createConfiguration(
            configurationName(layout, launchMode),
            configurationType().configurationFactories.first(),
        )

        (settings.configuration as ServerpodRunConfiguration).options.also {
            it.serverDir = serverDir
            it.launchMode = launchMode.id

            // `serverpod start` applies pending migrations itself, so the flag
            // would only duplicate what it already does.
            it.applyMigrations = launchMode == ServerpodLaunchMode.ENTRY_POINT &&
                applyMigrations &&
                layout.hasMigrations
        }

        runManager.addConfiguration(settings)
        return settings
    }

    fun createDefault(
        project: Project,
        layout: ServerpodLayout,
        applyMigrations: Boolean = layout.hasMigrations,
    ) = onEdt(project) {
        RunManager.getInstance(project).selectedConfiguration =
            findOrCreate(project, layout, applyMigrations = applyMigrations)
    }

    fun runServer(
        project: Project,
        layout: ServerpodLayout,
        launchMode: ServerpodLaunchMode = preferredLaunchMode(),
    ) = onEdt(project) {
        val settings = findOrCreate(project, layout, launchMode)
        RunManager.getInstance(project).selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }

    /**
     * `serverpod start` supersedes running the entry point by hand, so it is what
     * a new configuration gets once the installed CLI has it.
     */
    fun preferredLaunchMode(): ServerpodLaunchMode =
        if (CliVersions.getInstance().supports(ServerpodFeature.START)) {
            ServerpodLaunchMode.START
        } else {
            ServerpodLaunchMode.ENTRY_POINT
        }

    /** Distinct per mode, so the three can coexist in the Run menu. */
    private fun configurationName(layout: ServerpodLayout, launchMode: ServerpodLaunchMode): String =
        when (launchMode) {
            ServerpodLaunchMode.START -> "${layout.projectName} full stack"
            ServerpodLaunchMode.ENTRY_POINT -> "${layout.projectName} server"
            ServerpodLaunchMode.DATABASE -> "${layout.projectName} database"
        }

    private fun configurationType() =
        ConfigurationTypeUtil.findConfigurationType(ServerpodRunConfigurationType::class.java)

    private fun onEdt(project: Project, block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) block()
        }, project.disposed)
    }
}
