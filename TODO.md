# TODO

This document keeps track of tasks that need to be completed.

## Contributor plugins

* jvm-defaults: detect and configure test suites
* publish: sign using signing plugin — done (SigningWiring, signingEnabled flag, dual-sign keeps JReleaser for now)
* publish: publish snapshots directly to sonatype — done (toSonatypeSnapshots() via maven-publish, JReleaser nexus2 deployer removed)
* publish: deactivate signing in jreleaser — pending until Gradle signing proven (TODO in JreleaserYaml/SigningWiring)
* publish: applyMavenCentralRules: false to speed up deployment

## Architecture changes / fixes

(none currently)

## Other tasks
