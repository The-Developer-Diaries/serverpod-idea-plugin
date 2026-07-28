# Serverpod plugin for IntelliJ IDEA

Create and manage [Serverpod](https://serverpod.dev) projects from IntelliJ IDEA. The plugin is a thin
layer over the Serverpod CLI you already have installed, so the projects it produces are byte-for-byte
what `serverpod create` produces on the command line.

## Features

- **New Project wizard entry.** Serverpod appears in `File | New | Project` alongside the built-in
  generators. Pick a template (`server`, `mini`, or `module`), confirm the Dart package name, and the
  plugin runs the CLI and opens the finished workspace. An `After Creating the Project` group,
  collapsed by default, controls whether to start the Docker containers, add a run configuration,
  apply migrations on its first run, and open `bin/main.dart`. Your choices, including whether the
  group is expanded, are remembered for the next project.
- **Dart SDK setup.** A new project is given a module, a Dart SDK, and exclusions for build output, so
  the code is analysed the moment the workspace opens rather than after a trip through
  `Project Structure`. Opening a Serverpod workspace that has no SDK offers the same in one click.
- **Tool window.** Shows the server, client, and Flutter packages, whether migrations exist, and
  whether the Docker containers are running. Every command the plugin runs streams into its console.
- **Actions** under `Tools | Serverpod` and on the tool window toolbar: generate code, create a
  migration, create a repair migration, start or stop the Docker Compose services, and reset the
  database containers.
- **Your own scripts.** Whatever the server package declares under `serverpod/scripts` in its
  `pubspec.yaml` appears under `Tools | Serverpod | Run Script`, executed with `serverpod run`.
- **Regeneration on change.** Optionally runs `serverpod generate` after a `.spy.yaml` model is saved,
  so the generated client never lags behind the models.
- **Run configuration.** Runs `dart run bin/main.dart` in the server package, with a run-mode selector
  and toggles for `--apply-migrations` and `--apply-repair-migration`.

## Requirements

### IDE plugins

A Serverpod workspace is Dart from end to end, and the default template also generates a Flutter app,
so these two are declared as required dependencies. The IDE installs them with this plugin and will not
enable it without them:

| Plugin | ID | Version for build 262 |
| --- | --- | --- |
| [Dart](https://plugins.jetbrains.com/plugin/6351-dart) | `Dart` | 507.0.0 or newer |
| [Flutter](https://plugins.jetbrains.com/plugin/9212-flutter) | `io.flutter` | 94.0.0 or newer |

They provide the analysis, run configurations, and SDK registration for the generated code. This plugin
does not reimplement any of it, and does not call their APIs — it only relies on them being present.

### Tools on the machine

| Tool | Why | Notes |
| --- | --- | --- |
| IntelliJ IDEA 2026.2 or newer | Platform APIs used by the wizard | build 262+ |
| Serverpod CLI | Project creation, code generation, migrations | `dart pub global activate serverpod_cli` |
| Dart SDK | Running the server | Bundled with Flutter |
| Flutter SDK | The generated Flutter app | Never invoked, but its bundled Dart SDK is what gets registered |
| Docker | PostgreSQL and Redis for local development | Only for the `server` template |

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
./gradlew test      # layout detection, script parsing, package naming, version parsing
./gradlew runIde    # launches a sandbox IDE with the plugin loaded
```

`runIde` accepts a project to open, which is the quickest way to exercise the plugin against a real
workspace:

```bash
./gradlew runIde --args="/path/to/a/serverpod/project"
```

Building requires JDK 25, because IntelliJ IDEA 2026.2 is itself built with Java 25.

The target platform is stated once, as `platformVersion` in `gradle.properties`. Everything else follows
from it: the plugin's `since-build` floor, and the Dart and Flutter releases resolved for the sandbox.
Moving to an older platform would also mean lowering `jvmToolchain` to match that release's Java
version.

## How project creation works

`serverpod create` is happy to populate a directory that already exists, so the plugin normally runs it
directly inside the project directory the wizard just created. That keeps the absolute paths pub writes
into `.dart_tool` correct.

If the project directory name differs from the Dart package name — which the CLI would otherwise use to
create a nested folder — generation happens in a scratch directory beside the project, the result is
moved in, and dependencies are re-resolved so `.dart_tool` points at the final location.

## "Password authentication failed" after recreating a project

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
