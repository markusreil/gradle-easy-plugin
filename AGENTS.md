# AGENTS.md

## Project Overview
Multi-project Gradle plugin build (Kotlin + `java-gradle-plugin`, Gradle 9.4.1). Root
`build.gradle.kts` applies `kotlin-jvm`/`detekt`/`spotless` with `apply false` to avoid
Kotlin multi-load warning and configures aggregated reporting (`jacoco-report-aggregation`,
`test-report-aggregation`) + centralized Spotless. Plugins:
- `com.mreil.easy.project` → `com.mreil.easy.ProjectPlugin` (Project, in `easy-plugin-core`, published via `easy-plugin`)
- `com.mreil.easy.settings` → `com.mreil.easy.SettingsPlugin` (Settings, in `easy-plugin-core`, published via `easy-plugin`)
- Contributor plugins (internal, via SPI — not applied by ID): `contributor-plugins/publish/publish-plugin-api` (public `EasyPublishExtension` interface + `MavenRepoSpec`) + `contributor-plugins/publish/publish-plugin` → `com.mreil.easy.publish.EasyPublishContributor` / `EasyPublishPlugin` / `DefaultEasyPublishExtension` (`@PublicType(EasyPublishExtension::class)`), `contributor-plugins/jvm-defaults/jvm-defaults-plugin-api` (public `EasyJvmDefaultsExtension` interface) + `contributor-plugins/jvm-defaults/jvm-defaults-plugin` → `com.mreil.easy.jvm.EasyJvmDefaultsContributor` / `EasyJvmDefaultsPlugin` / `DefaultEasyJvmDefaultsExtension` (@EnabledBy EasyJvmDefaultsExtension, @PublicType) (+ `publish-test-plugin` / `jvm-defaults-test-plugin` harnesses `com.mreil.easy.test.publish` / `com.mreil.easy.test.jvm` that apply `ProjectPlugin` for `withPluginClasspath` functional tests)

Shared discovery via `PluginRegistry`/`PluginRegistryService` (BuildService) +
`EasyPluginContributor` SPI (ServiceLoader,
`META-INF/services/com.mreil.easy.EasyPluginContributor`). `EasyExtension` (`easy { }`) aggregates per-contributor extensions (`EasyPublishExtension`, `EasyJvmDefaultsExtension`, ...). Extensions can expose a public API via `@PublicType` on the implementation — `ExtensionRegistrar.createExtensionAs` registers the extension under the public type (its `Named` companion) and instantiates the implementation (resolved via `resolvePublicType()`).

