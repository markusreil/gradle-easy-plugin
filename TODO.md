# TODO

This document keeps track of tasks that need to be completed.

## Bugs

* ensure newline at end of file in codemeta.json when creating/updating.

## Architecture changes / fixes

* switch jackson to kotlinx for serialization
* publish: derive an artifact classifier from the git branch name so every branch
  publishes a distinct snapshot artifact (no overwrites of `latest.integration`),
  while still publishing on every push regardless of branch

## Other tasks

* tests: migrate hand-rolled test doubles to Mockito — jvm-defaults-plugin is done
  (Mockito 5.17.0 in the version catalog); still to do: easy-plugin-core
  `ExtensionRegistrarInjectionTest`/`ExtensionRegistrarEnabledConventionTest`/
  `ExtensionRegistrarCreationTest`/`ExtensionRegistrarCopyTest` (anonymous `object : <Interface>`
  fakes) and publish-plugin `GenerateJreleaserConfigTaskTest`/`SigningWiringTest`/
  `JreleaserConfigValidationTest` (stubs)
* declare Configuration Cache compatibility to silence Plugin Portal warning ("Consider declaring compatibility of your plugin with the following Gradle features: Configuration cache", https://plugins.gradle.org/docs/publish-plugin#declaring-compatibility) + note in README
* Lifecycle listener that collects and reports build statistics
* info task that gives detailed info about publications and repos
* tests: GitAssertions
* test-utils: Helper classes for ProjectBuilder?
* revisit func tests: required or just blowing up build time?

## post plugin update tasks/questions

* gradleTestKit() still needed in func test suite?
* report aggregation in root project still not automatic
