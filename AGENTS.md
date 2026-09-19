# AGENTS.md

## Project Overview
Multi-project Gradle plugin build (Kotlin + `java-gradle-plugin`, Gradle 9.4.1). Root
`build.gradle.kts` declares `kotlin-jvm`/`detekt`/`spotless` with `apply false` (puts them on
the classpath and avoids the Kotlin multi-load warning) and, in its leaf `subprojects { }`
block, centrally applies all three plus the shared detekt config and `check.dependsOn("detekt")`.
It also configures aggregated reporting (`jacoco-report-aggregation`, `test-report-aggregation`).
Plugins:
- `com.mreil.easy.project` → `com.mreil.easy.ProjectPlugin` (Project, in `easy-plugin`, published as `easy-plugin`)
- `com.mreil.easy.settings` → `com.mreil.easy.SettingsPlugin` (Settings, in `easy-plugin-settings`, published as `easy-plugin-settings`)
- The two marker modules are disjoint: each registers one ID and Shadow-bundles only its own scope plus shared core (`easy-plugin-core`/api/support/utils), so the settings artifact carries no KGP-linked project classes. Per-jar `verifyShadowPackaging` enforces it (see `TWO_JAR_SPLIT.md`).
- Contributor plugins (internal, via SPI — not applied by ID): `contributor-plugins/publish/publish-plugin-api` (public `EasyPublishExtension` interface + `MavenRepoSpec`) + `contributor-plugins/publish/publish-plugin` → `com.mreil.easy.publish.EasyPublishContributor` / `EasyPublishPlugin` / `DefaultEasyPublishExtension` (`@PublicType(EasyPublishExtension::class)`), `contributor-plugins/jvm-defaults/jvm-defaults-plugin-api` (public `EasyJvmDefaultsExtension` interface) + `contributor-plugins/jvm-defaults/jvm-defaults-plugin` → `com.mreil.easy.jvm.EasyJvmDefaultsContributor` / `EasyJvmDefaultsPlugin` / `DefaultEasyJvmDefaultsExtension` (@EnabledBy EasyJvmDefaultsExtension, @PublicType), `contributor-plugins/jvm-defaults/jvm-defaults-settings-plugin` → `com.mreil.easy.jvm.EasyJvmDefaultsSettingsContributor` / `com.mreil.easy.jvm.kotlin.DokkaJavadocSettingsPlugin` / `DefaultEasyJvmDefaultsSettingsExtension` (settings scope) (+ `*-test-plugin` harnesses `com.mreil.easy.test.publish` / `com.mreil.easy.test.jvm` etc. that depend on `:easy-plugin-core` and apply the core `ProjectPluginEntryPoint` for `withPluginClasspath` functional tests)

The two scopes are independent: `com.mreil.easy.settings` creates a settings-only root
`EasySettingsExtension` (currently empty) and applies settings contributors, while
`com.mreil.easy.project` creates the project root `EasyExtension`, injects copied extensions into
subprojects and applies project contributors. Each scope owns a separate `PluginRegistryService`
instance (`PluginRegistry.NAME` vs `PluginRegistry.SETTINGS_NAME`) because settings/project plugins
may be loaded by different classloaders. Shared discovery via `EasyPluginContributor` SPI
(ServiceLoader,
`META-INF/services/com.mreil.easy.EasyPluginContributor`). `EasyExtension` (`easy { }`) aggregates per-contributor extensions (`EasyPublishExtension`, `EasyJvmDefaultsExtension`, ...). Extensions can expose a public API via `@PublicType` on the implementation — `ExtensionRegistrar.createExtensionAs` registers the extension under the public type (its `Named` companion) and instantiates the implementation (resolved via `resolvePublicType()`).

