package dev.serverpod.idea.project

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ServerpodDatabaseTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reads the embedded database a Serverpod 4 project is created with`() {
        val server = serverWith(
            """
            database:
              host: localhost
              port: 8090
              name: demoapp
              user: postgres
              dataPath: .serverpod/development/pgdata
            """.trimIndent(),
        )

        assertEquals(ServerpodDatabaseMode.EMBEDDED, ServerpodDatabase.modeOf(server, null))
    }

    @Test
    fun `prefers the embedded database over a Compose file left from an upgrade`() {
        val server = serverWith("database:\n  dataPath: .serverpod/development/pgdata\n")

        assertEquals(
            ServerpodDatabaseMode.EMBEDDED,
            ServerpodDatabase.modeOf(server, server.resolve("docker-compose.yaml")),
        )
    }

    @Test
    fun `reads the SQLite dialect`() {
        val server = serverWith("database:\n  filePath: demoapp_dev.db\n")

        assertEquals(ServerpodDatabaseMode.SQLITE, ServerpodDatabase.modeOf(server, null))
    }

    @Test
    fun `falls back to Compose for a project that sets neither path`() {
        val server = serverWith("database:\n  host: localhost\n  port: 8090\n  user: postgres\n")

        assertEquals(
            ServerpodDatabaseMode.DOCKER,
            ServerpodDatabase.modeOf(server, server.resolve("docker-compose.yaml")),
        )
    }

    @Test
    fun `calls a database with nowhere local to run external`() {
        val server = serverWith("database:\n  host: db.example.com\n  port: 5432\n")

        assertEquals(ServerpodDatabaseMode.EXTERNAL, ServerpodDatabase.modeOf(server, null))
    }

    @Test
    fun `reports no database when the config declares none`() {
        val server = serverWith("apiServer:\n  port: 8080\n")

        assertEquals(ServerpodDatabaseMode.NONE, ServerpodDatabase.modeOf(server, null))
    }

    @Test
    fun `reports no database when there is no config at all`() {
        val server = Files.createDirectories(tempDir.resolve("bare_server"))

        assertEquals(ServerpodDatabaseMode.NONE, ServerpodDatabase.modeOf(server, null))
    }

    @Test
    fun `survives a config that is mid-edit`() {
        val server = serverWith("database:\n  dataPath: [unclosed\n")

        assertEquals(ServerpodDatabaseMode.NONE, ServerpodDatabase.modeOf(server, null))
    }

    @Test
    fun `ignores a commented-out dataPath`() {
        // The generated config comments out the keys a project has not opted into,
        // and a comment must not read as a value.
        val server = serverWith("database:\n  host: localhost\n  #dataPath: .serverpod/dev/pgdata\n")

        assertEquals(ServerpodDatabaseMode.EXTERNAL, ServerpodDatabase.modeOf(server, null))
    }

    private fun serverWith(config: String): Path {
        val server = Files.createDirectories(tempDir.resolve("demoapp_server/config"))
        Files.writeString(server.resolve("development.yaml"), config)
        return server.parent
    }
}
