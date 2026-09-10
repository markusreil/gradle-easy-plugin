# TODO

This document keeps track of tasks that need to be completed.
## Bugs

* "publish" tries to publish to staging repos (both mreil and sonatypeSnapshots) when project version is release.

## Contributor plugins

* jvm-defaults: detect and configure test suites
* publish: make sure sonatypeSnapshots is guarded by release/snapshots logic

## Architecture changes / fixes

* switch jackson to kotlinx for serialization
* publish: derive an artifact classifier from the git branch name so every branch
  publishes a distinct snapshot artifact (no overwrites of `latest.integration`),
  while still publishing on every push regardless of branch


## Other tasks

* upgrade detekt to v2
