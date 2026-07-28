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

        if (!Path.of(serverDir).resolve(ENTRY_POINT).isRegularFile()) {
            throw RuntimeConfigurationError("$ENTRY_POINT was not found in $serverDir.")
        }

        if (CliTool.DART.resolve() == null) {
            throw RuntimeConfigurationError(
                "The Dart SDK was not found. Set its path in Settings | Tools | Serverpod."
            )
        }
    }

    private fun buildCommandLine(): GeneralCommandLine {
        val serverDir = options.serverDir?.takeIf { it.isNotBlank() }
            ?: throw ExecutionException("The Serverpod server package is not set.")

        val arguments = buildList {
            add("run")
            add(ENTRY_POINT)
            add("--mode")
            add(ServerpodRunMode.from(options.runMode).cliValue)
            if (options.applyMigrations) add("--apply-migrations")
            if (options.applyRepairMigration) add("--apply-repair-migration")
            options.extraArguments
                ?.takeIf { it.isNotBlank() }
                ?.let { addAll(ParametersListUtil.parse(it)) }
        }

        return ServerpodCommand.commandLine(CliTool.DART, Path.of(serverDir), arguments)
            ?: throw ExecutionException(
                "The Dart SDK was not found. Set its path in Settings | Tools | Serverpod."
            )
    }

    companion object {
        const val ENTRY_POINT = "bin/main.dart"
    }
}
