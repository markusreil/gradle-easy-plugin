# TODO

This document keeps track of tasks that need to be completed.

## Meta

* retrospective on the 0.1.0 promotion work: why did it take so many iterations
  (credential leak into TestKit, snapshot signing, Verify sign exclusions, e2e staging
  coupling)? Working hypothesis: first-time CI integration teething problems, not inherent
  complexity. Confirm or refute, then decide what changes so the 0.2.0 (or next
  promotion-gated) cycle is boring:
  test isolation by default, verify/prod parity in CI, dogfood lag accounted for,
  quota math done up front?

## Architecture changes / fixes

* first separate contributor plugin in its own repo: the benchmark that informs this
  repo's final structure — in particular whether the test fixtures (`easy-test-support`,
  `gradle-plugin-testutils`) stay published for external consumers. Decides the deferred
  TODO.md quota item on fixtures; see also the future-improvements list in AGENTS.md
  (shared `easy-plugin-core` dependency, dropping `-api` publishes, extracting utils).
* publish: Central quota optimization (measured 0.1.0 at ~492 files/month vs 1,000 limit;
  see DEVELOPMENT.md). Extend checksum stripping to all Gradle-generated checksums
  (Central generates its own), unpublish `e2e-published` (`easy.publish.enabled = false`
  + DEVELOPMENT.md artifact list), keep test fixtures published only if external consumers
  need them. Required before the next same-month release after 0.1.0.

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
