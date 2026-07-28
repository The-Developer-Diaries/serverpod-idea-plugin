package dev.serverpod.idea.run

import com.intellij.execution.configurations.RunConfigurationOptions

class ServerpodRunConfigurationOptions : RunConfigurationOptions() {

    var serverDir by string()
    var runMode by string(ServerpodRunMode.DEVELOPMENT.cliValue)
    var applyMigrations by property(false)
    var applyRepairMigration by property(false)
    var extraArguments by string()
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
