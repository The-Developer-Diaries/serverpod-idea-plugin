package dev.serverpod.idea.cli

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.runBlockingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs [block] on the EDT while preserving the caller's coroutine cancellation. */
internal suspend fun <T> onEdt(block: () -> T): T = withContext(Dispatchers.EDT) { block() }

/**
 * Synchronous bridge retained for the console service, whose API must create a
 * Swing component before it can return it.
 */
internal fun <T> computeOnEdt(block: () -> T): T {
    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) return block()

    return runBlockingCancellable { onEdt(block) }
}
