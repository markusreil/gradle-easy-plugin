# AGENTS.md

## Project Overview
Multi-project Gradle plugin build (Kotlin + `java-gradle-plugin`, Gradle 9.4.1). Root
`build.gradle.kts` applies `kotlin-jvm`/`detekt`/`spotless` with `apply false` to avoid
Kotlin multi-load warning and configures aggregated reporting (`jacoco-report-aggregation`,
`test-report-aggregation`) + centralized Spotless. Plugins:
- `com.mreil.easy.project` → `com.mreil.easy.ProjectPlugin` (Project, in `easy-plugin-core`, published via `easy-plugin`)
- `com.mreil.easy.settings` → `com.mreil.easy.SettingsPlugin` (Settings, in `easy-plugin-core`, published via `easy-plugin`)
- Contributor plugins (internal, via SPI — not applied by ID): `contributor-plugins/publish/publish-plugin-api` (public `EasyPublishExtension` interface + `MavenRepoSpec`) + `contributor-plugins/publish/publish-plugin` → `com.mreil.easy.publish.EasyPublishContributor` / `EasyPublishPlugin` / `DefaultEasyPublishExtension` (`@PublicType(EasyPublishExtension::class)`), `contributor-plugins/jvm-defaults/jvm-defaults-plugin` → `com.mreil.easy.jvm.EasyJvmDefaultsContributor` / `EasyJvmDefaultsPlugin` (+ `publish-test-plugin` / `jvm-defaults-test-plugin` harnesses `com.mreil.easy.test.publish` / `com.mreil.easy.test.jvm` that apply `ProjectPlugin` for `withPluginClasspath` functional tests)

Shared discovery via `PluginRegistry`/`PluginRegistryService` (BuildService) +
`EasyPluginContributor` SPI (ServiceLoader,
`META-INF/services/com.mreil.easy.EasyPluginContributor`). `EasyExtension` (`easy { }`) aggregates per-contributor extensions (`EasyPublishExtension`, `EasyJvmDefaultsExtension`, ...). Extensions can expose a public API via `@PublicType` on the implementation — `ExtensionRegistrar.createExtensionAs` registers the extension under the public type (its `Named` companion) and instantiates the implementation (resolved via `resolvePublicType()`).

