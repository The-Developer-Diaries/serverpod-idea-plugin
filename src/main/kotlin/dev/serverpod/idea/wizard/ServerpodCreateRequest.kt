package dev.serverpod.idea.wizard

import dev.serverpod.idea.cli.ServerpodIde

data class ServerpodCreateRequest(
    val packageName: String,
    val template: ServerpodTemplate,
    /** The Serverpod 4 `create` flags, or null when the CLI predates them. */
    val features: ServerpodCreateFeatures?,
    val startDocker: Boolean,
    val createRunConfiguration: Boolean,
    val applyMigrations: Boolean,
    val openEntryPoint: Boolean,
)

/**
 * What Serverpod 4 lets `create` decide up front. Before 4.0 these were implied
 * by the template, which is why the whole block is optional.
 */
data class ServerpodCreateFeatures(
    val database: Boolean,
    val redis: Boolean,
    val auth: Boolean,
    /** Editors to install agent skills and MCP servers for. */
    val ides: List<ServerpodIde>,
) {
    /** The CLI rejects `--auth` without a database, so the pair is resolved here. */
    val effectiveAuth: Boolean get() = auth && database

    fun toArguments(): List<String> = buildList {
        add(if (database) "--database" else "--no-database")
        add(if (redis) "--redis" else "--no-redis")
        add(if (effectiveAuth) "--auth" else "--no-auth")

        // An empty list would leave the CLI's own default in place, which
        // configures Claude, Cursor, and VS Code.
        val selected = ides.ifEmpty { listOf(ServerpodIde.NONE) }
        selected.forEach { ide ->
            add("--ide")
            add(ide.cliValue)
        }
    }
}
