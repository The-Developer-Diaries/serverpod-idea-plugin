package dev.serverpod.idea.actions

import dev.serverpod.idea.cli.CliTool
import dev.serverpod.idea.cli.ServerpodCommand
import dev.serverpod.idea.project.ServerpodLayout

class GenerateAction : ServerpodAction() {

    override fun command(layout: ServerpodLayout) = ServerpodCommand(
        title = "serverpod generate",
        tool = CliTool.SERVERPOD,
        workDir = layout.serverDir,
        arguments = ServerpodCommand.NON_INTERACTIVE + "generate",
        successMessage = "Regenerated the client and serialization code.",
    )
}
