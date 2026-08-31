# AGENTS.md

## Project Overview
Multi-project Gradle plugin build (Kotlin + `java-gradle-plugin`). Root
`build.gradle.kts` applies `kotlin-jvm`/`detekt` with `apply false` to avoid
Kotlin multi-load warning. Plugins:
- `com.mreil.easy.greeting` → `com.mreil.easy.ProjectPlugin` (Project)
- `com.mreil.easy.settings` → `com.mreil.easy.SettingsPlugin` (Settings)
- `com.mreil.easy.publish` → `com.mreil.easy.publish.EasyPublishPlugin` (in `publish-plugin`)

Shared discovery via `PluginRegistry`/`PluginRegistryService` (BuildService) +
`EasyPluginContributor` SPI (ServiceLoader,
`META-INF/services/com.mreil.easy.EasyPluginContributor`).

## Structure
```
settings.gradle.kts          # includes :easy-plugin, :gradle-plugin-tools, :gradle-plugin-tools-api, :publish-plugin
build.gradle.kts             # root: kotlin-jvm/detekt apply false
gradle.properties            # CC/parallel/caching/warning.mode=all + plugin.greeting/settings IDs (single source; runtime mirror in PluginIds.kt)
gradle/libs.versions.toml    # version catalog (kotlin-jvm 2.3.0, junit-jupiter 5.11.3, assertj 3.27.3, detekt 1.23.7)
config/detekt/detekt.yml     # detekt config (maxLineLength 140, EmptyFunctionBlock off)
easy-plugin/build.gradle.kts      # java-gradle-plugin, plugin registrations via providers.gradleProperty, test suites, detekt
easy-plugin/src/main/kotlin/com/mreil/easy/  # ProjectPlugin, SettingsPlugin, PluginRegistryService, fixtures/Dummy*Plugin
gradle-plugin-tools-api/src/main/kotlin/com/mreil/easy/ # PluginIds, PluginRegistry, EasyPluginContributor
gradle-plugin-tools/         # plain Kotlin lib (no plugin)
publish-plugin/              # java-gradle-plugin, EasyPublishPlugin + EasyPublishContributor + META-INF/services
```

## Commands
```bash
./gradlew build                      # all projects, warning.mode=all
./gradlew :easy-plugin:check              # unit + functional + detekt (check.dependsOn detekt)
./gradlew :easy-plugin:test               # unit tests only (JUnit Jupiter + AssertJ + ProjectBuilder)
./gradlew :easy-plugin:functionalTest     # functional tests (GradleRunner + withPluginClasspath)
./gradlew :easy-plugin:detekt             # code analysis
./gradlew :easy-plugin:publishToMavenLocal
```

Use `./gradlew` (wrapper, Gradle 9.4.1) — not system `gradle`.

## Conventions
- Use imports instead of fully qualified names everywhere (e.g., `import kotlin.reflect.KClass` + `KClass` rather than `kotlin.reflect.KClass`). This applies to Kotlin sources and KDoc links where possible; prefer imported simple names for readability.
- Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`). Root
`build.gradle.kts` must keep `kotlin-jvm`/`detekt` `apply false`.
- Plugin IDs are the single source in `gradle.properties`
(`plugin.greeting`/`plugin.settings`), read via
`providers.gradleProperty(...).get()` in `easy-plugin/build.gradle.kts`; runtime
mirror is `gradle-plugin-tools-api/.../PluginIds.kt` — keep in sync.
`publish-plugin` IDs are internal (not applied by ID externally).
- Plugin registration via `gradlePlugin { plugins.creating { id,
implementationClass } }`. Functional test source set wired via
`gradlePlugin.testSourceSets.add(...)` and `check.dependsOn(functionalTest)` —
keep.
- CC/parallel/caching/warning.mode=all are on — tasks must be CC-compatible
(providers/properties, no `project` at execution).
- ServiceLoader SPI: contributors implement `EasyPluginContributor` in
`gradle-plugin-tools-api`, declare
`META-INF/services/com.mreil.easy.EasyPluginContributor`.
`ProjectPlugin`/`SettingsPlugin` call `registry.loadFromServiceLoader()` then
defer extra-plugin application via
`project.plugins.withType(ProjectPlugin::class.java) { apply }` /
`settings.pluginManager.withPlugin(PluginIds.SETTINGS) { apply }` +
`pluginManager.apply(kclass.java)` (no manual instantiation, never
`newInstance().apply()`).

## Testing
- Unit: `easy-plugin/src/test` — JUnit Jupiter 5.11.3 + AssertJ SoftAssertions,
`ProjectBuilder` for `PluginRegistryService` (fast). Fixtures in
`src/test/.../fixtures` and `src/main/.../fixtures` (shared for TestKit
classpath).
- Functional: `easy-plugin/src/functionalTest` — `GradleRunner` with
`withPluginClasspath()`; dummy plugins are real classes in
`src/main/.../fixtures`, not inline `build.gradle.kts`.
- Run `./gradlew :easy-plugin:check` before submitting.

## Dependencies
- All dependencies/plugins must be in `gradle/libs.versions.toml` and referenced
by alias (e.g., `alias(libs.plugins.kotlin.jvm)`, `libs.assertj.core`). No
hardcoded coordinates/versions.
- Detekt 1.23.7 has an upstream `ReportingExtension.file(String)` deprecation
warning — ignore until 2.x.

## Code Analysis
- Config in `config/detekt/detekt.yml`. `plugin:check` depends on `detekt` and `spotlessCheck`. Fix
`detekt` findings before submitting.
- Spotless (with `ktlint`) enforces code formatting across Kotlin sources and Gradle scripts. Run `./gradlew spotlessCheck` to verify and `./gradlew spotlessApply` to automatically format.

## Editing Guidelines
- Prefer editing over creating files. Match existing Kotlin style (no extra
comments unless requested).
- When adding a new plugin/task/extension, update `gradlePlugin` block, add
tests in both suites, and verify with `check`.
- Do not disable CC/parallel/caching/warning.mode without justification.

## Future Improvements
- `contributor-plugins/jvm-defaults/src/functionalTest/kotlin/com/mreil/easy/jvm/JvmDefaultsPluginFuncTest.kt:17` — 
`buildscript { classpath(files(...coreJar, apiJar, jvmJar)) }` with hardcoded
absolute paths and manual jar wiring is brittle (path-sensitive, not cacheable, 
skips `withPluginClasspath` metadata). Replace with a proper TestKit classpath
via `java-gradle-plugin` test fixtures, composite `includeBuild`, or `gradleTestKit()` 
dependency that wires `easy-plugin-core` transitively.