## Structure
```
settings.gradle.kts          # includes :easy-plugin, :easy-plugin-settings, :easy-plugin-core, :easy-contributor-api, :easy-contributor-support, :easy-test-support, :gradle-plugin-testutils, :gradle-plugin-utils, :contributor-plugins:publish:publish-plugin-api, :contributor-plugins:publish:publish-plugin, :contributor-plugins:publish:publish-test-plugin, :contributor-plugins:jvm-defaults:jvm-defaults-plugin-api, :contributor-plugins:jvm-defaults:jvm-defaults-plugin, :contributor-plugins:jvm-defaults:jvm-defaults-settings-plugin, :contributor-plugins:jvm-defaults:jvm-defaults-test-plugin, :contributor-plugins:semver:semver-plugin-api, :contributor-plugins:semver:semver-plugin, :contributor-plugins:semver:semver-test-plugin, :contributor-plugins:codemeta:codemeta-plugin-api, :contributor-plugins:codemeta:codemeta-plugin, :contributor-plugins:codemeta:codemeta-test-plugin, :contributor-plugins:project-defaults:project-defaults-plugin-api, :contributor-plugins:project-defaults:project-defaults-plugin, :contributor-plugins:project-defaults:project-defaults-test-plugin
build.gradle.kts             # root: lifecycle-base/jacoco-report-aggregation/test-report-aggregation + kotlin-jvm/detekt/spotless apply false; leaf subprojects{} centrally applies Kotlin JVM + detekt (shared config + check wiring) + Spotless (ktlint); aggregated testCodeCoverageReport/testAggregateTestReport
gradle.properties            # CC/parallel/caching/warning.mode=all + plugin.project/settings IDs (single source; runtime mirror in PluginIds.kt)
gradle/libs.versions.toml    # version catalog (kotlin-jvm 2.3.0, junit-jupiter 5.11.3, assertj 3.27.3, detekt 2.0.0-alpha.6, spotless 8.10.2)
config/detekt/detekt.yml     # detekt 2.x config (maxLineLength 140, EmptyFunctionBlock off; AbstractClassCanBeConcreteClass excludes test paths)
easy-plugin/build.gradle.kts           # java-gradle-plugin umbrella (PROJECT scope): registers com.mreil.easy.project, aggregates easy-plugin-core + all :contributor-plugins:*:*-plugin (excludes -settings-plugin); test suites + pluginUnderTestMetadata (both markers for cross-scope tests) + verifyShadowPackaging
easy-plugin-settings/build.gradle.kts  # java-gradle-plugin umbrella (SETTINGS scope): registers com.mreil.easy.settings, aggregates easy-plugin-core + :contributor-plugins:jvm-defaults:jvm-defaults-settings-plugin; own verifyShadowPackaging
easy-plugin-core/build.gradle.kts      # java-library: ProjectPluginEntryPoint/SettingsPluginEntryPoint bases, ProjectPlugin/SettingsPlugin live in their marker modules; PluginRegistryService, PluginRegistrar, ExtensionRegistrar, EasyExtension, EasySettingsExtension
easy-contributor-api/src/main/kotlin/com/mreil/easy/ # PluginIds, PluginRegistry, EasyPluginContributor, ApplyToSubprojects, EnabledBy, Named, EasyPluginExtension, EasySettingsExtension, CanBeEnabled, PublicType
easy-contributor-support/build.gradle.kts   # plain Kotlin lib (no plugin): AbstractEasyProjectPlugin, AbstractEasySettingsPlugin, PluginLifecycle
easy-test-support/build.gradle.kts         # fixtures + easy-specific test helpers (Dummy*Plugin via ServiceLoader, PluginTestUtils); package com.mreil.easy.fixtures / com.mreil.easy.test.support – NOT generic, for functional tests
gradle-plugin-testutils/src/main/kotlin/com/mreil/gradletest/project/ # generic TestKit helpers: GradleTestProject, ProbeTask, templates, assertj; package com.mreil.gradletest (no easy deps) – generic
gradle-plugin-utils/src/main/kotlin/com/mreil/utils/ # generic PropertyResolver – to be extracted to separate repo
contributor-plugins/publish/publish-plugin-api/       # java-library: public EasyPublishExtension interface + MavenRepoSpec (used by consumers, no publish logic)
contributor-plugins/publish/publish-plugin/           # java-library: EasyPublishPlugin + EasyPublishContributor + DefaultEasyPublishExtension (@PublicType) + META-INF/services; unit tests only
contributor-plugins/publish/publish-test-plugin/      # java-gradle-plugin harness: com.mreil.easy.test.publish → PublishTestHarnessPlugin (applies ProjectPluginEntryPoint) + functionalTest via withPluginClasspath
contributor-plugins/jvm-defaults/jvm-defaults-plugin-api/ # java-library: public EasyJvmDefaultsExtension + EasyJvmDefaultsSettingsExtension interfaces + shared DokkaJavadoc/JvmDefaultsPlugins constants (used by both scopes)
contributor-plugins/jvm-defaults/jvm-defaults-plugin/ # project scope (java-library): EasyJvmDefaultsPlugin + EasyJvmDefaultsKotlinPlugin (@EnabledBy EasyJvmDefaultsExtension) + EasyJvmDefaultsContributor + DefaultEasyJvmDefaultsExtension + META-INF/services; unit tests only
contributor-plugins/jvm-defaults/jvm-defaults-settings-plugin/ # settings scope (java-library): DokkaJavadocSettingsPlugin (@EnabledBy EasyJvmDefaultsSettingsExtension) + EasyJvmDefaultsSettingsContributor + DefaultEasyJvmDefaultsSettingsExtension + META-INF/services; unit tests only
contributor-plugins/jvm-defaults/jvm-defaults-test-plugin/ # java-gradle-plugin harness: com.mreil.easy.test.jvm → JvmDefaultsTestHarnessPlugin (applies ProjectPluginEntryPoint) + functionalTest via withPluginClasspath
contributor-plugins/semver/semver-plugin-api/              # java-library: public EasySemverExtension interface + EasySemver lookup (used by consumers)
contributor-plugins/semver/semver-plugin/                  # java-library: EasySemverPlugin + EasySemverContributor + DefaultEasySemverExtension + META-INF/services; unit tests only
contributor-plugins/semver/semver-test-plugin/             # java-gradle-plugin harness: com.mreil.easy.test.semver → SemverTestHarnessPlugin (applies ProjectPluginEntryPoint) + functionalTest via withPluginClasspath
contributor-plugins/codemeta/codemeta-plugin-api/          # java-library: public EasyCodemetaExtension interface + Codemeta/CodemetaLicense/EasyCodemeta + CodemetaService (used by consumers)
contributor-plugins/codemeta/codemeta-plugin/              # java-library: EasyCodemetaPlugin + EasyCodemetaContributor + DefaultEasyCodemetaExtension + GenerateCodemetaTask + META-INF/services; unit tests only
contributor-plugins/codemeta/codemeta-test-plugin/         # java-gradle-plugin harness: com.mreil.easy.test.codemeta → CodemetaTestHarnessPlugin (applies ProjectPluginEntryPoint) + functionalTest via withPluginClasspath
contributor-plugins/project-defaults/project-defaults-plugin-api/       # java-library: public EasyProjectDefaultsExtension interface (used by consumers)
contributor-plugins/project-defaults/project-defaults-plugin/           # java-library: EasyProjectDefaultsPlugin (applies `base` in init, fail-fast group/version in afterEnabled) + EasyProjectDefaultsContributor + DefaultEasyProjectDefaultsExtension + META-INF/services; unit tests only
contributor-plugins/project-defaults/project-defaults-test-plugin/      # java-gradle-plugin harness: com.mreil.easy.test.projectdefaults → ProjectDefaultsTestHarnessPlugin (applies ProjectPluginEntryPoint) + functionalTest via withPluginClasspath
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
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew publishAllPublicationsToMavenStagingRepository  # local publish-set verification: stages the full artifact set (jars, sources, javadoc, poms, module, markers, signatures) into each project's build/stagingRepo — no remote credentials needed, see DEVELOPMENT.md "Publishing"
./gradlew spotlessCheck                        # verify Kotlin/Gradle formatting (ktlint)
./gradlew spotlessApply                        # auto-format all sources
./gradlew testCodeCoverageReport testAggregateTestReport  # aggregated JaCoCo + test reports (root)
```

