package dev.serverpod.idea.cli

import com.intellij.util.system.OS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The sample outputs are verbatim from the tools, so a format change fails here. */
class CliToolTest {

    @Test
    fun `reads the version out of each tool's --version output`() {
        assertEquals("3.4.11", versionIn(CliTool.SERVERPOD, "Serverpod version: 3.4.11"))

        assertEquals(
            "3.12.2",
            versionIn(
                CliTool.DART,
                """Dart SDK version: 3.12.2 (stable) (Tue Jun 9 01:11:39 2026 -0700) on "macos_arm64"""",
            ),
        )

        assertEquals(
            "3.44.8",
            versionIn(
                CliTool.FLUTTER,
                "Flutter 3.44.8 \u2022 channel stable \u2022 https://github.com/flutter/flutter.git",
            ),
        )

        assertEquals("29.6.1", versionIn(CliTool.DOCKER, "Docker version 29.6.1, build 8900f1d"))
    }

    @Test
    fun `reads the version from multi-line output`() {
        val flutter = """
            Flutter 3.44.8 • channel stable • https://github.com/flutter/flutter.git
            Framework • revision 058e0af2c2 (5 days ago) • 2026-07-23 10:56:21 -0700
            Engine • hash 13ffd72b2f9a5ca4db2a74ea52d5353ec2e8f939
        """.trimIndent()

        assertEquals("3.44.8", versionIn(CliTool.FLUTTER, flutter))
    }

    @Test
    fun `tries Windows batch wrappers before native binary fallbacks`() {
        assertEquals(
            listOf("serverpod.bat", "serverpod.exe"),
            CliTool.SERVERPOD.executableNamesFor(OS.Windows),
        )
        assertEquals(
            listOf("dart.bat", "dart.exe"),
            CliTool.DART.executableNamesFor(OS.Windows),
        )
        assertEquals(
            listOf("flutter.bat", "flutter.exe"),
            CliTool.FLUTTER.executableNamesFor(OS.Windows),
        )
        assertEquals(
            listOf("docker.bat", "docker.exe"),
            CliTool.DOCKER.executableNamesFor(OS.Windows),
        )
    }

    @Test
    fun `uses unextended binary names outside Windows`() {
        listOf(OS.macOS, OS.Linux, OS.FreeBSD, OS.Other).forEach { os ->
            assertEquals(listOf("serverpod"), CliTool.SERVERPOD.executableNamesFor(os))
        }
    }

    @Test
    fun `reports nothing when the output carries no version`() {
        CliTool.entries.forEach { tool ->
            assertNull(versionIn(tool, "zsh: command not found"))
        }
    }

    private fun versionIn(tool: CliTool, output: String): String? =
        tool.versionPattern.find(output)?.groupValues?.getOrNull(1)
}
