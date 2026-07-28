package dev.serverpod.idea.project

import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads the script names out of the `serverpod/scripts` section of a server
 * package's `pubspec.yaml`.
 *
 * Only the names are needed: `serverpod run <name>` executes them, so the CLI
 * stays responsible for choosing the shell and resolving per-platform variants.
 */
object ServerpodScripts {

    fun namesIn(pubspec: Path): List<String> {
        if (!Files.isRegularFile(pubspec)) return emptyList()

        val document = runCatching {
            Files.newBufferedReader(pubspec).use { Yaml().load<Any?>(it) }
        }.getOrNull()

        val serverpod = (document as? Map<*, *>)?.get("serverpod") as? Map<*, *> ?: return emptyList()
        val scripts = serverpod["scripts"] as? Map<*, *> ?: return emptyList()

        return scripts.keys.filterIsInstance<String>().filter { it.isNotBlank() }
    }
}
