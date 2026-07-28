package dev.serverpod.idea.wizard

data class ServerpodCreateRequest(
    val packageName: String,
    val template: ServerpodTemplate,
    val startDocker: Boolean,
    val createRunConfiguration: Boolean,
    val applyMigrations: Boolean,
    val openEntryPoint: Boolean,
)
