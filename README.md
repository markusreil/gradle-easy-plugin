# gradle-easy-plugin

Umbrella Gradle plugin that provides a single `easy { }` DSL and discovers feature plugins via SPI. Built with Kotlin, `java-gradle-plugin`, Gradle 9.4.1.

## Main plugin

Published via `easy-plugin` (`easy-plugin-core` contains implementation):

* `com.mreil.easy.project` → `com.mreil.easy.ProjectPlugin` (`easy-plugin-core/src/main/kotlin/com/mreil/easy/ProjectPlugin.kt:8`) — apply to a `Project`. Creates `EasyExtension` (`easy { }`) on the project (and injects copies to subprojects), loads contributors via `PluginRegistryService` (`ServiceLoader`), and applies contributed `Plugin<Project>`s via `PluginRegistrar`.
* `com.mreil.easy.settings` → `com.mreil.easy.SettingsPlugin` (`easy-plugin-core/src/main/kotlin/com/mreil/easy/SettingsPlugin.kt`) — same for `Settings`. The `Settings` `easy` is copied to the root `Project` as parent via `ExtensionCopier`.

Plugin IDs are the single source in `gradle.properties:7` (`plugin.project`/`plugin.settings`), mirrored in `easy-contributor-api/src/main/kotlin/com/mreil/easy/PluginIds.kt`.

Core mechanism:

* `EasyPluginContributor` SPI (`easy-contributor-api/src/main/kotlin/com/mreil/easy/EasyPluginContributor.kt`) — `META-INF/services/com.mreil.easy.EasyPluginContributor`. Contributors declare `projectPlugins()`, `settingsPlugins()`, `pluginExtensions()` (`EasyPluginExtension`).
* `PluginRegistry`/`PluginRegistryService` (BuildService) + `ExtensionRegistrar`/`PluginRegistrar` — eager `easy { }` creation, ordered application (`orderedAllProjects`), `@ApplyToSubprojects` fan-out, `@EnabledBy(Extension::class)` + `CanBeEnabled.enabled` (`easy-contributor-api/src/main/kotlin/com/mreil/easy/CanBeEnabled.kt:12`) + `AbstractEasyProjectPlugin.afterEnabled`/`afterEvaluate` for lazy enabling.
* `@PublicType` — `ExtensionRegistrar.createExtensionAs` (`easy-plugin-core/src/main/kotlin/com/mreil/easy/ExtensionRegistrar.kt:160`) registers extensions under the public `-api` interface (e.g. `EasyPublishExtension`) while instantiating the internal `@PublicType` implementation.

## Contributor plugins

Internal, contributed via SPI — not applied by ID directly.

| Contributor | API / Impl | Plugin | Extension (`easy.<name>`) | What it does |
|---|---|---|---|---|
| `contributor-plugins/publish` | `publish-plugin-api: EasyPublishExtension` + `MavenRepoSpec` / `publish-plugin: DefaultEasyPublishExtension` (`@PublicType`, `enabled` **false** by default) | `EasyPublishPlugin` (`@EnabledBy(EasyPublishExtension::class)`) | `easy.publish` (`CanBeEnabled`, disabled by default — `enabled.set(true)` required) | Wraps `maven-publish`. Creates default `maven` publication from `java` component (unless `java-gradle-plugin` present), normalizes coordinates/POM/versionMapping, wires `mavenRepo {}` declarations and snapshot/release filtering (uses `EasySemver` when `easy.semver` is enabled). See `contributor-plugins/publish/publish-plugin/src/main/kotlin/com/mreil/easy/publish/EasyPublishPlugin.kt:32`. |
| `contributor-plugins/jvm-defaults` | no API extension | `EasyJvmDefaultsPlugin` | — (no DSL) | When `java` plugin is present, configures `JavaPluginExtension` with `withSourcesJar()` / `withJavadocJar()`. Always active via `EasyJvmDefaultsContributor`. See `contributor-plugins/jvm-defaults/jvm-defaults-plugin/src/main/kotlin/com/mreil/easy/jvm/EasyJvmDefaultsPlugin.kt:8`. |
| `contributor-plugins/semver` | `semver-plugin-api: EasySemverExtension` / `semver-plugin: DefaultEasySemverExtension` | `EasySemverPlugin` (`@EnabledBy`) | `easy.semver` (`CanBeEnabled`, marker) | No eager work. Exposes typed `EasySemver.of(project): Provider<Semver>` for `project.version` (validates `group`/`version` are set and SEMVER via `semver4j`). Used by publish for snapshot detection and by external plugins. See `contributor-plugins/semver/semver-test-plugin/src/functionalTest/kotlin/com/mreil/easy/semver/SemverFuncTest.kt:14`. |
| `contributor-plugins/codemeta` | `codemeta-plugin-api: EasyCodemetaExtension` (`filename: Property<String>` default `codemeta.json`) / `codemeta-plugin: DefaultEasyCodemetaExtension` | `EasyCodemetaPlugin` (`@EnabledBy`) | `easy.codemeta` (`CanBeEnabled`) | Registers `CodemetaService` (reads `codemeta.json` via Jackson) and `generateCodemeta` task. If the file is missing, every task depends on `generateCodemeta` which creates an initial `codemeta.json` and fails the build intentionally (`onlyIf !exists`). See `contributor-plugins/codemeta/codemeta-plugin/src/main/kotlin/com/mreil/easy/codemeta/EasyCodemetaPlugin.kt:17`. |

