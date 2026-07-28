package dev.serverpod.idea.cli

import com.intellij.execution.configurations.GeneralCommandLine
import dev.serverpod.idea.project.ServerpodLayout
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/** A single external command, described independently of how it gets executed. */
data class ServerpodCommand(
    val title: String,
    val tool: CliTool,
    val workDir: Path,
    val arguments: List<String>,
    val successMessage: String? = null,
) {
    companion object {

        /** Global flags that keep the CLI from blocking on prompts inside the IDE. */
        val NON_INTERACTIVE = listOf("--no-analytics", "--no-interactive")

        fun generate(layout: ServerpodLayout) = ServerpodCommand(
            title = "serverpod generate",
            tool = CliTool.SERVERPOD,
            workDir = layout.serverDir,
            arguments = NON_INTERACTIVE + "generate",
            successMessage = "Regenerated the client and serialization code.",
        )

        fun runScript(layout: ServerpodLayout, script: String) = ServerpodCommand(
            title = "serverpod run $script",
            tool = CliTool.SERVERPOD,
            workDir = layout.serverDir,
            arguments = NON_INTERACTIVE + listOf("run", script),
        )

        fun commandLine(tool: CliTool, workDir: Path, arguments: List<String>): GeneralCommandLine? {
            val executable = tool.resolve() ?: return null

            return GeneralCommandLine(executable.toString())
                .withParameters(arguments)
                .withWorkingDirectory(workDir)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                .withCharset(StandardCharsets.UTF_8)
                .withExtendedPath()
        }

        /**
         * An IDE started from the Dock inherits a minimal `PATH`, which breaks the
         * Serverpod CLI when it shells out to `dart`. Put every tool we resolved on
         * the child's `PATH` so nested lookups succeed.
         */
        private fun GeneralCommandLine.withExtendedPath(): GeneralCommandLine {
            val toolDirs = CliTool.entries
                .mapNotNull { it.resolve()?.parent?.toString() }
                .distinct()
            if (toolDirs.isEmpty()) return this

            val inherited = parentEnvironment["PATH"].orEmpty()
            val combined = (toolDirs + inherited.split(File.pathSeparator))
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(File.pathSeparator)

            return withEnvironment("PATH", combined)
        }
    }

    fun toCommandLine(): GeneralCommandLine? = commandLine(tool, workDir, arguments)
}
