package dev.serverpod.idea.run

import com.intellij.execution.configurations.RunConfigurationOptions
import dev.serverpod.idea.cli.ServerpodFeature

class ServerpodRunConfigurationOptions : RunConfigurationOptions() {

    var serverDir by string()

    /**
     * Defaults to the entry point rather than `serverpod start`, so a
     * configuration written by an older plugin build keeps doing what it did.
     */
    var launchMode by string(ServerpodLaunchMode.ENTRY_POINT.id)
    var runMode by string(ServerpodRunMode.DEVELOPMENT.cliValue)
    var applyMigrations by property(false)
    var applyRepairMigration by property(false)

    /** Serverpod 4 only: also launch the Flutter apps marked `auto_launch`. */
    var launchFlutterApps by property(true)
    var extraArguments by string()
}

/** What the configuration actually starts. */
enum class ServerpodLaunchMode(
    val id: String,
    val displayName: String,
    /** Null when every supported CLI can do this. */
    val requires: ServerpodFeature?,
) {

    /** `dart run bin/main.dart`, the only option before Serverpod 4. */
    ENTRY_POINT("entryPoint", "dart run bin/main.dart", null),

    /** `serverpod start`: code generation, the database, the server, and the apps. */
    START("start", "serverpod start", ServerpodFeature.START),

    /** `serverpod database start`, for a project using the embedded PostgreSQL. */
    DATABASE("database", "serverpod database start", ServerpodFeature.EMBEDDED_DATABASE),
    ;

    /** Only the two server modes take a run mode and migration flags. */
    val runsServer: Boolean get() = this != DATABASE

    override fun toString(): String = displayName

    companion object {
        fun from(id: String?): ServerpodLaunchMode =
            entries.firstOrNull { it.id == id } ?: ENTRY_POINT
    }
}

enum class ServerpodRunMode(val cliValue: String) {
    DEVELOPMENT("development"),
    TEST("test"),
    STAGING("staging"),
    PRODUCTION("production");

    override fun toString(): String = cliValue

    companion object {
        fun from(value: String?): ServerpodRunMode =
            entries.firstOrNull { it.cliValue == value } ?: DEVELOPMENT
    }
}
