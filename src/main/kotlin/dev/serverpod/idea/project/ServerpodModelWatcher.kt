package dev.serverpod.idea.project

import com.intellij.execution.ExecutionManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.serverpod.idea.ServerpodNotifications
import dev.serverpod.idea.cli.ServerpodCommand
import dev.serverpod.idea.cli.ServerpodCommandRunner
import dev.serverpod.idea.run.ServerpodLaunchMode
import dev.serverpod.idea.run.ServerpodRunConfiguration
import dev.serverpod.idea.settings.ServerpodSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

/**
 * Closes the loop between editing a model and the generated code catching up.
 *
 * Regenerating is opt-in because it spawns a process on every save; until it is
 * turned on, a changed model is pointed out once per session instead.
 */
@Service(Service.Level.PROJECT)
class ServerpodModelWatcher(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {

    private val pending = AtomicReference<Job?>(null)
    private val running = AtomicBoolean(false)
    private val queued = AtomicBoolean(false)
    private val prompted = AtomicBoolean(false)

    fun onModelFilesChanged() {
        if (project.isDisposed || ServerpodProjectService.getInstance(project).layout() == null) return

        // `serverpod start` watches the models itself and hot-reloads the result.
        // A second generate would race it over the same output files.
        if (isStartRunning()) return

        if (!ServerpodSettings.getInstance().generateOnModelChange) {
            promptOnce()
            return
        }

        schedule()
    }

    private fun isStartRunning(): Boolean = ExecutionManager.getInstance(project)
        .getRunningDescriptors { settings ->
            val configuration = settings.configuration as? ServerpodRunConfiguration
            configuration != null &&
                ServerpodLaunchMode.from(configuration.options.launchMode) == ServerpodLaunchMode.START
        }
        .isNotEmpty()

    /** Saving several models at once, or a reformat, should still produce one run. */
    private fun schedule() {
        val next = coroutineScope.launch(start = CoroutineStart.LAZY) {
            delay(DEBOUNCE_MS)
            pending.compareAndSet(coroutineContext[Job], null)
            generate()
        }
        pending.getAndSet(next)?.cancel()
        next.start()
    }

    private suspend fun generate() {
        if (project.isDisposed) return
        val layout = ServerpodProjectService.getInstance(project).layout() ?: return

        // Two concurrent runs would write the same files, so a change arriving
        // mid-run is deferred rather than started alongside.
        if (!running.compareAndSet(false, true)) {
            queued.set(true)
            return
        }

        try {
            ServerpodCommandRunner.run(project, ServerpodCommand.generate(layout))
        } finally {
            running.set(false)
            if (queued.compareAndSet(true, false)) schedule()
        }
    }

    private fun promptOnce() {
        if (!prompted.compareAndSet(false, true)) return

        ServerpodNotifications.actionable(
            project,
            "Serverpod models changed",
            "The generated client and serialization code is now out of date.",
            "Generate" to { generateNow() },
            "Generate on Every Change" to {
                ServerpodSettings.getInstance().generateOnModelChange = true
                generateNow()
            },
        )
    }

    private fun generateNow() {
        pending.getAndSet(null)?.cancel()
        coroutineScope.launch { generate() }
    }

    companion object {
        private const val DEBOUNCE_MS = 1_500L

        /** Serverpod's model files, which the CLI reads to produce everything else. */
        private val MODEL_SUFFIXES = listOf(".spy.yaml", ".spy.yml")

        fun getInstance(project: Project): ServerpodModelWatcher = project.service()

        fun isModelFile(path: String): Boolean = MODEL_SUFFIXES.any { path.endsWith(it) }
    }
}
