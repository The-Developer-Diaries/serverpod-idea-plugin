package dev.serverpod.idea.project

import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

/**
 * Where a server package keeps its development database.
 *
 * Serverpod 4 made this a real choice: setting `dataPath` on the `database`
 * section runs PostgreSQL as a child of the server process, `filePath` selects
 * the new SQLite dialect, and Docker is only used when neither is present.
 */
enum class ServerpodDatabaseMode(val displayName: String) {

    /** No `database` section, so the project runs without one. */
    NONE("none"),

    /** `dataPath` is set, so the server runs PostgreSQL itself. */
    EMBEDDED("embedded PostgreSQL"),

    /** `filePath` is set, so the server uses the SQLite dialect. */
    SQLITE("SQLite"),

    /** Neither key is set, but there is a Compose file to bring PostgreSQL up. */
    DOCKER("Docker Compose"),

    /** A database is configured but is none of the above. */
    EXTERNAL("external"),
}

/**
 * Reads the `database` section of a server package's development config.
 *
 * Only what the IDE has to act on is taken: whether a database is configured,
 * and where its data lives. Everything else, including the password files,
 * stays the CLI's business.
 */
object ServerpodDatabase {

    /** The run mode the IDE cares about; the others are not run locally. */
    const val DEVELOPMENT_CONFIG = "config/development.yaml"

    /**
     * How [serverDir] stores its development data, given whether the package has
     * a [dockerComposeFile].
     */
    fun modeOf(serverDir: Path, dockerComposeFile: Path?): ServerpodDatabaseMode {
        val database = databaseSection(serverDir.resolve(DEVELOPMENT_CONFIG))
            ?: return ServerpodDatabaseMode.NONE

        return when {
            database.hasValue("dataPath") -> ServerpodDatabaseMode.EMBEDDED
            database.hasValue("filePath") -> ServerpodDatabaseMode.SQLITE
            dockerComposeFile != null -> ServerpodDatabaseMode.DOCKER
            else -> ServerpodDatabaseMode.EXTERNAL
        }
    }

    private fun Map<*, *>.hasValue(key: String): Boolean =
        get(key)?.toString()?.isNotBlank() == true

    private fun databaseSection(configFile: Path): Map<*, *>? {
        if (!Files.isRegularFile(configFile)) return null

        val document = runCatching {
            Files.newBufferedReader(configFile).use { Yaml().load<Any?>(it) }
        }.getOrNull()

        return (document as? Map<*, *>)?.get("database") as? Map<*, *>
    }
}
