# TODO

This document keeps track of tasks that need to be completed.

## Bugs

* verify next build: release publish must not attempt to deploy to snapshot repos (seen around 0.0.106 release)
* fix configuration-cache violations: external processes started at configuration time — `git rev-parse --abbrev-ref --symbolic-full-name @{u}` and `git remote get-url origin` (see https://docs.gradle.org/9.4.1/userguide/configuration_cache_requirements.html#config_cache:requirements:external_processes)

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
