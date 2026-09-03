package dev.serverpod.idea.wizard

import dev.serverpod.idea.cli.ServerpodIde
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerpodCreateFeaturesTest {

    @Test
    fun `states every choice, so the CLI never falls back to its own default`() {
        val arguments = ServerpodCreateFeatures(
            database = true,
            redis = true,
            auth = true,
            ides = listOf(ServerpodIde.CURSOR),
        ).toArguments()

        assertEquals(
            listOf("--database", "--redis", "--auth", "--ide", "cursor"),
            arguments,
        )
    }

    @Test
    fun `negates the flags that are turned off`() {
        val arguments = ServerpodCreateFeatures(
            database = false,
            redis = false,
            auth = false,
            ides = listOf(ServerpodIde.NONE),
        ).toArguments()

        assertEquals(
            listOf("--no-database", "--no-redis", "--no-auth", "--ide", "none"),
            arguments,
        )
    }

    @Test
    fun `drops auth without a database, which the CLI rejects`() {
        val features = ServerpodCreateFeatures(
            database = false,
            redis = true,
            auth = true,
            ides = emptyList(),
        )

        assertTrue(features.toArguments().contains("--no-auth"))
    }

    @Test
    fun `opts out explicitly when no editor is picked`() {
        val arguments = ServerpodCreateFeatures(
            database = true,
            redis = true,
            auth = true,
            ides = emptyList(),
        ).toArguments()

        assertEquals(listOf("--ide", "none"), arguments.takeLast(2))
    }

    @Test
    fun `repeats the flag for each editor`() {
        val arguments = ServerpodCreateFeatures(
            database = true,
            redis = true,
            auth = true,
            ides = listOf(ServerpodIde.CLAUDE, ServerpodIde.CURSOR, ServerpodIde.VS_CODE),
        ).toArguments()

        assertEquals(
            listOf("--ide", "claude", "--ide", "cursor", "--ide", "vscode"),
            arguments.takeLast(6),
        )
    }
}
