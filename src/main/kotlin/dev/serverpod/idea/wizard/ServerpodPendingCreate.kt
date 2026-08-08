package dev.serverpod.idea.wizard

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicReference

/**
 * Carries the wizard's request across to the startup activity that acts on it.
 *
 * The wizard finishes describing the project before the project has finished
 * opening, and the CLI cannot run until it has. The platform's own hook for that
 * gap is internal API, so the request waits here instead and is collected from a
 * [com.intellij.openapi.startup.ProjectActivity].
 */
@Service(Service.Level.PROJECT)
class ServerpodPendingCreate {

    private val pending = AtomicReference<ServerpodCreateRequest?>(null)

    fun submit(request: ServerpodCreateRequest) {
        pending.set(request)
    }

    /** Clears as it reads, so a request is acted on once and not on later opens. */
    fun consume(): ServerpodCreateRequest? = pending.getAndSet(null)

    companion object {

        fun getInstance(project: Project): ServerpodPendingCreate = project.service()
    }
}