## Structure
```
settings.gradle.kts          # includes :easy-plugin, :easy-plugin-core, :easy-contributor-api, :easy-contributor-support, :easy-test-support, :gradle-plugin-testutils, :gradle-plugin-utils, :contributor-plugins:publish:publish-plugin-api, :contributor-plugins:publish:publish-plugin, :contributor-plugins:publish:publish-test-plugin, :contributor-plugins:jvm-defaults:jvm-defaults-plugin-api, :contributor-plugins:jvm-defaults:jvm-defaults-plugin, :contributor-plugins:jvm-defaults:jvm-defaults-test-plugin, :contributor-plugins:semver:semver-plugin-api, :contributor-plugins:semver:semver-plugin, :contributor-plugins:semver:semver-test-plugin, :contributor-plugins:codemeta:codemeta-plugin-api, :contributor-plugins:codemeta:codemeta-plugin, :contributor-plugins:codemeta:codemeta-test-plugin, :contributor-plugins:project-defaults:project-defaults-plugin-api, :contributor-plugins:project-defaults:project-defaults-plugin, :contributor-plugins:project-defaults:project-defaults-test-plugin
build.gradle.kts             # root: lifecycle-base/jacoco-report-aggregation/test-report-aggregation + kotlin-jvm/detekt/spotless apply false; centralized Spotless (ktlint) for leaf projects; aggregated testCodeCoverageReport/testAggregateTestReport
gradle.properties            # CC/parallel/caching/warning.mode=all + plugin.project/settings IDs (single source; runtime mirror in PluginIds.kt)
gradle/libs.versions.toml    # version catalog (kotlin-jvm 2.3.0, junit-jupiter 5.11.3, assertj 3.27.3, detekt 1.23.7, spotless 7.0.2)
config/detekt/detekt.yml     # detekt config (maxLineLength 140, EmptyFunctionBlock off)
easy-plugin/build.gradle.kts           # java-gradle-plugin umbrella: registers com.mreil.easy.project/settings via providers.gradleProperty, aggregates easy-plugin-core + all :contributor-plugins:*:*-plugin (dynamic, APIs transitively); test suites + pluginUnderTestMetadata + detekt; publishing.repositories for mreilComGradlePluginsSnapshots (marker publications via java-gradle-plugin)
easy-plugin-core/build.gradle.kts      # java-library: ProjectPlugin, SettingsPlugin, PluginRegistryService, PluginRegistrar, ExtensionRegistrar, EasyExtension
easy-contributor-api/src/main/kotlin/com/mreil/easy/ # PluginIds, PluginRegistry, EasyPluginContributor, ApplyToSubprojects, EnabledBy, Named, EasyPluginExtension, CanBeEnabled, PublicType
easy-contributor-support/build.gradle.kts   # plain Kotlin lib (no plugin): AbstractEasyProjectPlugin, AbstractEasySettingsPlugin, PluginLifecycle
easy-test-support/build.gradle.kts         # fixtures + easy-specific test helpers (Dummy*Plugin via ServiceLoader, PluginTestUtils); package com.mreil.easy.fixtures / com.mreil.easy.test.support – NOT generic, for functional tests
gradle-plugin-testutils/src/main/kotlin/com/mreil/gradletest/project/ # generic TestKit helpers: GradleTestProject, ProbeTask, templates, assertj; package com.mreil.gradletest (no easy deps) – generic
gradle-plugin-utils/src/main/kotlin/com/mreil/utils/ # generic PropertyResolver – to be extracted to separate repo
contributor-plugins/publish/publish-plugin-api/       # java-library: public EasyPublishExtension interface + MavenRepoSpec (used by consumers, no publish logic)
contributor-plugins/publish/publish-plugin/           # java-library: EasyPublishPlugin + EasyPublishContributor + DefaultEasyPublishExtension (@PublicType) + META-INF/services; unit tests only
contributor-plugins/publish/publish-test-plugin/      # java-gradle-plugin harness: com.mreil.easy.test.publish → PublishTestHarnessPlugin (applies ProjectPlugin) + functionalTest via withPluginClasspath
contributor-plugins/jvm-defaults/jvm-defaults-plugin-api/ # java-library: public EasyJvmDefaultsExtension interface (used by consumers)
contributor-plugins/jvm-defaults/jvm-defaults-plugin/ # java-library: EasyJvmDefaultsPlugin (@EnabledBy EasyJvmDefaultsExtension) + EasyJvmDefaultsContributor + DefaultEasyJvmDefaultsExtension + META-INF/services; unit tests only
contributor-plugins/jvm-defaults/jvm-defaults-test-plugin/ # java-gradle-plugin harness: com.mreil.easy.test.jvm → JvmDefaultsTestHarnessPlugin (applies ProjectPlugin) + functionalTest via withPluginClasspath
contributor-plugins/semver/semver-plugin-api/              # java-library: public EasySemverExtension interface + EasySemver lookup (used by consumers)
contributor-plugins/semver/semver-plugin/                  # java-library: EasySemverPlugin + EasySemverContributor + DefaultEasySemverExtension + META-INF/services; unit tests only
contributor-plugins/semver/semver-test-plugin/             # java-gradle-plugin harness: com.mreil.easy.test.semver → SemverTestHarnessPlugin (applies ProjectPlugin) + functionalTest via withPluginClasspath
contributor-plugins/codemeta/codemeta-plugin-api/          # java-library: public EasyCodemetaExtension interface + Codemeta/CodemetaLicense/EasyCodemeta + CodemetaService (used by consumers)
contributor-plugins/codemeta/codemeta-plugin/              # java-library: EasyCodemetaPlugin + EasyCodemetaContributor + DefaultEasyCodemetaExtension + GenerateCodemetaTask + META-INF/services; unit tests only
contributor-plugins/codemeta/codemeta-test-plugin/         # java-gradle-plugin harness: com.mreil.easy.test.codemeta → CodemetaTestHarnessPlugin (applies ProjectPlugin) + functionalTest via withPluginClasspath
contributor-plugins/project-defaults/project-defaults-plugin-api/       # java-library: public EasyProjectDefaultsExtension interface (used by consumers)
contributor-plugins/project-defaults/project-defaults-plugin/           # java-library: EasyProjectDefaultsPlugin (applies `base` in init, fail-fast group/version in afterEnabled) + EasyProjectDefaultsContributor + DefaultEasyProjectDefaultsExtension + META-INF/services; unit tests only
contributor-plugins/project-defaults/project-defaults-test-plugin/      # java-gradle-plugin harness: com.mreil.easy.test.projectdefaults → ProjectDefaultsTestHarnessPlugin (applies ProjectPlugin) + functionalTest via withPluginClasspath
test-projects/README.md               # manual snapshot dogfooding docs
test-projects/simple/                 # standalone smoke-test for project plugin (NOT included in root build); id("com.mreil.easy.project") version "latest.integration" from mreilComGradlePluginsSnapshots; own wrapper + gradle/gradle-daemon-jvm.properties toolchainVersion=21 + cache 0; run via `cd test-projects/simple && ./gradlew build` (Java 21 daemon auto-provisioned)
test-projects/simple-settings/        # same for settings plugin (id("com.mreil.easy.settings") in settings.gradle.kts)
```

