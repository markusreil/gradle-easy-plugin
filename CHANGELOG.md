# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `preReleaseCommit` task in the release plugin: rewrites the `version=` line of a version file (default root `gradle.properties`, overridable via `EasyReleaseExtension.versionFile`) to the resolved release version and commits only that file. Commit message from `preReleaseCommitMessage` (default `Set version for release: $v`, `$v` = release version); without a VCS the commit is a `VcsNone` no-op (file still updated). Requires a separate Gradle invocation afterwards to build/publish with the new version (the version file is read at configuration time).

- `preReleaseTag` task in the release plugin: tags the release commit with the release version. Tag name from `tagTemplate` (default `v$v`, `$v` = release version); runs after `preReleaseCommit` (tags the version-bump commit), `VcsNone` no-op when no VCS is available.

### Changed

### Deprecated

### Fixed

### Removed

### Security

## [0.0.107] - 2026-09-11

### Fixed

- VCS configuration-cache compatibility (`Fix VCS cc issues #13`): `VcsService` no longer starts external Git processes at configuration time (moved to provider APIs via `VcsOperations`), with a new `VcsConfigurationCacheFuncTest` that fails on CC violations.

## [0.0.106] - 2026-09-11

### Added

- VCS contributor plugin suite (`vcs-plugin-api`, `vcs-plugin`, `vcs-test-plugin`): Git detection via `VcsService` build service, exposed through the `EasyVcs` extension, with codemeta integration.

### Changed

- Maven Central publishing wiring supports per-project `toMavenCentral`, deferred to `projectsEvaluated` with ANY semantics.
- Disabled publishing for `easy-plugin-core` and contributor plugin modules; only `easy-plugin` publishes.
- Build on Java 17 (CI and toolchain).

### Fixed

- Published artifacts (`Fix published artifacts #9`): corrected Maven Central / JReleaser wiring and artifact set.

## [0.0.104] - 2026-09-10

Fixes to publish tasks. Published to Gradle Plugin Portal.

[//]: # (TODO update this section when feedback comes in)

## [0.0.100] - 2026-09-08

First version. Only deployed to Maven Central.
