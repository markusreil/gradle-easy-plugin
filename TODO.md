# TODO

This document keeps track of tasks that need to be completed.

## Bugs

## Contributor plugins

* jvm-defaults: detect and configure test suites

## Architecture changes / fixes

* switch jackson to kotlinx for serialization
* publish: derive an artifact classifier from the git branch name so every branch
  publishes a distinct snapshot artifact (no overwrites of `latest.integration`),
  while still publishing on every push regardless of branch
* rethink "runCatching{is enabled && Service.of()}" idiom to get services. No good! Maybe static method
  can return Provider\<Service\>?

## Other tasks

* upgrade detekt to v2