## Structure
```
settings.gradle.kts          # includes :easy-plugin, :easy-plugin-core, :easy-contributor-api, :easy-contributor-support, :test-fixtures, :contributor-plugins:publish:publish-plugin-api, :contributor-plugins:publish:publish-plugin, :contributor-plugins:publish:publish-test-plugin, :contributor-plugins:jvm-defaults:jvm-defaults-plugin, :contributor-plugins:jvm-defaults:jvm-defaults-test-plugin
build.gradle.kts             # root: lifecycle-base/jacoco-report-aggregation/test-report-aggregation + kotlin-jvm/detekt/spotless apply false; centralized Spotless (ktlint) for leaf projects; aggregated testCodeCoverageReport/testAggregateTestReport
gradle.properties            # CC/parallel/caching/warning.mode=all + plugin.project/settings IDs (single source; runtime mirror in PluginIds.kt)
gradle/libs.versions.toml    # version catalog (kotlin-jvm 2.3.0, junit-jupiter 5.11.3, assertj 3.27.3, detekt 1.23.7, spotless 7.0.2)
config/detekt/detekt.yml     # detekt config (maxLineLength 140, EmptyFunctionBlock off)
easy-plugin/build.gradle.kts           # java-gradle-plugin umbrella: registers com.mreil.easy.project/settings via providers.gradleProperty, aggregates easy-plugin-core + contributor libs + test-fixtures; test suites + pluginUnderTestMetadata + detekt
easy-plugin-core/build.gradle.kts      # java-library: ProjectPlugin, SettingsPlugin, PluginRegistryService, PluginRegistrar, ExtensionRegistrar, EasyExtension
easy-contributor-api/src/main/kotlin/com/mreil/easy/ # PluginIds, PluginRegistry, EasyPluginContributor, ApplyToSubprojects, EnabledBy, Named, EasyPluginExtension, CanBeEnabled, PublicType
easy-contributor-support/build.gradle.kts   # plain Kotlin lib (no plugin): AbstractEasyProjectPlugin, AbstractEasySettingsPlugin, PluginLifecycle
test-fixtures/build.gradle.kts         # java-library fixtures shared for TestKit classpath (Dummy*Plugin, etc.)
contributor-plugins/publish/publish-plugin-api/       # java-library: public EasyPublishExtension interface + MavenRepoSpec (used by consumers, no publish logic)
contributor-plugins/publish/publish-plugin/           # java-library: EasyPublishPlugin + EasyPublishContributor + DefaultEasyPublishExtension (@PublicType) + META-INF/services; unit tests only
contributor-plugins/publish/publish-test-plugin/      # java-gradle-plugin harness: com.mreil.easy.test.publish → PublishTestHarnessPlugin (applies ProjectPlugin) + functionalTest via withPluginClasspath
contributor-plugins/jvm-defaults/jvm-defaults-plugin/ # java-library: EasyJvmDefaultsPlugin + EasyJvmDefaultsContributor + META-INF/services; unit tests only
contributor-plugins/jvm-defaults/jvm-defaults-test-plugin/ # java-gradle-plugin harness: com.mreil.easy.test.jvm → JvmDefaultsTestHarnessPlugin (applies ProjectPlugin) + functionalTest via withPluginClasspath
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

## Testing
- Unit: `easy-plugin/src/test`, `easy-plugin-core/src/test`, `easy-contributor-support/src/test`, `contributor-plugins/*/src/test` — JUnit Jupiter 5.11.3 + AssertJ SoftAssertions, `ProjectBuilder` for `PluginRegistryService`/`ExtensionRegistrar`/`PluginRegistrar` (fast). Shared fixtures in `test-fixtures` (also `easy-plugin` `fixtures` configuration for TestKit classpath).
- Functional: `easy-plugin/src/functionalTest` — `GradleRunner` with `withPluginClasspath()` (real classes from `test-fixtures`/`easy-plugin-core`, not inline `build.gradle.kts`). `contributor-plugins/publish/publish-test-plugin` & `jvm-defaults/jvm-defaults-test-plugin` — `GradleRunner` with `withPluginClasspath()` + `id("com.mreil.easy.test.publish")` / `id("com.mreil.easy.test.jvm")` harness plugins that apply `ProjectPlugin`.
- Run `./gradlew :easy-plugin:check` (or `./gradlew build` for all modules + aggregated reports) before submitting.

## Dependencies
- All dependencies/plugins must be in `gradle/libs.versions.toml` and referenced
by alias (e.g., `alias(libs.plugins.kotlin.jvm)`, `libs.assertj.core`). No
hardcoded coordinates/versions.
- Detekt 1.23.7 has an upstream `ReportingExtension.file(String)` deprecation
warning — ignore until 2.x.

## Code Analysis
- Config in `config/detekt/detekt.yml` (maxLineLength 140, EmptyFunctionBlock off). `check` depends on `detekt` and `jacocoTestReport` (plus `functionalTest` where applicable). Fix `detekt` findings before submitting.
- Spotless (with `ktlint`) enforces code formatting across Kotlin sources and Gradle scripts (centralized in root `build.gradle.kts` for leaf projects). Run `./gradlew spotlessCheck` to verify and `./gradlew spotlessApply` to automatically format.

## Editing Guidelines
- Prefer editing over creating files. Match existing Kotlin style (no extra
comments unless requested).
- When adding a new plugin/task/extension, update `gradlePlugin` block (or contributor SPI + `META-INF/services`), add `EasyPluginExtension` + `EnabledBy` if needed, add tests in both suites, and verify with `check`.
- Do not disable CC/parallel/caching/warning.mode without justification.

## Future Improvements
- None currently — contributor harnesses use `withPluginClasspath`; add new contributors under `contributor-plugins/<name>/<name>-plugin` + `<name>-test-plugin` with `Easy<Name>Plugin`/`Easy<Name>Extension` naming.
