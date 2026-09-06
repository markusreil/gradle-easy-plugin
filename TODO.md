# TODO

This document keeps track of tasks that need to be completed.

## Contributor plugins

* jvm-defaults: detect and configure test suites
* publish: move ensureDefaultPublication to a task or something that can be disabled once the plugin-plugin 
  publication is created. I really dislike the double-invocation of `afterEvaluate { ... }` + `if (state.executed) ...`.

## Architecture changes / fixes

* publish: `afterEvaluate { ... }` + `if (state.executed) ...` double-invocation — `ensureDefaultPublication`,
  `wirePublishToMavenLocal` (and the JReleaser wiring `syncEnabled` blocks) run twice for already-evaluated
  projects since a late-registered `afterEvaluate` fires immediately. Harmless today (exists-guards), but
  should run exactly once (e.g. only register `afterEvaluate` when not yet executed).

## Other tasks
