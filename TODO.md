# TODO

This document keeps track of tasks that need to be completed.

## Meta

* 0.1.0 promotion retrospective — verdict: mixed. Test/CI preflight gaps were avoidable;
  TestKit env inheritance, dogfood lag, staging-e2e coupling and the Central quota model
  are structural and recur unless planned for. Acceptance criteria for boring-by-0.2.0:
  credential-absence tests clear env vars (proven by exporting all 8 vars locally and
  watching only the asserting classes fail without the annotations); Verify-vs-prod
  differences listed explicitly with proof elsewhere; rollout plans record the dogfood
  version and treat source fixes as unproven until release + bump; quota math (staged and
  submitted file counts) done before tagging. See .omo/plans/promote-optimizations.md
  Phase 3 for the remaining checklist.

## Architecture changes / fixes

* first separate contributor plugin in its own repo: the benchmark that informs this
  repo's final structure — in particular whether the test fixtures (`easy-test-support`,
  `gradle-plugin-testutils`) stay published for external consumers. Decides the deferred
  TODO.md quota item on fixtures; see also the future-improvements list in AGENTS.md
  (shared `easy-plugin-core` dependency, dropping `-api` publishes, extracting utils).
* publish: promotion/signing matrix test — DONE 2026-10-22 (#41): `promoteToCentral()`
  on a snapshot fails fast / is a silent no-op without the flag; release requires signing
  keys; plain `publish` activates nothing promotion-gated.
* publish: confirm the ~171-file target after the next dogfood bump carries the strip
  (re-run the staging count, update the plan ledger + DEVELOPMENT.md footprint note).
  Required before treating the quota work as done.
* publish: document the 2-tier deployment process (valid, established use case):
  `deployToPortal()` ships markers on every release publish (no quota), while Maven
  artifacts wait for Central quota via `promoteToCentral()` + `-Ppromote`. Spell out
  when a Portal-only release is appropriate (e.g. marker bugfixes while Central is
  capped) and what plain `publish` vs `publish -Ppromote` each deploy to.
* publish: Central quota optimization, remaining: end-to-end strip proof after the next
  dogfood bump carries it (re-run staging count, confirm ~171, update ledger + DEVELOPMENT.md
  note); then any month may ship up to ~5 releases within the 1,000-file limit. October is
  closed at 492 — no further Central release this month.

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
  `./gradlew build` (dropping the `-x :e2e-published:test` carve-out).
  DONE 2026-10-22 (#42 merged): guard opt-in via `-Pe2e` + nightly workflow; Verify is
  plain again. Remaining: confirm the first scheduled nightly run goes green.