Use `./gradlew` (wrapper, Gradle 9.4.1) — not system `gradle`.

## Conventions
- Use imports instead of fully qualified names everywhere (e.g., `import kotlin.reflect.KClass` + `KClass` rather than `kotlin.reflect.KClass`). This applies to Kotlin sources and KDoc links where possible; prefer imported simple names for readability.
- Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`). Root
`build.gradle.kts` must keep `kotlin-jvm`/`detekt`/`spotless` `apply false`; its leaf `subprojects { }` block centrally applies Kotlin JVM + detekt + Spotless and wires the shared detekt config + `check.dependsOn("detekt")` — keep module build files free of those declarations.
- Plugin IDs are the single source in `gradle.properties`
(`plugin.project`/`plugin.settings`), read via
`providers.gradleProperty(...).get()` in the marker module build files (`easy-plugin/build.gradle.kts`
/ `easy-plugin-settings/build.gradle.kts`); runtime mirror is `easy-contributor-api/.../PluginIds.kt`
— keep in sync.
Contributor `publish-plugin`/`jvm-defaults` IDs are internal (contribute via SPI, not applied by ID externally).
- Published artifact set is documented in `DEVELOPMENT.md` → "Publishing (deployed artifact set)" and is the source of truth for everything any `mavenRepo()`-declared repository receives. Whenever publish behaviour changes (new module, new publication, marker changes, harness publishing), update that section and the local-verification note (`publishAllPublicationsToMavenStagingRepository`) — keep both docs in sync.
- Plugin registration via `gradlePlugin { plugins.creating { id,
implementationClass } }`. The repo dogfoods the released `com.mreil.easy.settings` (settings scope,
version in `settings.gradle.kts`) and `com.mreil.easy.project` (project scope, version in the root
`build.gradle.kts`); the project plugin's `jvm-defaults` contributor auto-configures the built-in
`test` and discovered `*Test` suites (catalog-pinned JUnit Jupiter, catalog test deps, `main` output,
`gradleTestKit()`/plugin-under-test metadata/`testSourceSets`, `check` + `shouldRunAfter`) and
applies `jacoco` — so module build files declare only repo-specific test-helper deps (e.g.
`:easy-test-support`, `:gradle-plugin-testutils`) plus `jvmArgs`, never the
`kotlin.jvm`/`detekt` plugin aliases, the detekt config or `check.dependsOn("detekt")`,
`useJUnitJupiter`/AssertJ/Pioneer/JUnit params/Mockito, `jacoco`, `testSourceSets` or
`check.dependsOn(functionalTest)`. JaCoCo report formats are centralized in the root
`subprojects` block.
- CC/parallel/caching/warning.mode=all are on — tasks must be CC-compatible
(providers/properties, no `project` at execution).
- **External plugin integration (standard practice):** never link or depend on an external Gradle
plugin's types. Integrate by id and react with `project.pluginManager.withPlugin(id) { ... }`
(`withId`/`withType` where appropriate), no-oping when it is absent — so the same wiring works
whether the consumer applies the external plugin directly or a settings-scope opt-in only adds its
marker to the root buildscript classpath. Settings scope adds marker/classpath only; project scope
applies and rewires. Reference: Dokka (`DokkaJavadocSettingsWiring.inject` +
`DokkaJavadocWiring.configureJavadocJar`), covered by both `DokkaJavadocFuncTest` scenarios
(settings opt-in; consumer-applied `dokka-javadoc`).
- ServiceLoader SPI: contributors implement `EasyPluginContributor` in
`easy-contributor-api`, declare
`META-INF/services/com.mreil.easy.EasyPluginContributor`.
`ProjectPluginEntryPoint`/`SettingsPluginEntryPoint`/`PluginRegistrar` call
`registry.loadFromServiceLoader(javaClass.classLoader)` (the caller's loader, which is why the
concrete marker classes — not the core bases — are registered under the plugin IDs) then
defer extra-plugin application via
`project.plugins.withType(ProjectPluginEntryPoint::class.java) { apply }` /
`settings.pluginManager.withPlugin(PluginIds.SETTINGS) { apply }` +
`pluginManager.apply(kclass.java)` (no manual instantiation, never
`newInstance().apply()`). Settings and project scopes resolve distinct `PluginRegistryService`
instances via `PluginRegistry.SETTINGS_NAME`/`PluginRegistry.NAME` (`InternalProjectUtils.getRegistry(name)`);
`SettingsPluginEntryPoint` neither applies the project entry point nor copies its extension into projects.
Ordering is `orderedAllProjects` (root + subprojects sorted by path); `@ApplyToSubprojects` on the contributor controls fan-out, `@EnabledBy(Extension::class)` + `AbstractEasyProjectPlugin`/`afterEvaluate` + `CanBeEnabled` controls lazy enabling via `easy { ... }`. Extensions can expose a public API via `@PublicType` on the implementation — `ExtensionRegistrar.createExtensionAs` registers the extension under the public type (its `Named` companion) and instantiates the implementation (resolved via `resolvePublicType()`).
- Lifecycle contract (`AbstractEasyProjectPlugin.apply` = eager `init()` + deferred `afterEnabled()`): shared state consumed across contributors (e.g. `BuildService`s like `CodemetaService`) must be registered in `init()` (apply time), never in `afterEnabled()`. `afterEnabled()` is for enabled-gated behavior only (tasks, wiring). `withType`/`withId` order plugin *application*, not deferred `afterEvaluate` actions — so cross-contributor reads during configuration may only depend on eagerly-available state (extensions, apply-time services), never on another contributor's `afterEvaluate` having run.
- `afterEvaluate` is a last resort, never routine: prefer lazy Gradle APIs (providers, `withType`/`withId`/`configureEach`, `named`) plus the `init()`/`afterEnabled()` lifecycle, which compose regardless of evaluation order. Extra `afterEvaluate` blocks (nested ones, `state.executed` branches) create ordering puzzles — e.g. the `PomCheckWiring` incident where a `matching().all()` hook silently never fired for lazily-registered tasks.

## Code Style
- Early returns / guard clauses over nesting: `val x = ... ?: return`, then `x.y.orNull?.let { ... }` (detekt `ReturnCount` max is 2 — stay within it, don't stack guards to dodge nesting).
- Idiomatic null handling: `?.let`, `?:`, `takeIf`, `orNull`, `orEmpty` — never compound `x != null && y != null` guards or temp-then-check (`val x = a?.b` followed by `if (a != null && x != null)`).
- `filter { ... }.forEach { ... }` over `forEach` + `return@forEach`; expression bodies for single-expression functions; `mapNotNull` chains over `return@mapNotNull null` guards.
- Prefer `Optional` chaining over `orElse(null)` unwraps when consuming `java.util.Optional` (e.g. version-catalog lookups): chain `Optional.ofNullable(...)` + `flatMap`/`map`/`filter` and terminate with `getOrElse { fallback }`/`getOrNull()` (`kotlin.jvm.optionals`) rather than interleaving `?.orElse(null)` — see `gradle-plugin-utils/.../VersionCatalogVersions.kt` for the canonical form.
- Behavior-preserving: conciseness refactors must not change semantics — verify with `check` (unit + functional + detekt) and `spotlessCheck`.

## Testing
- Unit: `easy-plugin/src/test`, `easy-plugin-core/src/test`, `easy-contributor-support/src/test`, `gradle-plugin-utils/src/test`, `contributor-plugins/*/src/test` — JUnit Jupiter 5.11.3 + AssertJ SoftAssertions, `ProjectBuilder` for `PluginRegistryService`/`ExtensionRegistrar`/`PluginRegistrar` (fast). Shared fixtures in `easy-test-support` (`com.mreil.easy.fixtures` + `com.mreil.easy.test.support.PluginTestUtils`) — also `easy-plugin` `fixtures` configuration for TestKit classpath; generic helpers are `com.mreil.gradletest.project.GradleTestProject`/`ProbeTask` in `gradle-plugin-testutils` (package `com.mreil.gradletest`), `com.mreil.utils.PropertyResolver` in `gradle-plugin-utils` (generic, to be extracted).
- Functional: `easy-plugin/src/functionalTest` — `GradleRunner` with `withPluginClasspath()`; its `pluginUnderTestMetadata` carries both marker modules so cross-scope tests can apply both IDs (TestKit flattens the classpath; the real two-jar topology is guarded by the published-consumer E2E in `TWO_JAR_SPLIT.md` step 5). `contributor-plugins/*/*-test-plugin` — `GradleRunner` with `withPluginClasspath()` + `id("com.mreil.easy.test.publish")` / `id("com.mreil.easy.test.jvm")` etc. harness plugins that apply the core `ProjectPluginEntryPoint`.
- Isolated functional tests (recommended): use `@DisableAllEasyPlugins` + `@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)` from `easy-test-support` (`com.mreil.easy.test.support`). The extension sets `easy.disableAllPlugins=true` on both host and TestKit child (via `GradleTestProject.systemProperty`) and clears after. With all `CanBeEnabled` disabled (`ExtensionRegistrar.kt:131`), tests explicitly re-enable needed plugins via `easy { <name> { enabled.set(true) } }` (e.g. `publish` needing `codemeta` must declare extra `project(":contributor-plugins:codemeta:codemeta-plugin")` + enable both). For low-level flag verification see `DisableAllPluginsFuncTest.kt:14` (still uses manual `@SetSystemProperty` + `systemProperty`).
- Run `./gradlew :easy-plugin:check` (or `./gradlew build` for all modules + aggregated reports) before submitting.
- New contributor plugins must ship a configuration-cache compatibility test that fails on CC validation problems (e.g. external processes started at configuration time) — the VCS plugin's `git rev-parse ... @{u}` / `git remote get-url origin` calls broke CC and were only found after release.

## Ad-hoc Release / Snapshot Verification

No smoke-test projects are committed — they needed constant up-keeping against every release, so
do this ad-hoc when a release/snapshot must be verified. Build a throwaway project outside the repo
(e.g. under `/tmp`):

1. Scaffold a standalone Gradle project with its own wrapper: copy `gradlew`, `gradlew.bat` and
   `gradle/wrapper/` from this repo, and run with Java 17+ (the published plugin is a Java-17
   variant, `org.gradle.jvm.version=17`).
2. Apply both plugin IDs (released version resolves from the Plugin Portal; snapshot versions use
   `latest.integration` from the snapshot repo). `settings.gradle.kts` (settings scope):
   ```kotlin
   pluginManagement {
       repositories {
           gradlePluginPortal()
           mavenCentral()
           maven { url = uri("https://repo.mreil.com/gradle-plugins-snapshots") }
       }
   }
   plugins {
       id("com.mreil.easy.settings") version "<version>"
       // Do NOT declare com.mreil.easy.project here (not even with `apply false`): that would put
       // the project artifact, including its KGP wiring, on the settings classpath where KGP is
       // not visible. Each ID is a separate artifact and is versioned where it is applied.
   }
   dependencyResolutionManagement { repositories { mavenCentral() } }
   ```
   Root `build.gradle.kts` (project scope — the `easy { }` DSL and all contributors live here):
   ```kotlin
   plugins { id("com.mreil.easy.project") version "<version>" }
   easy {
       // Contributors default to enabled; switch off what the test does not exercise to stay focused.
       publish { enabled.set(false) }
       semver { enabled.set(false) }
       codemeta { enabled.set(false) }
       vcs { enabled.set(false) }
       release { enabled.set(false) }
   }
   ```
3. Add the modules/sources the feature needs (`java-library`, `java-gradle-plugin`,
   `src/<name>Test` suites) and a `gradle/libs.versions.toml` with the aliases catalog-aware wiring
   probes (`assertj-core`, `junit-pioneer`, `junit-jupiter`/`junit-jupiter-params`, `mockito-core`).
4. Run `./gradlew clean check --configuration-cache`, then `./gradlew check --configuration-cache`
   again to assert the entry is reused. Inspect the outputs that matter: per-suite
   `build/test-results`, root `build/reports/tests/<suite>/aggregated-results`,
   `build/reports/jacoco/<suite>CodeCoverageReport*`, and `:dependencies` for catalog pinning/scoping.
5. For publish behaviour, rehearse against a local docker Nexus using the test-only
   `jreleaser.testNexusUrl` property (swaps in a `nexus3/local-test` deployer and demotes
   `mavenCentral` to `NEVER`, so it can never touch real Central) — see
   `contributor-plugins/publish/publish-plugin`.

## Dependencies
- All dependencies/plugins must be in `gradle/libs.versions.toml` and referenced
by alias (e.g., `alias(libs.plugins.kotlin.jvm)`, `libs.assertj.core`). No
hardcoded coordinates/versions.
- detekt 2.0.0-alpha.6 uses the `dev.detekt` plugin id/group (renamed from
`io.gitlab.arturbosch.detekt`); the 2.x config schema differs from 1.x — regenerate
with `./gradlew detektGenerateConfig` (to a scratch path) before editing by hand.

## Code Analysis
- detekt 2.0.0-alpha.6 (`dev.detekt` plugin). Config in `config/detekt/detekt.yml` (maxLineLength 140, EmptyFunctionBlock off). `check` depends on detekt and `jacocoTestReport` (plus `functionalTest` where applicable). Fix detekt findings before submitting.
- detekt runs with type resolution by default in 2.x. Root `build.gradle.kts` routes the conventional `detekt` task (and therefore `check`) through the type-aware per-source-set tasks (`detektMain`, `detektTest`, `detektFunctionalTest`, …) and disables the plain task's own non-type-aware run; dependencies of a disabled task still execute, so `./gradlew detekt` and `./gradlew check` both run the type-aware analysis. `FunctionNaming` excludes test paths. Production abstract plugin bases carry `@Suppress("AbstractClassCanBeConcreteClass")` and abstract extension/implementation bases carry `@Suppress("AbstractClassCanBeInterface")` (Gradle decoration requires non-final types, and extension impls need class semantics); both rules exclude test/functionalTest/testFixtures paths.
- Spotless (with `ktlint`) enforces code formatting across Kotlin sources and Gradle scripts (centralized in root `build.gradle.kts` for leaf projects). Run `./gradlew spotlessCheck` to verify and `./gradlew spotlessApply` to automatically format.

## Editing Guidelines
- Prefer editing over creating files. Match existing Kotlin style (no extra
comments unless requested).
- When adding a new plugin/task/extension, update `gradlePlugin` block (or contributor SPI + `META-INF/services`), add `EasyPluginExtension` + `EnabledBy` if needed, add tests in both suites, and verify with `check`.
- Do not disable CC/parallel/caching/warning.mode without justification.

## Future Improvements
- Reconcile the other unmerged branches: `fix-KGP-classloader-issues`, `split-settings-and-project-plugin`, `central-kgp-application`, `fix-double-action`, `add-central-publishing`.
- Publish a shared `easy-plugin-core` Maven dependency instead of bundling it into both marker jars.
- Drop the redundant `-api` publishes (they are bundled *and* published today).
- Extract `PropertyResolver` (and the `com.mreil.utils` helpers) so the settings marker no longer declares an unused `commons-configuration2` dependency.
- Rename `easy-plugin` → `easy-plugin-project` for symmetry with `easy-plugin-settings`.
- Contributor harnesses use `withPluginClasspath`; add new contributors under `contributor-plugins/<name>/<name>-plugin` + `<name>-test-plugin` with `Easy<Name>Plugin`/`Easy<Name>Extension` naming.
- Design/risk record for the settings/project artifact split: `TWO_JAR_SPLIT.md`.
