package dev.serverpod.idea.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Caches the detected [ServerpodLayout] so that `AnAction.update()` and other
 * EDT callers never touch the file system.
 */
@Service(Service.Level.PROJECT)
class ServerpodProjectService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {

    @Volatile
    private var layout: ServerpodLayout? = null

    private val refreshInProgress = AtomicBoolean(false)

    init {
        VirtualFileManager.getInstance().addAsyncFileListener(
            coroutineScope,
            ServerpodVfsListener(project, coroutineScope),
        )
    }

    /** Non-blocking: returns the last detection result, or null before the first scan. */
    fun layout(): ServerpodLayout? = layout

    val isServerpodProject: Boolean get() = layout != null

    /** Runs detection on the calling thread. Must not be called on the EDT. */
    fun detectNow(): ServerpodLayout? {
        val basePath = project.basePath?.let { Path.of(it) }
        val detected = basePath?.let { ServerpodLayout.detect(it) }

        val changed = detected != layout
        layout = detected
        if (changed && !project.isDisposed) {
            LOG.info("Serverpod workspace in '${project.name}': ${detected?.serverDir ?: "none detected"}")
            project.messageBus.syncPublisher(TOPIC).layoutChanged(detected)
        }
        return detected
    }

    /** Schedules detection on a background thread, coalescing concurrent requests. */
    fun scheduleRefresh() {
        if (project.isDisposed) return
        if (!refreshInProgress.compareAndSet(false, true)) return

        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (!project.isDisposed) detectNow()
            } finally {
                refreshInProgress.set(false)
            }
        }
    }

    /** Creates work owned by a shorter-lived project UI component. */
    internal fun createChildScope(name: String): CoroutineScope = coroutineScope.childScope(name)

    fun interface LayoutListener {
        fun layoutChanged(layout: ServerpodLayout?)
    }

    companion object {
        private val LOG = logger<ServerpodProjectService>()

        @JvmField
        val TOPIC: Topic<LayoutListener> = Topic.create("Serverpod layout", LayoutListener::class.java)

        fun getInstance(project: Project): ServerpodProjectService = project.service()

        fun layoutOf(project: Project?): ServerpodLayout? =
            project?.takeUnless { it.isDisposed }?.let { getInstance(it).layout() }
    }
}
