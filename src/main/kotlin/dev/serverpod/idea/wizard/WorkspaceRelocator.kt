package dev.serverpod.idea.wizard

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * File moves used when `serverpod create` had to run in a scratch directory.
 * Kept free of IDE APIs so the behaviour can be tested directly.
 */
object WorkspaceRelocator {

    /**
     * Moves everything under [source] into [target], merging directories and
     * leaving files that only exist in [target] (such as `.idea`) untouched.
     */
    @Throws(IOException::class)
    fun mergeInto(source: Path, target: Path) {
        if (!source.isDirectory()) throw IOException("$source was not generated")

        Files.createDirectories(target)
        Files.list(source).use { children ->
            children.forEach { child ->
                val destination = target.resolve(child.name)
                when {
                    // Nothing in the way, so the whole subtree moves at once.
                    !Files.exists(destination) ->
                        Files.move(child, destination, StandardCopyOption.REPLACE_EXISTING)

                    child.isDirectory() && destination.isDirectory() -> mergeInto(child, destination)
                    else -> Files.move(child, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        Files.deleteIfExists(source)
    }

    /** Best-effort recursive delete; failures are not worth interrupting the user for. */
    fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return

        runCatching {
            Files.walk(path).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    /**
     * Pub records absolute paths in `.dart_tool`, so those caches are useless
     * once the workspace has been moved.
     */
    fun deleteStalePubCaches(projectRoot: Path) {
        deleteRecursively(projectRoot.resolve(".dart_tool"))

        runCatching {
            Files.list(projectRoot).use { children ->
                children.filter { it.isDirectory() }
                    .forEach { deleteRecursively(it.resolve(".dart_tool")) }
            }
        }
    }
}
