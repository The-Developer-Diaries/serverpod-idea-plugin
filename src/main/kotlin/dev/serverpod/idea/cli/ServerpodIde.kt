package dev.serverpod.idea.cli

/**
 * An editor `serverpod create` can install agent skills and register its MCP
 * servers for, through the `--ide` option added in Serverpod 4.
 *
 * The CLI owns the writing. Each editor expects its own config file shape, and
 * keeping that knowledge on the Serverpod side means the plugin does not have to
 * track any of them.
 */
enum class ServerpodIde(val cliValue: String, val displayName: String) {

    // JetBrains IDEs are not among the CLI's targets, so a user of this plugin
    // is picking the agent they use alongside it.
    CLAUDE("claude", "Claude Code"),
    CURSOR("cursor", "Cursor"),
    VS_CODE("vscode", "VS Code"),
    CODEX("codex", "Codex"),
    OPEN_CODE("opencode", "OpenCode"),
    ANTIGRAVITY("antigravity", "Antigravity"),

    /** Explicitly opts out, and the CLI rejects it alongside any other value. */
    NONE("none", "None"),
    ;

    override fun toString(): String = displayName

    companion object {
        /** What the CLI configures when `--ide` is not given. */
        val DEFAULTS = listOf(CLAUDE, CURSOR, VS_CODE)

        fun fromCliValues(values: String?): List<ServerpodIde> =
            values?.split(',')
                ?.mapNotNull { value -> entries.firstOrNull { it.cliValue == value.trim() } }
                .orEmpty()
    }
}
