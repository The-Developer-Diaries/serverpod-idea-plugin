package dev.serverpod.idea.cli

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.util.SystemInfo
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
        override fun fallbackCandidates(): List<Path> = listOf(
            home().resolve(".pub-cache/bin/$executableName"),
        )

        // Serverpod version: 3.4.11
        override val versionPattern = Regex("""Serverpod version:\s+(\S+)""")
    },

    DART("Dart SDK", "dart") {
        override fun fallbackCandidates(): List<Path> = buildList {
            // The Dart binary always sits next to the Flutter one.
            PathEnvironmentVariableUtil.findInPath(if (SystemInfo.isWindows) "flutter.bat" else "flutter")
                ?.toPath()?.parent?.let { add(it.resolve(executableName)) }
            addAll(flutterBinCandidates(executableName))
            add(Path.of("/opt/homebrew/bin/$executableName"))
            add(Path.of("/usr/local/bin/$executableName"))
        }

        // Dart SDK version: 3.12.2 (stable) (Tue Jun 9 01:11:39 2026 -0700) on "macos_arm64"
        override val versionPattern = Regex("""Dart SDK version:\s+(\S+)""")
    },

    FLUTTER("Flutter SDK", "flutter") {
        override fun fallbackCandidates(): List<Path> = flutterBinCandidates(executableName)

        // Flutter 3.44.8 • channel stable • https://github.com/flutter/flutter.git
        override val versionPattern = Regex("""Flutter\s+(\S+)""")
    },

    DOCKER("Docker", "docker") {
        override fun fallbackCandidates(): List<Path> = listOf(
            home().resolve(".docker/bin/$executableName"),
            Path.of("/usr/local/bin/$executableName"),
            Path.of("/opt/homebrew/bin/$executableName"),
            Path.of("/Applications/Docker.app/Contents/Resources/bin/$executableName"),
        )

        // Docker version 29.6.1, build 8900f1d
        override val versionPattern = Regex("""Docker version\s+([^,\s]+)""")
    };

    /** Platform-correct file name, e.g. `serverpod.bat` on Windows. */
    val executableName: String
        get() = if (SystemInfo.isWindows) "$binaryName.bat" else binaryName

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

        PathEnvironmentVariableUtil.findInPath(executableName)?.let { return it.toPath() }
        if (SystemInfo.isWindows) {
            PathEnvironmentVariableUtil.findInPath("$binaryName.exe")?.let { return it.toPath() }
        }

        return fallbackCandidates().firstOrNull { it.isRunnable() }
    }

    companion object {
        private fun home(): Path = Path.of(System.getProperty("user.home"))

        private fun Path.isRunnable(): Boolean = Files.isRegularFile(this) && isExecutable()

        /** The `bin` directories of the Flutter installs people actually use. */
        private fun flutterBinCandidates(executableName: String): List<Path> = listOf(
            home().resolve("fvm/default/bin/$executableName"),
            home().resolve("flutter/bin/$executableName"),
            home().resolve("development/flutter/bin/$executableName"),
            home().resolve("Development/flutter/bin/$executableName"),
        )
    }
}
