package dev.serverpod.idea.cli

import com.intellij.openapi.application.ApplicationManager

/** Runs [block] on the EDT and returns its result, blocking the caller if needed. */
internal fun <T> computeOnEdt(block: () -> T): T {
    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) return block()

    var result: Result<T>? = null
    application.invokeAndWait { result = runCatching(block) }
    return result!!.getOrThrow()
}
