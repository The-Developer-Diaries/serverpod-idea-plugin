package dev.serverpod.idea.project

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * The three-package workspace that `serverpod create` produces. Only the server
 * package is guaranteed to exist; the `mini` and `module` templates omit some of
 * the others.
 */
data class ServerpodLayout(
    val projectName: String,
    val root: Path,
    val serverDir: Path,
    val clientDir: Path?,
    val flutterDir: Path?,
    val dockerComposeFile: Path?,
    val migrationsDir: Path?,
    /** Script names from the server package's `serverpod/scripts` section. */
    val scripts: List<String>,
) {
    val hasDocker: Boolean get() = dockerComposeFile != null

    val hasMigrations: Boolean get() = migrationsDir != null

    val serverEntryPoint: Path get() = serverDir.resolve("bin/main.dart")

    companion object {

        private const val SERVER_SUFFIX = "_server"

        /**
         * Detects a Serverpod workspace at [basePath], which may either be the
         * workspace root or the server package itself (when a user opens just
         * the server directory).
         */
        fun detect(basePath: Path): ServerpodLayout? {
            if (!basePath.isDirectory()) return null

            if (isServerPackage(basePath)) {
                return from(basePath)
            }

            val serverDir = runCatching {
                Files.list(basePath).use { stream ->
                    stream.filter { isServerPackage(it) }
                        .sorted(compareBy { it.name })
                        .findFirst()
                        .orElse(null)
                }
            }.getOrNull() ?: return null

            return from(serverDir)
        }

        private fun from(serverDir: Path): ServerpodLayout {
            val projectName = serverDir.name.removeSuffix(SERVER_SUFFIX)
            val root = serverDir.parent ?: serverDir

            return ServerpodLayout(
                projectName = projectName,
                root = root,
                serverDir = serverDir,
                clientDir = root.resolve("${projectName}_client").takeIf { it.isDirectory() },
                flutterDir = root.resolve("${projectName}_flutter").takeIf { it.isDirectory() },
                dockerComposeFile = listOf("docker-compose.yaml", "docker-compose.yml")
                    .map { serverDir.resolve(it) }
                    .firstOrNull { it.isRegularFile() },
                migrationsDir = serverDir.resolve("migrations").takeIf { it.isDirectory() },
                scripts = ServerpodScripts.namesIn(serverDir.resolve("pubspec.yaml")),
            )
        }

        private fun isServerPackage(candidate: Path): Boolean {
            if (!candidate.isDirectory() || !candidate.name.endsWith(SERVER_SUFFIX)) return false
            if (!candidate.resolve("pubspec.yaml").isRegularFile()) return false

            // The generator config is the most reliable marker, but the mini
            // template can be trimmed down, so fall back to the dependency.
            if (candidate.resolve("config/generator.yaml").isRegularFile()) return true

            return runCatching {
                Files.readString(candidate.resolve("pubspec.yaml")).contains("serverpod")
            }.getOrDefault(false)
        }
    }
}
