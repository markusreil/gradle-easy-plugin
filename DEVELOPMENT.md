# Development Guide

This document covers contributors and maintainers of `gradle-easy-plugin`. For **usage** as a consumer, see [README.md](README.md).

## Requirements

* **Java 21+** — Kotlin 2.3.0 / Gradle 9.4.1 toolchain, `org.gradle.jvm.version=21` variant. Run with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`. The smoke-test `test-projects/simple` uses `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }`, but plugin classpath still requires the daemon on 21.
* Gradle 9.4.1 via `./gradlew` (not system `gradle`).

## Project Overview

Multi-project Gradle plugin build (Kotlin + `java-gradle-plugin`). Root `build.gradle.kts` applies `kotlin-jvm`/`detekt`/`spotless` with `apply false` and aggregated reporting (`jacoco-report-aggregation`, `test-report-aggregation`).

Plugins:
* `com.mreil.easy.project` → `com.mreil.easy.ProjectPlugin` (in `easy-plugin-core`, published via `easy-plugin`)
* `com.mreil.easy.settings` → `com.mreil.easy.SettingsPlugin` (in `easy-plugin-core`, published via `easy-plugin`)
* Contributor plugins (internal, via SPI — not applied by ID): `EasyPublishPlugin`, `EasyJvmDefaultsPlugin`, `EasySemverPlugin`, `EasyCodemetaPlugin` etc. (+ `*-test-plugin` harnesses that apply `ProjectPlugin` for `withPluginClasspath` functional tests)

Discovery via `PluginRegistry`/`PluginRegistryService` (BuildService) + `EasyPluginContributor` SPI (`META-INF/services/com.mreil.easy.EasyPluginContributor`). `EasyExtension` (`easy { }`) aggregates per-contributor extensions. Extensions can expose a public API via `@PublicType` on the implementation — `ExtensionRegistrar.createExtensionAs` registers under the public type (its `Named` companion) and instantiates the implementation (resolved via `resolvePublicType()`).

## Structure

```
settings.gradle.kts          # includes :easy-plugin, :easy-plugin-core, :easy-contributor-api, :easy-contributor-support, :easy-test-support, :gradle-plugin-testutils, :gradle-plugin-utils, :contributor-plugins:publish:..., :contributor-plugins:jvm-defaults:..., :contributor-plugins:semver:..., :contributor-plugins:codemeta:...
build.gradle.kts             # root: lifecycle-base/jacoco-report-aggregation/test-report-aggregation + kotlin-jvm/detekt/spotless apply false; centralized Spotless; aggregated reports
gradle.properties            # CC/parallel/caching/warning.mode=all + plugin.project/settings IDs (single source; runtime mirror in PluginIds.kt)
gradle/libs.versions.toml    # version catalog (kotlin-jvm 2.3.0, junit-jupiter 5.11.3, assertj 3.27.3, detekt 1.23.7, spotless 7.0.2)
config/detekt/detekt.yml     # maxLineLength 140, EmptyFunctionBlock off
easy-plugin/build.gradle.kts           # java-gradle-plugin umbrella: registers com.mreil.easy.project/settings via providers.gradleProperty, aggregates easy-plugin-core + contributor libs via dynamic :contributor-plugins:*:*-plugin; test suites + pluginUnderTestMetadata + detekt; publishing.repositories for mreilComGradlePluginsSnapshots (marker publications via java-gradle-plugin)
easy-plugin-core/build.gradle.kts      # java-library: ProjectPlugin, SettingsPlugin, PluginRegistryService, PluginRegistrar, ExtensionRegistrar, EasyExtension
easy-contributor-api/src/main/kotlin/com/mreil/easy/ # PluginIds, PluginRegistry, EasyPluginContributor, ApplyToSubprojects, EnabledBy, Named, EasyPluginExtension, CanBeEnabled, PublicType
easy-contributor-support/build.gradle.kts   # plain Kotlin lib: AbstractEasyProjectPlugin, AbstractEasySettingsPlugin, PluginLifecycle
easy-test-support/build.gradle.kts         # fixtures + easy-specific test helpers (Dummy*Plugin via ServiceLoader, PluginTestUtils.loadGradleProperty); not generic – for functional tests
gradle-plugin-testutils/src/main/kotlin/com/mreil/gradletest/project/ # generic TestKit helpers: GradleTestProject, ProbeTask, templates, assertj; package com.mreil.gradletest (no easy deps)
gradle-plugin-utils/src/main/kotlin/com/mreil/utils/ # generic PropertyResolver – to be extracted to separate repo
contributor-plugins/publish/publish-plugin-api/       # public EasyPublishExtension interface + MavenRepoSpec
contributor-plugins/publish/publish-plugin/           # EasyPublishPlugin + EasyPublishContributor + DefaultEasyPublishExtension (@PublicType) + META-INF/services
contributor-plugins/publish/publish-test-plugin/      # harness: com.mreil.easy.test.publish → PublishTestHarnessPlugin
contributor-plugins/jvm-defaults/jvm-defaults-plugin/ # EasyJvmDefaultsPlugin + EasyJvmDefaultsContributor
contributor-plugins/jvm-defaults/jvm-defaults-test-plugin/ # harness: com.mreil.easy.test.jvm
contributor-plugins/semver/...                        # semver-plugin-api / semver-plugin / semver-test-plugin
contributor-plugins/codemeta/...                      # codemeta-plugin-api / codemeta-plugin / codemeta-test-plugin
test-projects/README.md               # manual snapshot dogfooding docs
test-projects/simple/                 # standalone single-module smoke-test (NOT included in root build); id("com.mreil.easy.project") version "latest.integration" from mreilComGradlePluginsSnapshots; own wrapper; `cd test-projects/simple && ./gradlew build` (Java 21); gradle/gradle-daemon-jvm.properties toolchainVersion=21 + cacheChangingModulesFor(0)
test-projects/simple-settings/        # same for settings plugin (id("com.mreil.easy.settings") in settings.gradle.kts)
```

## Core Mechanism

* `EasyPluginContributor` SPI (`easy-contributor-api/src/main/kotlin/com/mreil/easy/EasyPluginContributor.kt`) — `META-INF/services/com.mreil.easy.EasyPluginContributor`. Contributors declare `projectPlugins()`, `settingsPlugins()`, `pluginExtensions()` (`EasyPluginExtension`).
* `PluginRegistry`/`PluginRegistryService` (BuildService) + `ExtensionRegistrar`/`PluginRegistrar` — eager `easy { }` creation, ordered application (`orderedAllProjects`), `@ApplyToSubprojects` fan-out, `@EnabledBy(Extension::class)` + `CanBeEnabled.enabled` + `AbstractEasyProjectPlugin.afterEnabled`/`afterEvaluate` for lazy enabling.
* `@PublicType` — `ExtensionRegistrar.createExtensionAs` (`easy-plugin-core/src/main/kotlin/com/mreil/easy/ExtensionRegistrar.kt:160`) registers extensions under the public `-api` interface (e.g. `EasyPublishExtension`) while instantiating the internal `@PublicType` implementation.

Plugin IDs are the single source in `gradle.properties` (`plugin.project`/`plugin.settings`), read via `providers.gradleProperty(...).get()` in `easy-plugin/build.gradle.kts`; runtime mirror is `easy-contributor-api/.../PluginIds.kt` — keep in sync.

## Contributor Plugins (internals)

| Contributor | API / Impl | Plugin | Extension | Internals |
|---|---|---|---|---|
| `contributor-plugins/publish` | `publish-plugin-api: EasyPublishExtension` + `MavenRepoSpec` / `publish-plugin: DefaultEasyPublishExtension` (`@PublicType`, `enabled` false by default) | `EasyPublishPlugin` (`@EnabledBy(EasyPublishExtension::class)`) | `easy.publish` | Wraps `maven-publish`. Creates default `maven` publication from `java` component (unless `java-gradle-plugin` present), normalizes coordinates/POM/versionMapping, wires `mavenRepo {}` and snapshot/release filtering (uses `EasySemver`). See `EasyPublishPlugin.kt:32`. |
| `contributor-plugins/jvm-defaults` | no API extension | `EasyJvmDefaultsPlugin` | — | Configures `JavaPluginExtension` with `withSourcesJar()`/`withJavadocJar()` when `java` plugin present. `EasyJvmDefaultsContributor`. |
| `contributor-plugins/semver` | `semver-plugin-api: EasySemverExtension` / `semver-plugin: DefaultEasySemverExtension` | `EasySemverPlugin` (`@EnabledBy`) | `easy.semver` | Exposes `EasySemver.of(project): Provider<Semver>` (validates SEMVER via `semver4j`). |
| `contributor-plugins/codemeta` | `codemeta-plugin-api: EasyCodemetaExtension` (`filename` default `codemeta.json`) / `codemeta-plugin: DefaultEasyCodemetaExtension` | `EasyCodemetaPlugin` (`@EnabledBy`) | `easy.codemeta` | Registers `CodemetaService` (Jackson) and `generateCodemeta`. If file missing, every task depends on `generateCodemeta` which creates initial `codemeta.json` and fails. |

Each contributor has a `-test-plugin` harness (`com.mreil.easy.test.publish` etc.) that applies `ProjectPlugin` for `withPluginClasspath` functional tests.

## Testing

* **Unit:** `easy-plugin/src/test`, `easy-plugin-core/src/test`, `easy-contributor-support/src/test`, `contributor-plugins/*/src/test` — JUnit Jupiter 5.11.3 + AssertJ, `ProjectBuilder` for `PluginRegistryService`/`ExtensionRegistrar`/`PluginRegistrar`.
* **Functional:** `easy-plugin/src/functionalTest` — `GradleRunner` with `withPluginClasspath()` (real classes from `easy-test-support`/`easy-plugin-core`, `com.mreil.gradletest.project.GradleTestProject` from `gradle-plugin-testutils`). `contributor-plugins/*/*-test-plugin` — `GradleRunner` + harness plugins (`com.mreil.easy.test.publish` etc.).
* **Isolated functional tests (recommended):** Use `@DisableAllEasyPlugins` + `@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)` from `easy-test-support` (`com.mreil.easy.test.support`). The extension sets `easy.disableAllPlugins=true` on both the host (via `System.setProperty`) and the TestKit child (via `GradleTestProject.systemProperty`) before each test and clears after. With all `CanBeEnabled` disabled (`ExtensionRegistrar.kt:131`), tests explicitly re-enable needed plugins via `easy { <name> { enabled.set(true) } }` – isolated by design, no transitive surprise (e.g. `publish` needing `codemeta` must declare `implementation(project(":contributor-plugins:codemeta:codemeta-plugin"))` and `easy { publish { enabled.set(true) }; codemeta { enabled.set(true) } }`). For low-level verification of the flag itself, see `DisableAllPluginsFuncTest.kt:14` which still uses manual `@SetSystemProperty` + `systemProperty`.

Run `./gradlew :easy-plugin:check` (or `./gradlew build` for all modules + aggregated reports) before submitting.

## Manual Snapshot Testing

Standalone projects in `test-projects/` dogfood the latest snapshot from `mreilComGradlePluginsSnapshots` (`https://repo.mreil.com/gradle-plugins-snapshots`). They are **not** included in the root build — run in isolation (see `test-projects/README.md` for full docs):

