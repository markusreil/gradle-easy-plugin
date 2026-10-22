# Contributing a contributor plugin

Audience: authors of **external** contributor plugins — new `easy { ... }` features
that live in their own repositories. (For working *inside* this repo, see
[DEVELOPMENT.md](DEVELOPMENT.md); for *using* the plugins, see [README.md](README.md).)

The contract below is all there is: implement the SPI, ship two modules, test
against the published artifacts. `project-defaults` (`contributor-plugins/project-defaults/`)
is the smallest in-repo example to read alongside — every file named here has a
counterpart there.

## 1. Depend on the published API

All coordinates are on Maven Central, group `com.mreil.gradleplugins.easy`
(verified for 0.1.0; use the Portal for plugin-marker resolution in settings):

| You need | Artifact | Scope |
|---|---|---|
| Extension + SPI types (`EasyPluginContributor`, `EasyPluginExtension`, `@EnabledBy`, `@PublicType`, `CanBeEnabled`, …) | `easy-contributor-api` | `implementation` |
| `AbstractEasyProjectPlugin` / `AbstractEasySettingsPlugin` base + lifecycle | `easy-contributor-support` | `implementation` |
| `PropertyResolver`, `hasSnapshotVersion`, `hasGroup`, … helpers | `gradle-plugin-utils` | `implementation` |
| Gradle API | `gradleApi()` | `compileOnly` — never depend on another Gradle plugin's types (see step 4) |

`easy-plugin-core` is intentionally **not** published (it is bundled into the two
marker jars). Anything you need from it that is not reachable through the three
artifacts above is a gap in the public API — file it instead of working around it.

## 2. Write the `-api` module (your public surface)

A plain `java-library` module with one interface extending `EasyPluginExtension`:

```kotlin
interface EasyGreetExtension : EasyPluginExtension {
    val greeting: Property<String>
}
```

Keep this module dependency-free except `easy-contributor-api` (+ `gradleApi()`
`compileOnly`). Consumers compile against it; you evolve it under semantic versioning.

## 3. Write the `-plugin` module (your implementation)

A plain `java-library` module (do **not** use `java-gradle-plugin` — contributors are
discovered, not applied by ID):

- **Extension implementation**, e.g. `DefaultEasyGreetExtension`, annotated
  `@PublicType(EasyGreetExtension::class)` when the public type differs from the class.
  `ExtensionRegistrar.createExtensionAs` registers the extension under the public type
  and instantiates your implementation.
- **Contributor**: implement `EasyPluginContributor`, return your plugin from
  `projectPlugins()` (and/or `settingsPlugins()`), and register it in
  `META-INF/services/com.mreil.easy.EasyPluginContributor`.
- **Plugin**: extend `AbstractEasyProjectPlugin` (or the settings base), annotated
  `@EnabledBy(EasyGreetExtension::class)` so it only activates when the user enables it.

## 4. Respect the lifecycle contract

`AbstractEasyProjectPlugin.apply` runs in two phases — get this wrong and your plugin
will break in combination with others:

- `init()` runs at apply time. Register everything other contributors may need here:
  shared `BuildService`s, repositories, extensions. Never read another contributor's
  deferred state here.
- `afterEnabled()` runs only when your extension is enabled (`easy { greet { enabled.set(true) } }`,
  honoring `CanBeEnabled`). Do enabled-gated work here: tasks, wiring, publications.
- To react to an external Gradle plugin, never depend on its types. Integrate by id
  and react: `project.pluginManager.withPlugin("their.id") { ... }`, no-oping when absent.

`@ApplyToSubprojects` on the contributor controls root-vs-subproject fan-out; the
default is root only — check what your feature needs.

## 5. Test it

- **Unit** (`src/test`): JUnit Jupiter + AssertJ + `ProjectBuilder`. Apply the core
  entry point, enable your extension, assert wiring. Fast; run per change.
- **Functional** (a `-test-plugin` harness module if you need TestKit): a
  `java-gradle-plugin` module whose plugin applies the core entry point, so
  `GradleRunner.withPluginClasspath()` picks up your contributor in a child build.
  In-repo harnesses apply `ProjectPluginEntryPoint` from the (unpublished) core;
  externally, apply the published marker instead (`id("com.mreil.easy.project")`),
  resolved from the Portal — this doubles as a consumer-side smoke test. Whether that
  substitution is seamless is an open benchmark question; report friction, don't hack it.
- **Isolation**: TestKit children inherit the test JVM's environment. Any test asserting
  on *absent* credentials must clear them with pioneer's repeatable
  `@ClearEnvironmentVariable` (all `JRELEASER_*` / `GRADLE_PUBLISH_*` vars), and test
  JVMs need `--add-opens java.base/java.util=ALL-UNNAMED` for Pioneer's reflective access.
- **Configuration cache** stays on: wire tasks with lazy providers, never `project` at
  execution time, never an extra `afterEvaluate`.

Name new functional suites `src/<name>Test` (e.g. `src/functionalTest`) — the shared
wiring auto-configures framework, catalog test dependencies, ordering, and aggregate
reports for every suite discovered there.

## 6. How users enable your plugin

Users apply the marker (`com.mreil.easy.project` / `com.mreil.easy.settings`) and opt
in inside `easy { }`:

```kotlin
easy {
    greet {
        enabled.set(true)
        greeting.set("hello")
    }
}
```

`enabled` defaults: `true` makes yours an always-on default (like `publish`), `false`
makes it strictly opt-in. Pick deliberately — defaults are the one thing you cannot
take back without a major version.

## When you are stuck

If any step above cannot be completed using only this file plus the three published
artifacts, that is a defect in the public API, not in your plugin. The known open
question is external functional-test wiring (step 5); everything else should work
blind. Report the gap with the smallest failing build that shows it.
