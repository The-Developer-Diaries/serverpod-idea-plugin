package dev.serverpod.idea.actions

import dev.serverpod.idea.cli.CliTool
import dev.serverpod.idea.cli.ServerpodCommand
import dev.serverpod.idea.project.ServerpodLayout

class CreateRepairMigrationAction : ServerpodAction() {

    override fun isAvailable(layout: ServerpodLayout) = layout.hasMigrations

    override fun command(layout: ServerpodLayout) = ServerpodCommand(
        title = "serverpod create-repair-migration",
        tool = CliTool.SERVERPOD,
        workDir = layout.serverDir,
        arguments = ServerpodCommand.NON_INTERACTIVE + "create-repair-migration",
        successMessage = "Created a repair migration against the live database.",
    )
}