```bash
./gradlew :easy-plugin:publish          # deploy snapshot (requires credentials)
cd test-projects/simple
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew build  # id("com.mreil.easy.project") version "latest.integration"
```

`test-projects/simple` details (standalone):
* `settings.gradle.kts` — `pluginManagement { repositories { gradlePluginPortal(); maven("https://repo.mreil.com/gradle-plugins-snapshots"); mavenCentral() } }`
* `build.gradle.kts` — `plugins { id("com.mreil.easy.project") version "latest.integration" }` + `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }` + `configurations.all { cacheChangingModulesFor(0, SECONDS); cacheDynamicVersionsFor(0, SECONDS) }` (threshold 0 for snapshots) + own wrapper.
* Not included in root `settings.gradle.kts`; `test-projects/README.md` explains adding `test-projects/settings/` etc.

`easy-plugin/build.gradle.kts` adds only `publishing.repositories` for snapshots; marker publications are created by `java-gradle-plugin`.

## Commands

```bash
./gradlew build                                # all projects, warning.mode=all (includes aggregated coverage/reports)
./gradlew :easy-plugin:check                   # unit + functional + detekt + jacocoTestReport
./gradlew :easy-plugin:test                    # unit tests only
./gradlew :easy-plugin:functionalTest          # functional tests (GradleRunner)
./gradlew :easy-plugin:detekt                  # code analysis
./gradlew :easy-plugin-core:check              # core unit tests + detekt + jacoco
./gradlew :contributor-plugins:publish:publish-plugin:check
./gradlew :contributor-plugins:publish:publish-test-plugin:check
./gradlew :contributor-plugins:jvm-defaults:jvm-defaults-plugin:check
./gradlew :contributor-plugins:jvm-defaults:jvm-defaults-test-plugin:check
./gradlew :easy-plugin:publishToMavenLocal
./gradlew :easy-plugin:publish                 # publish snapshots to mreilComGradlePluginsSnapshots (requires credentials)
cd test-projects/simple && ./gradlew build     # manual smoke-test (standalone, Java 21, latest.integration)
./gradlew spotlessCheck                        # verify Kotlin/Gradle formatting (ktlint)
./gradlew spotlessApply                        # auto-format all sources
./gradlew testCodeCoverageReport testAggregateTestReport  # aggregated JaCoCo + test reports (root)
```

