**Title:** IntelliJ IDEA plugin for project creation and CLI workflows (working draft available)

## Problem to Solve

Serverpod's editor tooling is VS Code only. The official [Serverpod extension](https://marketplace.visualstudio.com/items?itemName=serverpod.serverpod)
has around 8.6k installs and the installation docs recommend it as part of the standard setup. There
is no equivalent for the IntelliJ platform.

That matters because Serverpod is built for the Flutter community, and a large share of that community
works in Android Studio or IntelliJ IDEA rather than VS Code. Those developers get no Serverpod
tooling at all, so every part of the workflow stays manual:

- `serverpod create` has to be run in a terminal, and the resulting workspace opened by hand
- `serverpod generate`, `create-migration`, and `create-repair-migration` are invisible unless you
  already know they exist
- starting PostgreSQL and Redis means remembering `docker compose up` in the right package directory
- running the server means `dart run bin/main.dart --mode development` rather than a run configuration

The practical effect is a rougher onboarding path for anyone evaluating Serverpod who does not already
use VS Code.

Related: #3350 asks for an IntelliJ plugin, focused on the model-file diagnostics and syntax
highlighting the VS Code extension provides, and carries the `area: lsp` label. This request covers
the project and workflow surface instead, which is separate work. Happy for the two to be merged into
one tracking issue if you would rather keep it together.

## Proposal

An officially supported IntelliJ platform plugin, published on the JetBrains Marketplace, that drives
the existing `serverpod` CLI rather than reimplementing any of it:

- A **New Project wizard** entry with template choice (`server`, `mini`, `module`) and Dart package
  name validation, which runs `serverpod create` and opens the finished workspace
- **Actions** for `generate`, `create-migration`, and `create-repair-migration`
- **Docker Compose** controls to start and stop the containers, plus a reset that recreates the
  volumes for when a stale volume keeps an old `POSTGRES_PASSWORD`
- A **run configuration** for `bin/main.dart` with a run-mode selector and `--apply-migrations` and
  `--apply-repair-migration` toggles
- A **tool window** showing the detected workspace layout, container state, the resolved Dart, Flutter
  and Serverpod versions, and the streamed output of every command the plugin runs
- A **CLI check** that detects a missing `serverpod` and offers to install it with
  `dart pub global activate serverpod_cli`

Worth noting that `serverpod language-server` already exists and is documented as intended for an IDE
client. An IntelliJ plugin would be a natural host for it later, which would deliver what #3350 asks
for inside the same plugin rather than as a second one.

## Use Case

A Flutter developer in Android Studio or IntelliJ IDEA creates a Serverpod project from
`File | New | Project`, gets the containers running and the server started without opening a terminal,
and discovers the generate and migration commands from a menu instead of the documentation. When the
model files change, regenerating is one action rather than a remembered command.

More broadly, it removes IDE choice as a friction point when adopting Serverpod, and gives the
IntelliJ-family user base the same first-class experience VS Code users already have.

## Alternatives

- **Status quo: VS Code only, terminal everywhere else.** Works, and the CLI is good enough to use
  directly, but it leaves the onboarding gap above and makes the commands undiscoverable.
- **A community plugin under a non-Serverpod namespace.** This is what I have now. It works, but it
  fragments the tooling story, cannot be recommended from your docs with the same confidence, risks
  drifting out of step with CLI changes, and leaves the naming and namespace question unresolved.
- **A thin generic LSP client over `serverpod language-server`.** Would address the diagnostics side of
  #3350 with far less work, but does nothing for project creation, migrations, containers, or run
  configurations.
- **Lean on the existing Serverpod DevTools extension.** It already brings Insights into IntelliJ and
  Android Studio, but it is a runtime debugging surface, not project workflow, so the gap remains.

## Additional context

**I have a working draft plugin already**, which is why I am raising this. It is a Kotlin IntelliJ
platform plugin that shells out to the CLI through `GeneralCommandLine`, with every command run as
`--no-analytics --no-interactive`, so it reimplements no Serverpod logic and no protocol. Everything
listed under Proposal is implemented and working against `serverpod_cli` 3.4.11. Layout detection,
package-name validation, and CLI version parsing are unit tested.

It declares the official Dart (`Dart`) and Flutter (`io.flutter`) plugins as required dependencies, so
it sits on top of Google's tooling rather than alongside it and the IDE installs all three together.

Two things I would want your guidance on:

- **Naming and namespace.** The draft currently uses the plugin ID `dev.serverpod.idea` and the display
  name `Serverpod`, which is your namespace and your name. I am not going to publish it that way
  without your say. The options as I see them are that you adopt it as the official plugin, or you
  permit those identifiers for a community plugin, or I rename it to something clearly unofficial.
  A Marketplace plugin ID cannot be changed after first publication without starting a new listing,
  so this is worth settling before anything ships.
- **Platform floor.** The draft targets IntelliJ IDEA 2026.2 (`sinceBuild 262`) because that is what I
  developed against. Android Studio tracks an older platform release, so covering it means lowering
  that floor and verifying against the corresponding build. I have not done that yet.

I am happy to contribute the code, hand it over, or maintain it as a community plugin under your
direction, whichever fits your plans best.

### How experienced are you with this library?

<!-- Select the option matching your own experience when filing. -->

### Are you interested in working on a PR for this?

- [x] I want to work on this
