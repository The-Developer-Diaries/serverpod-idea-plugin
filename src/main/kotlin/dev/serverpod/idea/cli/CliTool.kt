package dev.serverpod.idea.cli

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.util.system.OS
import dev.serverpod.idea.settings.ServerpodSettings
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isExecutable

/**
 * An external executable the plugin drives. Resolution order is always
 * user setting, then `PATH`, then the well-known install locations, because
 * an IDE launched from Finder or the Dock inherits a minimal `PATH`.
 */
enum class CliTool(val displayName: String, private val binaryName: String) {

    SERVERPOD("Serverpod CLI", "serverpod") {
        override fun fallbackCandidates(): List<Path> = fallbackPaths(
            listOf(home().resolve(".pub-cache/bin")),
        )

        // Serverpod version: 3.4.11
        override val versionPattern = Regex("""Serverpod version:\s+(\S+)""")
    },

    DART("Dart SDK", "dart") {
        override fun fallbackCandidates(): List<Path> = buildList {
            // The Dart binary always sits next to the Flutter one.
            FLUTTER.executableNamesFor(OS.CURRENT)
                .firstNotNullOfOrNull { PathEnvironmentVariableUtil.findInPath(it)?.toPath()?.parent }
                ?.let { flutterBin -> addAll(fallbackPaths(listOf(flutterBin))) }
            addAll(fallbackPaths(flutterBinDirectories()))
            addAll(
                fallbackPaths(
                    listOf(
                        Path.of("/opt/homebrew/bin"),
                        Path.of("/usr/local/bin"),
                    ),
                ),
            )
        }

        // Dart SDK version: 3.12.2 (stable) (Tue Jun 9 01:11:39 2026 -0700) on "macos_arm64"
        override val versionPattern = Regex("""Dart SDK version:\s+(\S+)""")
    },

    FLUTTER("Flutter SDK", "flutter") {
        override fun fallbackCandidates(): List<Path> = fallbackPaths(flutterBinDirectories())

        // Flutter 3.44.8 • channel stable • https://github.com/flutter/flutter.git
        override val versionPattern = Regex("""Flutter\s+(\S+)""")
    },

    DOCKER("Docker", "docker") {
        override fun fallbackCandidates(): List<Path> = fallbackPaths(
            listOf(
                home().resolve(".docker/bin"),
                Path.of("/usr/local/bin"),
                Path.of("/opt/homebrew/bin"),
                Path.of("/Applications/Docker.app/Contents/Resources/bin"),
            ),
        )

        // Docker version 29.6.1, build 8900f1d
        override val versionPattern = Regex("""Docker version\s+([^,\s]+)""")
    };

    /** Preferred command file name, e.g. `serverpod.bat` on Windows. */
    val executableName: String
        get() = executableNames.first()

    /** Resolution order retains Windows command wrappers before native executables. */
    internal fun executableNamesFor(os: OS): List<String> = buildList {
        if (os == OS.Windows) add("$binaryName.bat")
        add(os.getBinaryName(binaryName))
    }

    abstract fun fallbackCandidates(): List<Path>

    /** Captures the version number from this tool's `--version` output. */
    abstract val versionPattern: Regex

    private fun configuredPath(): String? = when (this) {
        SERVERPOD -> ServerpodSettings.getInstance().serverpodPath
        DART -> ServerpodSettings.getInstance().dartPath
        FLUTTER -> ServerpodSettings.getInstance().flutterPath
        DOCKER -> ServerpodSettings.getInstance().dockerPath
    }

    fun resolve(): Path? {
        configuredPath()?.let { configured ->
            val path = Path.of(configured)
            if (path.isRunnable()) return path
        }

        executableNames
            .firstNotNullOfOrNull { PathEnvironmentVariableUtil.findInPath(it)?.toPath() }
            ?.let { return it }

        return fallbackCandidates().firstOrNull { it.isRunnable() }
    }

    private val executableNames: List<String>
        get() = executableNamesFor(OS.CURRENT)

    protected fun fallbackPaths(directories: Iterable<Path>): List<Path> =
        directories.flatMap { directory -> executableNames.map { directory.resolve(it) } }

    companion object {
        private fun home(): Path = Path.of(System.getProperty("user.home"))

        private fun Path.isRunnable(): Boolean = Files.isRegularFile(this) && isExecutable()

        /** The `bin` directories of the Flutter installs people actually use. */
        private fun flutterBinDirectories(): List<Path> = listOf(
            home().resolve("fvm/default/bin"),
            home().resolve("flutter/bin"),
            home().resolve("development/flutter/bin"),
            home().resolve("Development/flutter/bin"),
        )
    }
}
