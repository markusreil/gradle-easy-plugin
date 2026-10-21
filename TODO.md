# TODO

This document keeps track of tasks that need to be completed.

## Architecture changes / fixes

* serialization: factor shared JSON file handling (pretty-print config, read/write) into
  `gradle-plugin-utils` alongside `GradleProperties` once a third consumer appears; keep
  domain-specific handling (e.g. `CodemetaJson`'s single-value-as-array normalization) in the
  owning contributor
* publish: derive an artifact classifier from the git branch name so every branch
  publishes a distinct snapshot artifact (no overwrites of `latest.integration`),
  while still publishing on every push regardless of branch
* semver: turn into general versioning plugin. support CD vs. semver. How does that affect
  publish and release? artifact classifier (or additional prerelease) from above should live here.

## Other tasks

* declare Configuration Cache compatibility to silence Plugin Portal warning ("Consider declaring compatibility of your plugin with the following Gradle features: Configuration cache", https://plugins.gradle.org/docs/publish-plugin#declaring-compatibility) + note in README
* Lifecycle listener that collects and reports build statistics
* info task that gives detailed info about publications and repos
* tests: GitAssertions
* test-utils: Helper classes for ProjectBuilder?
* revisit func tests: required or just blowing up build time?
* revisit e2e-published once the publish flow is settled: asserting the process (staging +
  sign + publish wiring) as part of the project build is smelly — the published-consumer
  guard drags staging/signing into every `build`. Consider moving it out of the main
  build graph (nightly job, manual rehearsal, or fixture against prebuilt staging).
  Once decoupled, the promote workflow's Verify step can go back to a plain
  `./gradlew build` (dropping the `-x sign*` exclusions added for v0.1.0).
