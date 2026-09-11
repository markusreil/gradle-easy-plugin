# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed

### Deprecated

### Fixed

### Removed

### Security

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
