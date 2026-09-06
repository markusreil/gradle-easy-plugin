# TODO

This document keeps track of tasks that need to be completed.

## Contributor plugins

* jvm-defaults: detect and configure test suites
* publish: data class for jreleaser task config properties

## Architecture changes / fixes

* publish: `afterEvaluate { ... }` + `if (state.executed) ...` double-invocation — `ensureDefaultPublication`,
  `wirePublishToMavenLocal` (and the JReleaser wiring `syncEnabled` blocks) run twice for already-evaluated
  projects since a late-registered `afterEvaluate` fires immediately. Harmless today (exists-guards), but
  should run exactly once (e.g. only register `afterEvaluate` when not yet executed).

## Other tasks
