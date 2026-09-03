# Serverpod plugin for IntelliJ IDEA

Create and manage [Serverpod](https://serverpod.dev) projects from IntelliJ IDEA. The plugin is a thin
layer over the Serverpod CLI you already have installed, so the projects it produces are byte-for-byte
what `serverpod create` produces on the command line.

## Features

- **New Project wizard entry.** Serverpod appears in `File | New | Project` alongside the built-in
  generators. Pick a template, confirm the Dart package name, and the plugin runs the CLI and opens
  the finished workspace. Collapsed groups cover the project's features, the editors to install agent
  skills for, and what to do once the project exists. Your choices, including which groups were
  expanded, are remembered for the next project.
- **Dart SDK setup.** A new project is given a module, a Dart SDK, and exclusions for build output, so
  the code is analysed the moment the workspace opens rather than after a trip through
  `Project Structure`. Opening a Serverpod workspace that has no SDK offers the same in one click.
- **Tool window.** Shows the server, client, and Flutter packages, whether migrations exist, where the
  development database lives, and whether the Docker containers are running. Every command the plugin
  runs streams into its console.
- **Actions** under `Tools | Serverpod` and on the tool window toolbar: start the full stack, run the
  server, start the embedded database, generate code, create a migration, create a repair migration,
  start or stop the Docker Compose services, reset the database containers, and install the AI agent
  skills.
- **Your own scripts.** Whatever the server package declares under `serverpod/scripts` in its
  `pubspec.yaml` appears under `Tools | Serverpod | Run Script`, executed with `serverpod run`.
- **Regeneration on change.** Optionally runs `serverpod generate` after a `.spy.yaml` model is saved,
  so the generated client never lags behind the models.
- **Run configuration.** Runs the server in the server package, with a run-mode selector and toggles
  for `--apply-migrations` and `--apply-repair-migration`. The `Command` field selects between
  `serverpod start`, `dart run bin/main.dart`, and `serverpod database start`.

## Serverpod versions

The plugin is not pinned to a Serverpod release. It reads the version of the CLI on your machine and
offers what that CLI can actually do, so a feature appears when you upgrade and goes away again if you
roll back. Until the version has been read, only the surface that every supported release has is
offered, which is why a command never fails because the plugin assumed too much.

These are the parts that follow the installed CLI:

| Surface | Serverpod 3.x | Serverpod 4.0 and newer |
| --- | --- | --- |
| Templates | `server`, `mini`, `module` | `fullstack`, `server`, `module` |
| Create options | Implied by the template | Database, Redis, and authentication as separate choices |
| Agent skills | — | Editors to install skills and MCP servers for |
| Run configuration default | `dart run bin/main.dart` | `serverpod start` |
| Database | Docker Compose | Embedded PostgreSQL, SQLite, or Docker Compose |

`serverpod start` is Serverpod 4's single command for the whole stack: it generates code, brings up the
database, runs the server with hot reload on save, and launches the Flutter apps marked `auto_launch`.
The plugin runs it with `--no-tui`, because its interactive terminal UI needs a real terminal and the
run console is not one. The hot reload, the code generation, and the database still work; the keyboard
shortcuts for migrations do not, and the plugin's own migration actions cover those.

A Serverpod 4 project created today runs PostgreSQL from the project directory rather than from Docker,
so it never needs Docker Desktop. The tool window's `Database` row says which one a workspace uses,
which matters most just after an upgrade, when a project has a `docker-compose.yaml` it no longer uses.
`Tools | Serverpod | Start Embedded Database` runs that database on its own, which is what `psql` needs
to connect to it.

`Tools | Serverpod | Install AI Agent Skills…` runs `serverpod create .` over an existing project, which
is how Serverpod installs its agent skills and registers its MCP servers. It rewrites each selected
editor's own configuration file, so it names them and asks first.

## Requirements

### IDE plugins

A Serverpod workspace is Dart from end to end. The plugin declares the official Dart plugin as its only
required IDE dependency. `<depends>Dart</depends>` asks Marketplace to install a Dart version compatible
with the user's IDE; it does not pin that installed version.

| Plugin | ID | Pinned build, sandbox, and verifier version |
| --- | --- | --- |
| [Dart](https://plugins.jetbrains.com/plugin/6351-dart) | `Dart` | 508.1.0 |

The Dart plugin provides analysis and SDK registration for the generated code. The Flutter IDE plugin is
optional: install it for Flutter-specific IDE support when working on the generated Flutter app. This
plugin neither declares nor calls the Flutter plugin.

### Tools on the machine

| Tool | Why | Notes |
| --- | --- | --- |
| IntelliJ IDEA 2026.2 or newer | Platform APIs used by the wizard | build 262+ |
| Serverpod CLI | Project creation, code generation, migrations | `dart pub global activate serverpod_cli` |
| Dart SDK | Running the server | Install Dart directly or use Flutter's bundled Dart SDK |
| Flutter SDK | The generated Flutter app | Needed only to run or develop the generated Flutter app |
| Docker | PostgreSQL and Redis for local development | Not needed on Serverpod 4, which runs PostgreSQL itself |

The plugin looks for each executable in this order: the path set in `Settings | Tools | Serverpod`,
then `PATH`, then the usual install locations (for example `~/.pub-cache/bin/serverpod`). The fallback
matters because an IDE launched from the Dock or Finder does not inherit your shell's `PATH`.

Nothing is pinned or cached across restarts: every command resolves its executable at the moment it
runs, so the SDKs on your machine right now are the ones used. To make that visible rather than
assumed, `Settings | Tools | Serverpod` shows the resolved path and version of each tool, and the
tool window has an `SDKs` row with the Dart, Flutter, and Serverpod versions commands run with. This
matters most when several SDKs are installed — an FVM checkout, a Homebrew Dart, and Flutter's own
bundled Dart can easily be different versions, and a Dock-launched IDE may not resolve the same one
your terminal does.

The SDK constraints in a generated `pubspec.yaml`, such as `sdk: '^3.8.0'`, are Serverpod's minimum
supported versions, not pins. A caret constraint means `>=3.8.0 <4.0.0`, so a newer SDK satisfies it
and is what actually gets used.

### If the Serverpod CLI is missing

You do not have to leave the IDE. The New Project wizard checks for the CLI before it lets you create
anything, and when it is absent the step grows a row offering to install it or to point at a copy you
already have. `Settings | Tools | Serverpod` has the same `Install or Update…` button, which is also
how you move to the latest release later on.

Installing runs `dart pub global activate serverpod_cli`, so it needs the Dart SDK; the button is
disabled until Dart resolves. Pub writes the binary to `~/.pub-cache/bin`, which the plugin already
searches, so it works straight away even if that directory is not on your `PATH`. The plugin does not
edit your shell profile — add `~/.pub-cache/bin` to `PATH` yourself if you also want `serverpod` in a
terminal.

## Keeping generated code current

Editing a `.spy.yaml` model leaves the generated client and serialization code stale until
`serverpod generate` runs. The plugin points this out the first time it happens in a session, and
`Settings | Tools | Serverpod` has a `Regenerate when a model file changes` option to have it handled
for you.

Regeneration is off by default because it starts a process on every save. When enabled, saves are
coalesced so a batch of edits produces one run, and a change arriving mid-run is deferred rather than
started alongside it, since two concurrent runs would write the same files.

## Running your own scripts

The server package can declare scripts in its `pubspec.yaml`, which the generated project already
uses:

```yaml
serverpod:
  scripts:
    start: dart bin/main.dart --apply-migrations
    test: dart test
```

Each one appears under `Tools | Serverpod | Run Script` and is executed with `serverpod run <name>`,
so the CLI keeps ownership of shell selection and of per-platform script variants. The menu is built
from the file, so adding a script is enough to make it appear.

## Building

```bash
./gradlew buildPlugin
```

The installable ZIP lands in `build/distributions/`. Install it with
`Settings | Plugins | ⚙ | Install Plugin from Disk…`.

## Development

```bash
./gradlew test      # version gating, layout and database detection, command arguments, package naming
./gradlew runIde    # launches a sandbox IDE with the plugin loaded
```

`runIde` accepts a project to open, which is the quickest way to exercise the plugin against a real
workspace:

```bash
./gradlew runIde --args="/path/to/a/serverpod/project"
```

Building requires JDK 25, because IntelliJ IDEA 2026.2 is itself built with Java 25.

The target platform is stated once, as `platformVersion` in `gradle.properties`. It sets the plugin's
`since-build` floor. The direct Dart Marketplace dependency is pinned separately as
`dartPluginVersion` for the build, sandbox, and verifier classpaths, so its SDK API contract is
reproducible. It does not constrain the end-user Dart plugin version Marketplace selects through
`<depends>Dart</depends>`; update the pin only after a clean verifier run. The Flutter IDE plugin is
optional and is not resolved as a plugin dependency. Moving to an older platform would also mean
lowering `jvmToolchain` to match that release's Java version.

## How project creation works

`serverpod create` is happy to populate a directory that already exists, so the plugin normally runs it
directly inside the project directory the wizard just created. That keeps the absolute paths pub writes
into `.dart_tool` correct.

If the project directory name differs from the Dart package name — which the CLI would otherwise use to
create a nested folder — generation happens in a scratch directory beside the project, the result is
moved in, and dependencies are re-resolved so `.dart_tool` points at the final location.

## "Password authentication failed" after recreating a project

This applies to a project using Docker. A project on Serverpod 4's embedded PostgreSQL keeps its data
in the directory named by `dataPath`, so deleting that directory is the equivalent reset.


PostgreSQL applies `POSTGRES_PASSWORD` only when it initialises an empty data directory. If you
previously had a project with the same name, `docker compose up` reuses the old named volume, so the
database keeps its original password while the newly generated `config/passwords.yaml` has a different
one. The server then fails with `28P01: password authentication failed for user "postgres"`.

`Tools | Serverpod | Reset Database Containers…` fixes this by removing the volumes and recreating the
containers. It discards everything in the development and test databases, so it asks for confirmation
first.

## A note on generated secrets

`serverpod create` writes real generated passwords into `<name>_server/config/passwords.yaml` and
`<name>_server/docker-compose.yaml`. Treat those files as secrets and keep them out of any repository
you share.
