package dev.serverpod.idea.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import dev.serverpod.idea.project.ServerpodLayout

object ServerpodRunConfigurations {

    /**
     * Reuses the configuration pointing at this server package, or adds one.
     * [applyMigrations] only affects a configuration that has to be created.
     */
    fun findOrCreate(
        project: Project,
        layout: ServerpodLayout,
        applyMigrations: Boolean = layout.hasMigrations,
    ): RunnerAndConfigurationSettings {
        val runManager = RunManager.getInstance(project)
        val serverDir = layout.serverDir.toString()

        runManager.getConfigurationSettingsList(configurationType())
            .firstOrNull { (it.configuration as? ServerpodRunConfiguration)?.options?.serverDir == serverDir }
            ?.let { return it }

        val settings = runManager.createConfiguration(
            "${layout.projectName} server",
            configurationType().configurationFactories.first(),
        )

        (settings.configuration as ServerpodRunConfiguration).options.also {
            it.serverDir = serverDir
            it.applyMigrations = applyMigrations && layout.hasMigrations
        }

        runManager.addConfiguration(settings)
        return settings
    }

    fun createDefault(
        project: Project,
        layout: ServerpodLayout,
        applyMigrations: Boolean = layout.hasMigrations,
    ) = onEdt(project) {
        RunManager.getInstance(project).selectedConfiguration = findOrCreate(project, layout, applyMigrations)
    }

    fun runServer(project: Project, layout: ServerpodLayout) = onEdt(project) {
        val settings = findOrCreate(project, layout)
        RunManager.getInstance(project).selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }

    private fun configurationType() =
        ConfigurationTypeUtil.findConfigurationType(ServerpodRunConfigurationType::class.java)

    private fun onEdt(project: Project, block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) block()
        }, project.disposed)
    }
}
