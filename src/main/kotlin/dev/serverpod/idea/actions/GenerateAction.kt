package dev.serverpod.idea.actions

import dev.serverpod.idea.cli.ServerpodCommand
import dev.serverpod.idea.project.ServerpodLayout

class GenerateAction : ServerpodAction() {

    override fun command(layout: ServerpodLayout) = ServerpodCommand.generate(layout)
}