## Commands
```bash
./gradlew build                                # all projects, warning.mode=all (includes aggregated coverage/reports)
./gradlew :easy-plugin:check                   # unit + functional + detekt + jacocoTestReport (check.dependsOn functionalTest)
./gradlew :easy-plugin:test                    # unit tests only (JUnit Jupiter + AssertJ + ProjectBuilder)
./gradlew :easy-plugin:functionalTest          # functional tests (GradleRunner + withPluginClasspath; fixtures via test-fixtures)
./gradlew :easy-plugin:detekt                  # code analysis (easy-plugin + easy-plugin-core)
./gradlew :easy-plugin-core:check              # core unit tests + detekt + jacoco
./gradlew :contributor-plugins:publish:publish-plugin:check           # publish lib: unit + detekt + jacoco
./gradlew :contributor-plugins:publish:publish-test-plugin:check      # publish harness: functionalTest via withPluginClasspath (id("com.mreil.easy.test.publish")) + detekt + jacoco
./gradlew :contributor-plugins:jvm-defaults:jvm-defaults-plugin:check    # jvm-defaults lib: unit + detekt + jacoco
./gradlew :contributor-plugins:jvm-defaults:jvm-defaults-test-plugin:check # jvm harness: functionalTest via withPluginClasspath (id("com.mreil.easy.test.jvm")) + detekt + jacoco
./gradlew :easy-plugin:publishToMavenLocal
./gradlew :easy-plugin:publish                 # publish snapshots to mreilComGradlePluginsSnapshots (requires credentials)
cd test-projects/simple && ./gradlew build     # manual snapshot smoke-test (standalone, Java 21, latest.integration)
./gradlew spotlessCheck                        # verify Kotlin/Gradle formatting (ktlint)
./gradlew spotlessApply                        # auto-format all sources
./gradlew testCodeCoverageReport testAggregateTestReport  # aggregated JaCoCo + test reports (root)
```

Use `./gradlew` (wrapper, Gradle 9.4.1) — not system `gradle`.

