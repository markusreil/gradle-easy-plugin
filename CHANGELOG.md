# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed

### Deprecated

### Fixed

- `release`: a disabled `release` contributor no longer realizes `EasySemver` while the
  configuration cache is stored, so an invalid/absent project version no longer fails the build when
  only `semver` is enabled. `ReleaseStateService` is now registered in the enabled-gated
  `afterEnabled` (it is release-internal); the cross-contributor `ReleaseLifecycleService` stays in
  `init()`.

### Removed

### Security

## [0.0.116] - 2026-09-19

### Added

- `easy-plugin-settings`: new settings-scope marker module publishing `com.mreil.easy.settings` (the
  project scope stays on `easy-plugin` / `com.mreil.easy.project`). Each marker Shadow-bundles only
  its own scope plus shared core, and per-jar `verifyShadowPackaging` asserts third-party
  publication and cross-scope class disjointness.
- Core entry-point bases `ProjectPluginEntryPoint` / `SettingsPluginEntryPoint` (the concrete
  `ProjectPlugin` / `SettingsPlugin` live in their marker modules), plus `EasySettingsExtension` and
  a settings-scope registry.
- `jvm-defaults-settings-plugin`: settings-scope slice hosting the Dokka Javadoc opt-in; shared
  Dokka/Kotlin constants moved to `jvm-defaults-plugin-api`.
- `:e2e-published`: published-consumer end-to-end guard resolving both marker artifacts from a local
  Maven repo, applying them in their own scopes with the Kotlin JVM plugin and
  `java.targetVersion=11`.

### Changed

- The plugin suite now ships as **two artifacts**, one per scope. Apply each ID where its scope is
  evaluated and versioned: `com.mreil.easy.settings` in `settings.gradle.kts`,
  `com.mreil.easy.project` in the root build. Project-scope `easy { }` configuration (publish,
  semver, codemeta, vcs, release, jvm-defaults) lives in the root build; the settings scope only owns
  settings extensions. The old `apply false` / versionless workaround is unsupported.
- `jvm-defaults`: the Dokka Javadoc opt-in moved off the project-scope `EasyJvmDefaultsExtension`
  onto the settings-scope `EasyJvmDefaultsSettingsExtension` (public API change; the
  `easy { jvmDefaults { dokkaJavadoc() } }` DSL shape is unchanged).

### Fixed

- Consumer classloader failure (`NoClassDefFoundError:
  org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension`) when `java.targetVersion` was set with the
  Kotlin JVM plugin applied: the project plugin is now loaded by the project buildscript
  classloader, which can see KGP.

## [0.0.115] - 2026-09-18

### Added

- `jvm-defaults`: opt-in `java.targetVersion` property pins Java source/target compatibility and
  `--release`, plus Kotlin `jvmTarget`/`-Xjdk-release` when the Kotlin JVM plugin is applied, so a
  newer toolchain can produce bytecode/API compatible with an older JDK.
- `jvm-defaults`: when the Kotlin JVM plugin is applied, Dokka's Javadoc plugin is dynamically
  applied and `javadocJar` is rewired to Dokka output. Opt-in via the settings-scope
  `easy { jvmDefaults { dokkaJavadoc() } }` function (`dokkaJavadoc("2.3.0")` pins the version,
  default `2.2.0`); it guards only buildscript-classpath inclusion and adds no Dokka dependency to
  any module.

### Fixed

- CI triggers fixed and build scans enabled (`Fix CI triggers and add build scan #27`).

## [0.0.114] - 2026-09-18

### Fixed

- `easy-plugin` fat jar now bundles only modules built by this build; third-party runtime
  dependencies are no longer shaded but declared at `runtime` scope in the published POM/Gradle
  metadata, so consumers resolve them from Maven Central. `verifyShadowPackaging` enforces the
  packaging constraints.

## [0.0.113] - 2026-09-18

### Added

- `jvm-defaults`: root report aggregation is now automatic via the new `aggregateReports`
  property (default `true`). The plugin applies `test-report-aggregation` (and, with
  `jacocoEnabled`, `jacoco-report-aggregation`) to the root and registers
  `<suite>AggregateTestReport`/`<suite>CodeCoverageReport` for the built-in `test` suite and every
  auto-configured `*Test` suite, so consumers no longer wire the root aggregation plugins,
  `reporting { }` blocks or `testReportAggregation`/`jacocoAggregation` dependencies themselves.