Use `./gradlew` (wrapper, Gradle 9.4.1) — not system `gradle`.

## Conventions

* Use imports instead of fully qualified names everywhere (e.g., `import kotlin.reflect.KClass` + `KClass`).
* Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`). Root `build.gradle.kts` must keep `kotlin-jvm`/`detekt`/`spotless` `apply false`; leaf `subprojects { }` centrally applies Spotless — keep.
* Plugin IDs are the single source in `gradle.properties` (`plugin.project`/`plugin.settings`), read via `providers.gradleProperty(...).get()` in `easy-plugin/build.gradle.kts`; runtime mirror is `easy-contributor-api/.../PluginIds.kt` — keep in sync.
* Plugin registration via `gradlePlugin { plugins.creating { id, implementationClass } }`. Functional test source set wired via `gradlePlugin.testSourceSets.add(...)` and `check.dependsOn(functionalTest)` — keep.
* CC/parallel/caching/warning.mode=all are on — tasks must be CC-compatible (providers/properties, no `project` at execution).
* ServiceLoader SPI: `EasyPluginContributor` in `easy-contributor-api`, `META-INF/services/com.mreil.easy.EasyPluginContributor`. `ProjectPlugin`/`SettingsPlugin`/`PluginRegistrar` call `registry.loadFromServiceLoader()` then defer via `project.plugins.withType(ProjectPlugin::class.java) { apply }` / `settings.pluginManager.withPlugin(PluginIds.SETTINGS) { apply }` + `pluginManager.apply(kclass.java)` (no `newInstance().apply()`). Ordering is `orderedAllProjects` (root + subprojects sorted by path); `@ApplyToSubprojects` controls fan-out, `@EnabledBy` + `CanBeEnabled` controls lazy enabling via `easy { }`. Extensions can expose public API via `@PublicType` — `ExtensionRegistrar.createExtensionAs` registers under public type and instantiates implementation via `resolvePublicType()`.

## Dependencies

* All dependencies/plugins must be in `gradle/libs.versions.toml` and referenced by alias (`alias(libs.plugins.kotlin.jvm)`, `libs.assertj.core`). No hardcoded coordinates/versions.
* Detekt 1.23.7 has upstream `ReportingExtension.file(String)` deprecation — ignore until 2.x.

## Code Analysis

* Config in `config/detekt/detekt.yml` (maxLineLength 140, EmptyFunctionBlock off). `check` depends on `detekt` and `jacocoTestReport` (plus `functionalTest` where applicable).
* Spotless (with `ktlint`) enforces formatting across Kotlin sources and Gradle scripts (centralized in root `build.gradle.kts` for leaf projects). Run `./gradlew spotlessCheck` / `spotlessApply`.

## Editing Guidelines

* Prefer editing over creating files. Match existing Kotlin style (no extra comments unless requested).
* When adding a new plugin/task/extension, update `gradlePlugin` block (or contributor SPI + `META-INF/services`), add `EasyPluginExtension` + `EnabledBy` if needed, add tests in both suites, and verify with `check`.
* Do not disable CC/parallel/caching/warning.mode without justification.

## Future Improvements

* None currently — contributor harnesses use `withPluginClasspath`; add new contributors under `contributor-plugins/<name>/<name>-plugin` + `<name>-test-plugin` with `Easy<Name>Plugin`/`Easy<Name>Extension` naming.
