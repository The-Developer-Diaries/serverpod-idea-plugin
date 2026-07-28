package dev.serverpod.idea.actions

import dev.serverpod.idea.cli.CliTool
import dev.serverpod.idea.cli.ServerpodCommand
import dev.serverpod.idea.project.ServerpodLayout

/**
 * Compose is always invoked from the server package, which is where
 * `serverpod create` puts `docker-compose.yaml`.
 */
abstract class DockerComposeAction : ServerpodAction() {

    override fun isAvailable(layout: ServerpodLayout) = layout.hasDocker
}

class DockerUpAction : DockerComposeAction() {

    override fun command(layout: ServerpodLayout) = ServerpodCommand(
        title = "docker compose up",
        tool = CliTool.DOCKER,
        workDir = layout.serverDir,
        arguments = listOf("compose", "up", "--detach"),
        successMessage = "PostgreSQL and Redis are running.",
    )
}

class DockerDownAction : DockerComposeAction() {

    override fun command(layout: ServerpodLayout) = ServerpodCommand(
        title = "docker compose down",
        tool = CliTool.DOCKER,
        workDir = layout.serverDir,
        arguments = listOf("compose", "down"),
        successMessage = "Stopped the Serverpod containers.",
    )
}