Each contributor has a `-test-plugin` harness (`com.mreil.easy.test.publish` / `com.mreil.easy.test.jvm` / `com.mreil.easy.test.codemeta` / `com.mreil.easy.test.semver`) that applies `ProjectPlugin` for `withPluginClasspath` functional tests.

## Usage

`settings.gradle.kts`:

```kotlin
plugins {
    id("com.mreil.easy.settings")
}
```

`build.gradle.kts` (root or single-project):

```kotlin
plugins {
    `java-library`
    id("com.mreil.easy.project")
}

group = "com.example"
version = "1.2.3"
description = "Example library"
```

### Complete `easy { }` example

All contributor extensions together (use only what you need — each is `CanBeEnabled`; `semver`/`codemeta` are enabled by default when their block is present, `publish` is **disabled by default** and must be explicitly enabled):

```kotlin
easy {
    // Publish to Maven repositories (disabled by default — must opt in)
    publish {
        enabled.set(true)
        // publish to build/stagingRepo (creates mavenStaging repository)
        toMavenStaging("stagingRepo")
        // also publish to local maven on `publish`
        toMavenLocal()

        // named repository - Action<MavenRepoSpec> style
        mavenRepo("myReleases") {
            url.set("https://repo.example.com/releases")
            passwordCredentials.set(false)
        }
        // convenience overload with credentials from -PmySnapshotsUsername/-PmySnapshotsPassword
        mavenRepo(
            name = "mySnapshots",
            url = "https://repo.example.com/snapshots",
            withPasswordCredentials = true,
        )
    }

    // Semantic versioning - typed access to project.version
    semver {
        // marker extension; no config. Use in build logic:
        // val semver = EasySemver.of(project).get() // major/minor/patch/isStable/version
        // enabled.set(false)
    }

    // CodeMeta generation
    codemeta {
        filename.set("codemeta.json") // convention: "codemeta.json"
        // enabled.set(false)
    }

    // jvm-defaults has no DSL - auto-applies when java plugin is present:
    // JavaPluginExtension.withSourcesJar() + withJavadocJar()
}
```

With `semver` enabled, `publish` automatically skips `*release*` repos for snapshots and `*snapshot*` repos for releases. With `codemeta` enabled, pre-create `codemeta.json` or let `generateCodemeta` create an initial file (first build fails by design to let you edit it).

## Multi-project

`ProjectPlugin` injects `easy` copies to subprojects (`ExtensionRegistrar.injectExtensionsToSubprojects` at `easy-plugin-core/src/main/kotlin/com/mreil/easy/ExtensionRegistrar.kt:65`) with `CopyMode` semantics and respects `@ApplyToSubprojects`. Configure `easy { }` in the root; subprojects inherit via `ExtensionCopier`.

## Testing helpers

* Unit: `ProjectBuilder` + `AssertJ` (`easy-plugin-core/src/test`, `easy-plugin/src/test`, `contributor-plugins/*/src/test`).
* Functional: `GradleRunner.withPluginClasspath()` + harness plugins (`easy-plugin/src/functionalTest/kotlin/com/mreil/easy/ProjectPluginFuncTest.kt:14`).
* Isolated: `-Deasy.disableAllPlugins=true` early-sets every `CanBeEnabled.enabled=false` in `ExtensionRegistrar.kt:131`; re-enable late with `easy { <name> { enabled.set(true) } }`. In tests use both `@SetSystemProperty` (JUnit Pioneer) and `GradleRunner.withArguments("-Deasy.disableAllPlugins=true")` — see `easy-plugin/src/functionalTest/kotlin/com/mreil/easy/DisableAllPluginsFuncTest.kt:14`.

## Commands

```bash
./gradlew build
./gradlew :easy-plugin:check
./gradlew spotlessCheck
./gradlew spotlessApply
```
