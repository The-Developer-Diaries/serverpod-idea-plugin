package dev.serverpod.idea.cli

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Reports the version of each resolved executable, so the tool actually being
 * driven is visible rather than assumed.
 *
 * Versions are read by running the tool, which is far too slow for the EDT, so
 * callers detect from a coroutine and read [cached] when painting.
 */
@Service(Service.Level.APP)
class CliVersions(private val coroutineScope: CoroutineScope) {

    private val versions = ConcurrentHashMap<CliTool, String>()

    /** Non-blocking: the last detected version, or null if it has not been read yet. */
    fun cached(tool: CliTool): String? = versions[tool]

    /** Runs the tool to read its version without blocking an IDE worker. */
    suspend fun detect(tool: CliTool): String? {
        val commandLine = ServerpodCommand.commandLine(
            tool,
            Path.of(System.getProperty("user.home")),
            listOf("--version"),
        ) ?: return null

        val output = try {
            withTimeout(TIMEOUT_MS) { captureProcess(commandLine) }
        } catch (_: TimeoutCancellationException) {
            return null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        if (output.exitCode != 0) return null

        // Some SDKs report the version on stderr depending on the release.
        val text = output.stdout.ifBlank { output.stderr }
        val version = tool.versionPattern.find(text)?.groupValues?.getOrNull(1)

        return version?.also { versions[tool] = it }
    }

    /** Reads whatever is still unknown, so repeat callers do not relaunch every tool. */
    suspend fun detectAll() {
        for (tool in CliTool.entries) {
            currentCoroutineContext().ensureActive()
            if (cached(tool) == null) detect(tool)
        }
    }

    /** Starts version detection in this app service's lifecycle-bound scope. */
    fun detectAllAsync(onDetected: suspend () -> Unit): Job = coroutineScope.launch {
        detectAll()
        onDetected()
    }

    /** Drops the cache so the next detection re-reads, after an install or a path change. */
    fun invalidate() = versions.clear()

    /** "Dart 3.12.2 · Flutter 3.44.8", skipping anything not resolved yet. */
    fun summary(tools: List<CliTool>): String =
        tools.mapNotNull { tool -> cached(tool)?.let { "${tool.displayName.removeSuffix(" SDK")} $it" } }
            .joinToString(" \u00b7 ")

    companion object {
        private const val TIMEOUT_MS = 15_000L

        fun getInstance(): CliVersions = service()
    }
}
