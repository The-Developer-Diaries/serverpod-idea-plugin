package dev.serverpod.idea.run

import dev.serverpod.idea.cli.ServerpodCommand

/**
 * Builds the argument list for each [ServerpodLaunchMode], kept apart from the
 * run configuration so the shape of the command can be asserted directly.
 */
object ServerpodLaunchArguments {

    /** `bin/main.dart`, relative to the server package. */
    const val ENTRY_POINT = "bin/main.dart"

    fun of(
        launchMode: ServerpodLaunchMode,
        runMode: ServerpodRunMode,
        applyMigrations: Boolean = false,
        applyRepairMigration: Boolean = false,
        launchFlutterApps: Boolean = true,
        extraArguments: List<String> = emptyList(),
    ): List<String> {
        val serverArguments = buildList {
            add("--mode")
            add(runMode.cliValue)
            if (applyMigrations) add("--apply-migrations")
            if (applyRepairMigration) add("--apply-repair-migration")
            addAll(extraArguments)
        }

        return when (launchMode) {
            ServerpodLaunchMode.ENTRY_POINT -> listOf("run", ENTRY_POINT) + serverArguments

            // The TUI drives the terminal with raw-mode escape sequences and the
            // run console is not one. Everything after `--` reaches the server.
            ServerpodLaunchMode.START -> ServerpodCommand.NON_INTERACTIVE +
                listOf("start", "--no-tui") +
                (if (launchFlutterApps) emptyList() else listOf("--no-flutter")) +
                listOf("--") +
                serverArguments

            ServerpodLaunchMode.DATABASE -> ServerpodCommand.NON_INTERACTIVE +
                listOf("database", "start", "--mode", runMode.cliValue) +
                extraArguments
        }
    }
}