### Changed

- `jvm-defaults`: JaCoCo report configuration centralized in the root project and redundant
  per-module references removed; modules now rely on the contributor for test-suite, dependency
  and coverage wiring (`Centralize JaCoCo report configuration #21`).
- `codemeta`/`publish`: replaced Jackson with kotlinx.serialization for `codemeta.json` and the
  JReleaser configuration, which is now emitted as JSON instead of YAML (`Jackson to kotlinx #23`).
- Gradle upgraded to 9.7.1 and the Kotlin Gradle plugins to 2.4.20, with Gradle test-suite
  configuration standardized across modules (`Upgrade Gradle #25`).

### Removed

- Unused `test-projects/` smoke projects and their related configuration.

## [0.0.112] - 2026-09-17

### Added

- `jvm-defaults`: test-suite auto-configuration and JaCoCo coverage, gated by the new
  `configureTestSuites`/`jacocoEnabled` properties (both default `true`). Functional suites
  discovered from `src/<name>Test/{java,kotlin}` get a catalog-pinned JUnit Jupiter,
  catalog-declared test dependencies, the project's `main` output, `check` and
  `java-gradle-plugin` wiring (incl. plugin-under-test metadata); the built-in `test` suite is
  configured in place (Mockito unit-only); `<suite>AggregateTestReport` and
  `<suite>CodeCoverageReport` aggregation are registered at the root.
- Easy logging helpers (`EasyLogging`) and test-source discovery (`findTestSuites()`,
  `TestSourceDiscovery`/`TestSuiteType`).
- `gradle-plugin-utils`: version-catalog helpers (`catalogLibrary`, `catalogVersionOrDefault`).
- Version catalog: Mockito (`org.mockito:mockito-core`).

### Changed

- detekt upgraded to 2.x (`Upgrade detekt #19`) with type-aware analysis per source set.
- Spotless upgraded to 8.10.2 (`Upgrade spotless #18`).
- Shared code moved to `gradle-plugin-utils`/`gradle-plugin-testutils`
  (`Move shared code to plugin/test utils #17`).

## [0.0.110] - 2026-09-16

### Fixed

- Plugin portal publishing (`Fix plugin portal publishing #16`): `toPluginPortal` now skips
  non-plugin projects and root projects without a `publish` task, logs lifecycle info for
  skipped projects, and handles missing credentials more gracefully.

## [0.0.109] - 2026-09-14

### Added

- CI publishes releases from version tags: the `publish` job now runs on `v*` tag pushes
  in addition to `main` (workflows re-enabled on branch pushes), so releases are published
  automatically from tags.

## [0.0.108] - 2026-09-14

### Added
- Release plugin: complete `check → commit → tag → push` flow — `preReleaseCommit` commits
  the version-file bump plus `ReleaseLifecycleListener` files (e.g. codemeta
  `version`/`dateModified`), `preReleaseTag` tags the release commit, `postReleasePush`
  bumps to the next development version and atomically pushes commit and tag, with
  `ReleaseStateService` rollback to the gate state on failure.

## [0.0.107] - 2026-09-11

### Fixed

- VCS configuration-cache compatibility (`Fix VCS cc issues #13`): `VcsService` no longer
  starts external Git processes at configuration time (moved to provider APIs via
  `VcsOperations`), with a new `VcsConfigurationCacheFuncTest` that fails on CC violations.

## [0.0.106] - 2026-09-11

### Added

- VCS contributor plugin suite (`vcs-plugin-api`, `vcs-plugin`, `vcs-test-plugin`):
  Git detection via `VcsService` build service, exposed through the `EasyVcs` extension,
  with codemeta integration.

### Changed

- Maven Central publishing wiring supports per-project `toMavenCentral`, deferred to
  `projectsEvaluated` with ANY semantics.
- Disabled publishing for `easy-plugin-core` and contributor plugin modules; only `easy-plugin` publishes.
- Build on Java 17 (CI and toolchain).

### Fixed

- Published artifacts (`Fix published artifacts #9`): corrected Maven Central / JReleaser wiring and artifact set.

## [0.0.104] - 2026-09-10

Fixes to publish tasks. Published to Gradle Plugin Portal.

[//]: # (TODO update this section when feedback comes in)

## [0.0.100] - 2026-09-08

First version. Only deployed to Maven Central.
