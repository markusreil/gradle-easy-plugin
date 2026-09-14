# TODO

This document keeps track of tasks that need to be completed.

## Bugs

* ensure newline at end of file in codemeta.json when creating/updating.

## Contributor plugins

* jvm-defaults: detect and configure test suites

## Architecture changes / fixes

* switch jackson to kotlinx for serialization
* publish: derive an artifact classifier from the git branch name so every branch
  publishes a distinct snapshot artifact (no overwrites of `latest.integration`),
  while still publishing on every push regardless of branch

## Other tasks

* upgrade detekt to v2
* declare Configuration Cache compatibility to silence Plugin Portal warning ("Consider declaring compatibility of your plugin with the following Gradle features: Configuration cache", https://plugins.gradle.org/docs/publish-plugin#declaring-compatibility) + note in README
* Lifecycle listener that collects and reports build statistics
* info task that gives detailed info about publications and repos
