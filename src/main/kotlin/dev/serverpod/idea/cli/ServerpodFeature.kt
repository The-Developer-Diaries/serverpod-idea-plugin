package dev.serverpod.idea.cli

/**
 * A capability whose availability depends on the Serverpod CLI installed on the
 * machine, so the plugin's surface follows the CLI rather than being fixed at
 * build time.
 *
 * The plugin never pins a Serverpod version. Everything here is gated on what
 * [CliVersions] read from `serverpod version`, which means a feature appears the
 * moment the user upgrades and disappears again if they roll back.
 */
enum class ServerpodFeature(
    private val since: ServerpodVersion? = null,
    private val removedIn: ServerpodVersion? = null,
) {

    /** `serverpod start`: server, database, and Flutter apps in one hot-reloading process. */
    START(since = ServerpodVersion.V4),

    /** Embedded PostgreSQL, run from `dataPath` instead of Docker. */
    EMBEDDED_DATABASE(since = ServerpodVersion.V4),

    /** Agent skills and MCP servers, installed by re-running `serverpod create`. */
    AGENT_TOOLING(since = ServerpodVersion.V4),

    /** The `fullstack` template, and `server` narrowing to mean server-only. */
    FULLSTACK_TEMPLATE(since = ServerpodVersion.V4),

    /** `--database`, `--redis`, `--auth`, `--webapp`, `--website` on `serverpod create`. */
    CREATE_FEATURE_FLAGS(since = ServerpodVersion.V4),

    /** `create-migration --empty`, for a migration to hand-write. */
    EMPTY_MIGRATION(since = ServerpodVersion.V4),

    /** The `mini` template, replaced by `--template fullstack --no-database`. */
    MINI_TEMPLATE(removedIn = ServerpodVersion.V4),
    ;

    /**
     * Whether [version] has this feature.
     *
     * An unknown version means the CLI has not been probed yet or did not
     * answer. That resolves to the surface the plugin has always had, so a slow
     * or broken probe never invents a command the CLI cannot run.
     */
    fun isSupportedBy(version: ServerpodVersion?): Boolean {
        if (version == null) return since == null

        if (since != null && !version.hasSurfaceOf(since)) return false
        if (removedIn != null && version.hasSurfaceOf(removedIn)) return false

        return true
    }
}
