# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `jvm-defaults`: root report aggregation is now automatic via the new `aggregateReports`
  property (default `true`). The plugin applies `test-report-aggregation` (and, with
  `jacocoEnabled`, `jacoco-report-aggregation`) to the root and registers
  `<suite>AggregateTestReport`/`<suite>CodeCoverageReport` for the built-in `test` suite and every
  auto-configured `*Test` suite, so consumers no longer wire the root aggregation plugins,
  `reporting { }` blocks or `testReportAggregation`/`jacocoAggregation` dependencies themselves.

### Changed

### Deprecated

### Fixed

### Removed

### Security

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
