package dev.serverpod.idea.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil
import com.jetbrains.lang.dart.sdk.DartSdkUtil
import dev.serverpod.idea.ServerpodNotifications
import dev.serverpod.idea.cli.CliTool
import java.nio.file.Files
import java.nio.file.Path

/**
 * Teaches the IDE that a generated workspace is Dart code.
 *
 * The New Project wizard produces a project with no modules, so without this the
 * files sit outside any content root and the analyzer never looks at them. The
 * Dart plugin owns the SDK library and the package resolution; all that is needed
 * here is a module to attach them to.
 */
object ServerpodDartSupport {

    sealed interface Result {
        data class Configured(val sdkHome: Path, val sdkVersion: String?) : Result

        /** Dart resolved as an executable but not to anything that looks like an SDK. */
        data object NoSdkFound : Result
    }

    fun isConfigured(project: Project): Boolean =
        ApplicationManager.getApplication().runReadAction<Boolean> { DartSdk.getDartSdk(project) != null }

    /** Runs [configure] in the background and reports the outcome. */
    fun configureInBackground(project: Project, layout: ServerpodLayout) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Configuring the Dart SDK", false) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true

                    when (val result = configure(project, layout)) {
                        is Result.Configured -> ServerpodNotifications.info(
                            project,
                            "Dart SDK configured",
                            describe(result),
                        )

                        Result.NoSdkFound -> ServerpodNotifications.error(
                            project,
                            "Dart SDK not found",
                            "Set the Dart or Flutter path in Settings | Tools | Serverpod, then try again.",
                        )
                    }
                }
            }
        )
    }

    fun describe(result: Result.Configured): String {
        val version = result.sdkVersion?.let { " (version $it)" }.orEmpty()
        return "The workspace now uses the SDK at ${result.sdkHome}$version."
    }

    /**
     * Adds the Dart SDK to the project and enables it on a module covering
     * [layout]. Safe to call repeatedly. Must not be called on the EDT.
     */
    fun configure(project: Project, layout: ServerpodLayout): Result {
        val sdkHome = findSdkHome() ?: return Result.NoSdkFound

        WriteAction.runAndWait<RuntimeException> {
            if (project.isDisposed) return@runAndWait

            DartSdkLibUtil.ensureDartSdkConfigured(project, sdkHome.toString())

            val module = moduleFor(project, layout)
            addContentRoot(module, layout)
            DartSdkLibUtil.enableDartSdk(module)
        }

        LOG.info("Configured the Dart SDK at $sdkHome for '${project.name}'")
        return Result.Configured(sdkHome, DartSdkUtil.getSdkVersion(sdkHome.toString()))
    }

    /**
     * The SDK root is the directory holding `bin` and `lib`, which is not where
     * either executable lives: `dart` may be a package-manager symlink, and
     * Flutter keeps its copy under `bin/cache`.
     */
    fun findSdkHome(): Path? {
        CliTool.DART.resolve()?.let { dart ->
            realPath(dart).parent?.parent?.takeIf { it.isSdkHome() }?.let { return it }
        }

        CliTool.FLUTTER.resolve()?.let { flutter ->
            realPath(flutter).parent?.resolve("cache/dart-sdk")?.takeIf { it.isSdkHome() }?.let { return it }
        }

        return null
    }

    private fun Path.isSdkHome(): Boolean = DartSdkUtil.isDartSdkHome(toString())

    private fun realPath(path: Path): Path = runCatching { path.toRealPath() }.getOrDefault(path)

    /**
     * Reuses a module that already covers the workspace, so running twice does not
     * accumulate them, but leaves any module rooted elsewhere alone.
     */
    private fun moduleFor(project: Project, layout: ServerpodLayout): Module {
        val manager = ModuleManager.getInstance(project)
        val rootUrl = VfsUtilCore.pathToUrl(layout.root.toString())

        manager.modules
            .firstOrNull { rootUrl in ModuleRootManager.getInstance(it).contentRootUrls }
            ?.let { return it }

        val moduleFile = layout.root
            .resolve(Project.DIRECTORY_STORE_FOLDER)
            .resolve("modules")
            .resolve("${layout.projectName}.iml")

        Files.createDirectories(moduleFile.parent)
        return manager.newModule(moduleFile, GENERIC_MODULE_TYPE)
    }

    private fun addContentRoot(module: Module, layout: ServerpodLayout) {
        val rootUrl = VfsUtilCore.pathToUrl(layout.root.toString())

        ModuleRootModificationUtil.updateModel(module) { model ->
            val entry = model.contentEntries.firstOrNull { it.url == rootUrl }
                ?: model.addContentEntry(rootUrl)

            val alreadyExcluded = entry.excludeFolderUrls.toSet()
            excludedPaths(layout)
                .map { VfsUtilCore.pathToUrl(it.toString()) }
                .filterNot { it in alreadyExcluded }
                .forEach { entry.addExcludeFolder(it) }
        }
    }

    /**
     * Build output, listed whether or not it exists yet so that a later build
     * does not need reconfiguring. `web/app` is the compiled Flutter web bundle
     * that `serverpod create` ships inside the server package.
     */
    private fun excludedPaths(layout: ServerpodLayout): List<Path> = buildList {
        listOfNotNull(layout.serverDir, layout.clientDir, layout.flutterDir).forEach { pkg ->
            add(pkg.resolve("build"))
            add(pkg.resolve(".dart_tool/build"))
        }
        add(layout.serverDir.resolve("web/app"))
    }

    /**
     * The id of the platform's generic module type. Anything Java-flavoured would
     * expect a JDK the workspace has no use for.
     */
    private const val GENERIC_MODULE_TYPE = "WEB_MODULE"

    private val LOG = logger<ServerpodDartSupport>()
}