## Conventions
- Use imports instead of fully qualified names everywhere (e.g., `import kotlin.reflect.KClass` + `KClass` rather than `kotlin.reflect.KClass`). This applies to Kotlin sources and KDoc links where possible; prefer imported simple names for readability.
- Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`). Root
`build.gradle.kts` must keep `kotlin-jvm`/`detekt`/`spotless` `apply false`; leaf `subprojects { }` block centrally applies Spotless — keep.
- Plugin IDs are the single source in `gradle.properties`
(`plugin.project`/`plugin.settings`), read via
`providers.gradleProperty(...).get()` in `easy-plugin/build.gradle.kts`; runtime
mirror is `easy-contributor-api/.../PluginIds.kt` — keep in sync.
Contributor `publish-plugin`/`jvm-defaults` IDs are internal (contribute via SPI, not applied by ID externally).
- Plugin registration via `gradlePlugin { plugins.creating { id,
implementationClass } }`. Functional test source set wired via
`gradlePlugin.testSourceSets.add(...)` (easy-plugin + contributor `publish-test-plugin`/`jvm-defaults-test-plugin` harnesses) and `check.dependsOn(functionalTest)` — keep.
- CC/parallel/caching/warning.mode=all are on — tasks must be CC-compatible
(providers/properties, no `project` at execution).
- ServiceLoader SPI: contributors implement `EasyPluginContributor` in
`easy-contributor-api`, declare
`META-INF/services/com.mreil.easy.EasyPluginContributor`.
`ProjectPlugin`/`SettingsPlugin`/`PluginRegistrar` call `registry.loadFromServiceLoader()` then
defer extra-plugin application via
`project.plugins.withType(ProjectPlugin::class.java) { apply }` /
`settings.pluginManager.withPlugin(PluginIds.SETTINGS) { apply }` +
`pluginManager.apply(kclass.java)` (no manual instantiation, never
`newInstance().apply()`). Ordering is `orderedAllProjects` (root + subprojects sorted by path); `@ApplyToSubprojects` on the contributor controls fan-out, `@EnabledBy(Extension::class)` + `AbstractEasyProjectPlugin`/`afterEvaluate` + `CanBeEnabled` controls lazy enabling via `easy { ... }`. Extensions can expose a public API via `@PublicType` on the implementation — `ExtensionRegistrar.createExtensionAs` registers the extension under the public type (its `Named` companion) and instantiates the implementation (resolved via `resolvePublicType()`).
- Lifecycle contract (`AbstractEasyProjectPlugin.apply` = eager `init()` + deferred `afterEnabled()`): shared state consumed across contributors (e.g. `BuildService`s like `CodemetaService`) must be registered in `init()` (apply time), never in `afterEnabled()`. `afterEnabled()` is for enabled-gated behavior only (tasks, wiring). `withType`/`withId` order plugin *application*, not deferred `afterEvaluate` actions — so cross-contributor reads during configuration may only depend on eagerly-available state (extensions, apply-time services), never on another contributor's `afterEvaluate` having run.
- `afterEvaluate` is a last resort, never routine: prefer lazy Gradle APIs (providers, `withType`/`withId`/`configureEach`, `named`) plus the `init()`/`afterEnabled()` lifecycle, which compose regardless of evaluation order. Extra `afterEvaluate` blocks (nested ones, `state.executed` branches) create ordering puzzles — e.g. the `PomCheckWiring` incident where a `matching().all()` hook silently never fired for lazily-registered tasks.

## Code Style
- Early returns / guard clauses over nesting: `val x = ... ?: return`, then `x.y.orNull?.let { ... }` (detekt `ReturnCount` max is 2 — stay within it, don't stack guards to dodge nesting).
- Idiomatic null handling: `?.let`, `?:`, `takeIf`, `orNull`, `orEmpty` — never compound `x != null && y != null` guards or temp-then-check (`val x = a?.b` followed by `if (a != null && x != null)`).
- `filter { ... }.forEach { ... }` over `forEach` + `return@forEach`; expression bodies for single-expression functions; `mapNotNull` chains over `return@mapNotNull null` guards.
- Behavior-preserving: conciseness refactors must not change semantics — verify with `check` (unit + functional + detekt) and `spotlessCheck`.

## Testing
- Unit: `easy-plugin/src/test`, `easy-plugin-core/src/test`, `easy-contributor-support/src/test`, `gradle-plugin-utils/src/test`, `contributor-plugins/*/src/test` — JUnit Jupiter 5.11.3 + AssertJ SoftAssertions, `ProjectBuilder` for `PluginRegistryService`/`ExtensionRegistrar`/`PluginRegistrar` (fast). Shared fixtures in `easy-test-support` (`com.mreil.easy.fixtures` + `com.mreil.easy.test.support.PluginTestUtils`) — also `easy-plugin` `fixtures` configuration for TestKit classpath; generic helpers are `com.mreil.gradletest.project.GradleTestProject`/`ProbeTask` in `gradle-plugin-testutils` (package `com.mreil.gradletest`), `com.mreil.utils.PropertyResolver` in `gradle-plugin-utils` (generic, to be extracted).
- Functional: `easy-plugin/src/functionalTest` — `GradleRunner` with `withPluginClasspath()` (real classes from `easy-test-support`/`easy-plugin-core`, not inline `build.gradle.kts`). `contributor-plugins/publish/publish-test-plugin` & `jvm-defaults/jvm-defaults-test-plugin` — `GradleRunner` with `withPluginClasspath()` + `id("com.mreil.easy.test.publish")` / `id("com.mreil.easy.test.jvm")` harness plugins that apply `ProjectPlugin`.
- Isolated functional tests (recommended): use `@DisableAllEasyPlugins` + `@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)` from `easy-test-support` (`com.mreil.easy.test.support`). The extension sets `easy.disableAllPlugins=true` on both host and TestKit child (via `GradleTestProject.systemProperty`) and clears after. With all `CanBeEnabled` disabled (`ExtensionRegistrar.kt:131`), tests explicitly re-enable needed plugins via `easy { <name> { enabled.set(true) } }` (e.g. `publish` needing `codemeta` must declare extra `project(":contributor-plugins:codemeta:codemeta-plugin")` + enable both). For low-level flag verification see `DisableAllPluginsFuncTest.kt:14` (still uses manual `@SetSystemProperty` + `systemProperty`).
- Run `./gradlew :easy-plugin:check` (or `./gradlew build` for all modules + aggregated reports) before submitting.

## Dependencies
- All dependencies/plugins must be in `gradle/libs.versions.toml` and referenced
by alias (e.g., `alias(libs.plugins.kotlin.jvm)`, `libs.assertj.core`). No
hardcoded coordinates/versions.
- Detekt 1.23.7 has an upstream `ReportingExtension.file(String)` deprecation
warning — ignore until 2.x.

## Code Analysis
- Config in `config/detekt/detekt.yml` (maxLineLength 140, EmptyFunctionBlock off). `check` depends on `detekt` and `jacocoTestReport` (plus `functionalTest` where applicable). Fix `detekt` findings before submitting.
- `detekt` analyzes every Kotlin source set (`main`, `test`, `functionalTest`, …) via centralized source wiring in root `build.gradle.kts` (the task defaults to `main`+`test` only). `FunctionNaming` excludes test paths; abstract plugin/extension bases carry `@Suppress("UnnecessaryAbstractClass")` (Gradle decoration requires non-final types).
- `detektMain`/`detektTest` (type-resolution rules) are NOT in `check`: EXPERIMENTAL in detekt 1.x and crash on some files under Kotlin 2.3 — run manually, revisit with detekt 2.x.
- Spotless (with `ktlint`) enforces code formatting across Kotlin sources and Gradle scripts (centralized in root `build.gradle.kts` for leaf projects). Run `./gradlew spotlessCheck` to verify and `./gradlew spotlessApply` to automatically format.

## Editing Guidelines
- Prefer editing over creating files. Match existing Kotlin style (no extra
comments unless requested).
- When adding a new plugin/task/extension, update `gradlePlugin` block (or contributor SPI + `META-INF/services`), add `EasyPluginExtension` + `EnabledBy` if needed, add tests in both suites, and verify with `check`.
- Do not disable CC/parallel/caching/warning.mode without justification.

## Future Improvements
- None currently — contributor harnesses use `withPluginClasspath`; add new contributors under `contributor-plugins/<name>/<name>-plugin` + `<name>-test-plugin` with `Easy<Name>Plugin`/`Easy<Name>Extension` naming.
