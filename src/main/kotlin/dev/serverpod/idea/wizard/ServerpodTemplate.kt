package dev.serverpod.idea.wizard

enum class ServerpodTemplate(
    val cliValue: String,
    val displayName: String,
    val description: String,
) {
    // Descriptions stay under the comment wrap width so the row keeps a
    // constant height as the selection changes.
    SERVER(
        "server",
        "Server",
        "Server, client, and Flutter app with PostgreSQL and Redis.",
    ),
    MINI(
        "mini",
        "Mini",
        "Server, client, and Flutter app with no database.",
    ),
    MODULE(
        "module",
        "Module",
        "A reusable module for other Serverpod projects.",
    );

    /** Only the full server template ships a `docker-compose.yaml`. */
    val hasDatabase: Boolean get() = this == SERVER

    override fun toString(): String = displayName

    companion object {
        fun fromCliValue(value: String?): ServerpodTemplate? =
            entries.firstOrNull { it.cliValue == value }
    }
}
