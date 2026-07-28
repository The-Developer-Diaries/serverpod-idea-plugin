package dev.serverpod.idea.actions

import dev.serverpod.idea.cli.CliTool
import dev.serverpod.idea.cli.ServerpodCommand
import dev.serverpod.idea.project.ServerpodLayout

class CreateMigrationAction : ServerpodAction() {

    override fun isAvailable(layout: ServerpodLayout) = layout.hasMigrations

    override fun command(layout: ServerpodLayout) = ServerpodCommand(
        title = "serverpod create-migration",
        tool = CliTool.SERVERPOD,
        workDir = layout.serverDir,
        arguments = ServerpodCommand.NON_INTERACTIVE + "create-migration",
        successMessage = "Created a migration for the current model state.",
    )
}
