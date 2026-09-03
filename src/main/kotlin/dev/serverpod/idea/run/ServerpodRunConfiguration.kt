package dev.serverpod.idea.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import dev.serverpod.idea.cli.CliTool
import dev.serverpod.idea.cli.CliVersions
import dev.serverpod.idea.cli.ServerpodCommand
import java.nio.file.Path
import kotlin.io.path.isRegularFile

class ServerpodRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : RunConfigurationBase<ServerpodRunConfigurationOptions>(project, factory, name) {

    public override fun getOptions(): ServerpodRunConfigurationOptions =
        super.getOptions() as ServerpodRunConfigurationOptions

    private val launchMode: ServerpodLaunchMode get() = ServerpodLaunchMode.from(options.launchMode)

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        ServerpodRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment) =
        object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                val handler = KillableColoredProcessHandler(buildCommandLine())
                ProcessTerminatedListener.attach(handler, project)
                return handler
            }
        }

    override fun checkConfiguration() {
        val serverDir = options.serverDir?.takeIf { it.isNotBlank() }
            ?: throw RuntimeConfigurationError("Specify the Serverpod server package.")

        val mode = launchMode
        mode.requires?.let { feature ->
            if (!CliVersions.getInstance().supports(feature)) {
                throw RuntimeConfigurationError(
                    "${mode.displayName} needs Serverpod 4 or newer. " +
                        "Update the CLI from Settings | Tools | Serverpod.",
                )
            }
        }

        when (mode) {
            ServerpodLaunchMode.ENTRY_POINT -> {
                if (!Path.of(serverDir).resolve(ENTRY_POINT).isRegularFile()) {
                    throw RuntimeConfigurationError("$ENTRY_POINT was not found in $serverDir.")
                }
                requireTool(CliTool.DART)
            }

            ServerpodLaunchMode.START, ServerpodLaunchMode.DATABASE -> requireTool(CliTool.SERVERPOD)
        }
    }

    private fun requireTool(tool: CliTool) {
        if (tool.resolve() == null) {
            throw RuntimeConfigurationError(
                "The ${tool.displayName} was not found. Set its path in Settings | Tools | Serverpod.",
            )
        }
    }

    private fun buildCommandLine(): GeneralCommandLine {
        val serverDir = options.serverDir?.takeIf { it.isNotBlank() }
            ?: throw ExecutionException("The Serverpod server package is not set.")

        val mode = launchMode
        val tool = if (mode == ServerpodLaunchMode.ENTRY_POINT) CliTool.DART else CliTool.SERVERPOD

        val arguments = ServerpodLaunchArguments.of(
            launchMode = mode,
            runMode = ServerpodRunMode.from(options.runMode),
            applyMigrations = options.applyMigrations,
            applyRepairMigration = options.applyRepairMigration,
            launchFlutterApps = options.launchFlutterApps,
            extraArguments = options.extraArguments
                ?.takeIf { it.isNotBlank() }
                ?.let(ParametersListUtil::parse)
                .orEmpty(),
        )

        return ServerpodCommand.commandLine(tool, Path.of(serverDir), arguments)
            ?: throw ExecutionException(
                "The ${tool.displayName} was not found. Set its path in Settings | Tools | Serverpod.",
            )
    }

    companion object {
        const val ENTRY_POINT = ServerpodLaunchArguments.ENTRY_POINT
    }
}
