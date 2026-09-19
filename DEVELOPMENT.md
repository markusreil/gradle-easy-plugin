# Development Guide

This document covers contributors and maintainers of `gradle-easy-plugin`. For **usage** as a consumer, see [README.md](README.md).

## Requirements

* **Java 17+** — `org.gradle.jvm.version=17` variant (see `gradle.properties` → `java.toolchainVersion`). The E2E guard runs on the build JVM (17 in CI, 21 locally).
* Gradle 9.4.1 via `./gradlew` (not system `gradle`).

## Project Overview

Multi-project Gradle plugin build (Kotlin + `java-gradle-plugin`). Root `build.gradle.kts` applies `kotlin-jvm`/`detekt`/`spotless` with `apply false` and aggregated reporting (`jacoco-report-aggregation`, `test-report-aggregation`).

Plugins:
* `com.mreil.easy.project` → `com.mreil.easy.ProjectPlugin` (in `easy-plugin`, published as `easy-plugin`) — project root (`EasyExtension`), subproject injection, project contributors
* `com.mreil.easy.settings` → `com.mreil.easy.SettingsPlugin` (in `easy-plugin-settings`, published as `easy-plugin-settings`) — settings-only root (`EasySettingsExtension`), settings contributors; never pushes configuration into projects
* Contributor plugins (internal, via SPI — not applied by ID): `EasyPublishPlugin`, `EasyJvmDefaultsPlugin`, `EasyJvmDefaultsSettingsPlugin`, `EasySemverPlugin`, `EasyCodemetaPlugin` etc. (+ `*-test-plugin` harnesses that apply the core `ProjectPluginEntryPoint` for `withPluginClasspath` functional tests)

The two marker modules are deliberately disjoint: each registers exactly one plugin ID and bundles
only its own scope plus the shared core, so the settings artifact never carries KGP-linked project
classes (see `TWO_JAR_SPLIT.md`).

The two scopes are independent: `com.mreil.easy.settings` creates a settings-only root `EasySettingsExtension` (currently empty) and applies settings contributors; `com.mreil.easy.project` creates the project root `EasyExtension`, injects copied extensions into subprojects and applies project contributors. Each scope owns a separate `PluginRegistryService` (`PluginRegistry.NAME` vs `PluginRegistry.SETTINGS_NAME`) because settings/project plugins may be loaded by different classloaders. Discovery via `PluginRegistry`/`PluginRegistryService` (BuildService) + `EasyPluginContributor` SPI (`META-INF/services/com.mreil.easy.EasyPluginContributor`). `EasyExtension` (`easy { }`) aggregates per-contributor extensions. Extensions can expose a public API via `@PublicType` on the implementation — `ExtensionRegistrar.createExtensionAs` registers under the public type (its `Named` companion) and instantiates the implementation (resolved via `resolvePublicType()`).

## Structure

```
settings.gradle.kts          # includes :easy-plugin, :easy-plugin-settings, :easy-plugin-core, :easy-contributor-api, :easy-contributor-support, :easy-test-support, :gradle-plugin-testutils, :gradle-plugin-utils, :contributor-plugins:publish:..., :contributor-plugins:jvm-defaults:..., :contributor-plugins:semver:..., :contributor-plugins:codemeta:..., :contributor-plugins:project-defaults:..., :contributor-plugins:vcs:..., :contributor-plugins:release:...
build.gradle.kts             # root: lifecycle-base/jacoco-report-aggregation/test-report-aggregation + kotlin-jvm/detekt/spotless apply false; leaf subprojects{} centrally applies Kotlin JVM + detekt (shared config/check) + Spotless; aggregated reports
gradle.properties            # CC/parallel/caching/warning.mode=all + plugin.project/settings IDs (single source; runtime mirror in PluginIds.kt)
gradle/libs.versions.toml    # version catalog (kotlin-jvm 2.3.0, junit-jupiter 5.11.3, assertj 3.27.3, detekt 2.0.0-alpha.6, spotless 8.10.2)
config/detekt/detekt.yml     # detekt 2.x config (maxLineLength 140, EmptyFunctionBlock off)
easy-plugin/build.gradle.kts           # java-gradle-plugin umbrella (PROJECT scope): registers com.mreil.easy.project, aggregates easy-plugin-core + all project :contributor-plugins:*:*-plugin (excludes -settings-plugin); test suites + pluginUnderTestMetadata (project + settings markers for cross-scope tests) + verifyShadowPackaging
easy-plugin-settings/build.gradle.kts  # java-gradle-plugin umbrella (SETTINGS scope): registers com.mreil.easy.settings, aggregates easy-plugin-core + :*jvm-defaults-settings-plugin; own verifyShadowPackaging
easy-plugin-core/build.gradle.kts      # java-library: ProjectPluginEntryPoint/SettingsPluginEntryPoint bases, PluginRegistryService, PluginRegistrar, ExtensionRegistrar, EasyExtension, EasySettingsExtension
easy-contributor-api/src/main/kotlin/com/mreil/easy/ # PluginIds, PluginRegistry, EasyPluginContributor, ApplyToSubprojects, EnabledBy, Named, EasyPluginExtension, EasySettingsExtension, CanBeEnabled, PublicType
easy-contributor-support/build.gradle.kts   # plain Kotlin lib: AbstractEasyProjectPlugin, AbstractEasySettingsPlugin, PluginLifecycle
easy-test-support/build.gradle.kts         # fixtures + easy-specific test helpers (Dummy*Plugin via ServiceLoader, PluginTestUtils.loadGradleProperty); not generic – for functional tests
gradle-plugin-testutils/src/main/kotlin/com/mreil/gradletest/project/ # generic TestKit helpers: GradleTestProject, ProbeTask, templates, assertj; package com.mreil.gradletest (no easy deps)
gradle-plugin-utils/src/main/kotlin/com/mreil/utils/ # generic PropertyResolver – to be extracted to separate repo
contributor-plugins/publish/publish-plugin-api/       # public EasyPublishExtension interface + MavenRepoSpec
contributor-plugins/publish/publish-plugin/           # EasyPublishPlugin + EasyPublishContributor + DefaultEasyPublishExtension (@PublicType) + META-INF/services
contributor-plugins/publish/publish-test-plugin/      # harness: com.mreil.easy.test.publish → PublishTestHarnessPlugin
contributor-plugins/jvm-defaults/jvm-defaults-plugin/ # project scope: EasyJvmDefaultsPlugin + EasyJvmDefaultsKotlinPlugin + EasyJvmDefaultsContributor
contributor-plugins/jvm-defaults/jvm-defaults-settings-plugin/ # settings scope: DokkaJavadocSettingsPlugin + EasyJvmDefaultsSettingsContributor
contributor-plugins/jvm-defaults/jvm-defaults-test-plugin/ # harness: com.mreil.easy.test.jvm
contributor-plugins/semver/...                        # semver-plugin-api / semver-plugin / semver-test-plugin
contributor-plugins/codemeta/...                      # codemeta-plugin-api / codemeta-plugin / codemeta-test-plugin
```

## Core Mechanism

* `EasyPluginContributor` SPI (`easy-contributor-api/src/main/kotlin/com/mreil/easy/EasyPluginContributor.kt`) — `META-INF/services/com.mreil.easy.EasyPluginContributor`. Contributors declare `projectPlugins()`, `settingsPlugins()`, `pluginExtensions()` (`EasyPluginExtension`).
* `PluginRegistry`/`PluginRegistryService` (BuildService) + `ExtensionRegistrar`/`PluginRegistrar` — eager `easy { }` creation, ordered application (`orderedAllProjects`), `@ApplyToSubprojects` fan-out, `@EnabledBy(Extension::class)` + `CanBeEnabled.enabled` + `AbstractEasyProjectPlugin.afterEnabled`/`afterEvaluate` for lazy enabling. Settings and project scopes use separate registry instances (`PluginRegistry.SETTINGS_NAME` vs `PluginRegistry.NAME`, via `InternalProjectUtils.getRegistry(name)`); `SettingsPluginEntryPoint` creates `EasySettingsExtension` and neither applies `ProjectPluginEntryPoint` nor copies extensions into projects.
* `@PublicType` — `ExtensionRegistrar.createExtensionAs` (`easy-plugin-core/src/main/kotlin/com/mreil/easy/ExtensionRegistrar.kt:160`) registers extensions under the public `-api` interface (e.g. `EasyPublishExtension`) while instantiating the internal `@PublicType` implementation.
* **External plugin integration (standard practice):** never add a compile/runtime dependency on an external Gradle plugin's types. Integrate by id and react via `project.pluginManager.withPlugin(id) { ... }` (or `withId`/`withType`), no-oping when it is absent. The same wiring then works whether the consumer applies the external plugin directly or a settings-scope opt-in only adds its marker to the root buildscript classpath — settings scope adds marker/classpath only, project scope applies and rewires. Reference: the two Dokka paths (`DokkaJavadocSettingsWiring.inject` + `DokkaJavadocWiring.configureJavadocJar`), covered by both `DokkaJavadocFuncTest` scenarios (settings opt-in; consumer-applied `dokka-javadoc`).

Plugin IDs are the single source in `gradle.properties` (`plugin.project`/`plugin.settings`), read via `providers.gradleProperty(...).get()` in `easy-plugin/build.gradle.kts` / `easy-plugin-settings/build.gradle.kts` respectively; runtime mirror is `easy-contributor-api/.../PluginIds.kt` — keep in sync.

## Contributor Plugins (internals)

| Contributor | API / Impl | Plugin | Extension | Internals |
|---|---|---|---|---|
| `contributor-plugins/publish` | `publish-plugin-api: EasyPublishExtension` + `MavenRepoSpec` / `publish-plugin: DefaultEasyPublishExtension` (`@PublicType`, `enabled` true by default) | `EasyPublishPlugin` (`@EnabledBy(EasyPublishExtension::class)`) | `easy.publish` | Wraps `maven-publish`. Creates default `maven` publication from `java` component (unless `java-gradle-plugin` present), normalizes coordinates/POM/versionMapping, wires `mavenRepo {}` and snapshot/release filtering (uses `EasySemver`). See `EasyPublishPlugin.kt:32`. |
| `contributor-plugins/jvm-defaults` | no API extension | `EasyJvmDefaultsPlugin` | — | Configures `JavaPluginExtension` with `withSourcesJar()`/`withJavadocJar()` when `java` plugin present. `EasyJvmDefaultsContributor`. |
| `contributor-plugins/semver` | `semver-plugin-api: EasySemverExtension` / `semver-plugin: DefaultEasySemverExtension` | `EasySemverPlugin` (`@EnabledBy`) | `easy.semver` | Exposes `EasySemver.of(project): Provider<Semver>` (validates SEMVER via `semver4j`). |
| `contributor-plugins/codemeta` | `codemeta-plugin-api: EasyCodemetaExtension` (`filename` default `codemeta.json`, `updateOnRelease` default `true`) / `codemeta-plugin: DefaultEasyCodemetaExtension` | `EasyCodemetaPlugin` (`@EnabledBy`) | `easy.codemeta` | Registers `CodemetaService` (kotlinx.serialization) and `generateCodemeta`. If file missing, every task depends on `generateCodemeta` which creates initial `codemeta.json` and fails. When `updateOnRelease` is enabled, registers a `ReleaseLifecycleListener` via `EasyRelease.beforePreReleaseCommit` that updates `version` (resolved release version) and `dateModified` (today, ISO date) in `codemeta.json` on every release; the file is committed together with the version file. |
| `contributor-plugins/release` | `release-plugin-api: EasyReleaseExtension` / `release-plugin: DefaultEasyReleaseExtension` (`@PublicType`, `enabled` true by default) | `EasyReleasePlugin` (`@EnabledBy(EasyReleaseExtension::class)`) | `easy.release` | Registers `preReleaseCheck` (verifies clean tree, in-sync-with-remote, release-branch pattern `releaseBranchPattern` default `(main\|master\|rel-.*)`, group/version set; resolves release/next version providers with override precedence system-prop `easy.release.version`/`easy.release.nextVersion` > semver and logs current/release/next versions; versions/names wired once into `ReleaseStateService` (shared service, `OperationCompletionListener` rolling a failed release-group task back to the gate commit — hard `git reset --hard <gateSha>` discards the local release/snapshot commits and the half-written version file, then deletes the release tag only if it points at a commit created after the gate so a pre-existing tag is left alone; nothing is ever reset against the remote, and non-release-group failures only log the captured state), `preReleaseCheck` reads log-values from the service and snapshots the commit SHA in its action (HEAD-at-gate); `preReleaseCommit` rewrites the `version=` line of `versionFile` (default root `gradle.properties`, overridable via `versionFile`) to the resolved release version and commits that file together with any files returned by `ReleaseLifecycleListener` implementations registered via `EasyRelease.beforePreReleaseCommit` via `VcsService.addAndCommit` — message from `commitMessageTemplate` (default `Set version for release: $v`, `$v` replaced with release version), no-op when the version file already has the release version and no listener contributed files, `VcsNone` no-op (files updated, nothing committed) when no VCS is available; `preReleaseTag` tags the release commit with the release version via `VcsService.tag` — name resolved once in `ReleaseStateService.tagName` from `tagTemplate` (default `v$v`, `$v` replaced with release version; tasks and rollback read it there so they can never disagree), runs after `preReleaseCommit` so the tag points at the version-bump commit, `VcsNone` no-op when no VCS is available; `postReleasePush` bumps `version=` of `versionFile` to the resolved next version (precedence `easy.release.nextVersion` > semver `withIncPatch().withPreRelease("SNAPSHOT")`), commits it via `VcsService.addAndCommit` — message from `postReleaseCommitMessage` (default `Set new version after release: $v`, `$v` replaced with next version), no-op when the file already has the next version, then pushes the commit and the release tag atomically via `VcsService.push(tag)` (`git push --atomic origin HEAD <tag>`), `VcsNone` no-op when no VCS is available; `release` depends on the full chain (check → commit → tag → push), publishing is a separate Gradle invocation (version file read at configuration time); every `release`-grouped task runs after `preReleaseCheck` (configureEach gate)). |

Each contributor has a `-test-plugin` harness (`com.mreil.easy.test.publish` etc.) that depends on `:easy-plugin-core` and applies `ProjectPluginEntryPoint` for `withPluginClasspath` functional tests, keeping each harness scoped to its own contributor (not the whole project contributor set). Harnesses are **not** published (`easy { publish.enabled = false }`).

## Publishing (deployed artifact set)

The `publish` contributor publishes **every Maven publication of every project** to
**every repository declared via `easy.publish.mavenRepo(...)`**, filtered by
`RepoRouting` name rules: a repo name containing `release` only
receives non-snapshot versions, one containing `snapshot` only snapshots (snapshot
routing requires `easy.semver`), any other name is neutral and receives everything.
`toMavenStaging()`/`toSonatypeSnapshots()` are `mavenRepo`s under the hood
(`mavenStaging`, `sonatypeSnapshots`); `toMavenCentral()` instead stages into
`mavenStaging` and lets JReleaser deploy that staged set to Maven Central.

This section is the source of truth for the deployed artifact set — update it
whenever the publish behaviour changes (e.g. new module, new publication,
marker changes, harness publishing).

Bundled-only modules set `easy.publish.enabled = false` in their `build.gradle.kts`: all
`*-test-plugin` harnesses, the contributor `-plugin` implementations (except `vcs-plugin`), and
`easy-plugin-core`. The two marker modules each Shadow-bundle only their own scope plus shared core,
and publish their third-party dependencies in the POM/Gradle metadata instead.

Publish-enabled projects:

* 7 easy modules: `easy-plugin`, `easy-plugin-settings`, `easy-contributor-api`,
  `easy-contributor-support`, `easy-test-support`, `gradle-plugin-testutils`,
  `gradle-plugin-utils`
* 7 contributor API modules: `publish-plugin-api`, `jvm-defaults-plugin-api`,
  `semver-plugin-api`, `codemeta-plugin-api`, `project-defaults-plugin-api`,
  `vcs-plugin-api`, `release-plugin-api`
* `vcs-plugin` (the one contributor implementation that is not publish-disabled)
* 2 plugin markers, one per marker module (via `java-gradle-plugin`):
  `com.mreil.easy.project.gradle.plugin` (from `easy-plugin`) and
  `com.mreil.easy.settings.gradle.plugin` (from `easy-plugin-settings`)
  — POM-only, groupId = plugin ID (`com.mreil.easy.project` / `com.mreil.easy.settings`),
  artifactId = `<plugin-id>.gradle.plugin`

Per module and version (`<groupId>/<artifactId>/<version>/`):

* `-jar`, `-sources.jar`, `-javadoc.jar`, `.pom`, `.module`
* `.asc` signature for every file above (`jar`, `sources.jar`, `javadoc.jar`, `pom`,
  `module`, marker `pom`) when `easy.publish.signingEnabled` (default `true`) and GPG
  keys are configured — CI and this repo both use `jreleaser.gpg.*` properties
* checksums (`md5`/`sha1`/`sha256`/`sha512`) per file, generated by Gradle
* The main jars of `easy-plugin` and `easy-plugin-settings` are Shadow **fat jars**
  (classifier `""`): each embeds only its own scope's in-build modules plus shared core,
  never third-party artifacts and never the other scope's classes. The bundling filter matches
  the internal project group (`com.mreil.gradleplugins.easy`), so the test-only `*-test-plugin`
  harnesses, `easy-test-support` and `gradle-plugin-testutils` are never bundled.
  Third-party runtime dependencies (declared on each module's Shadow `shadow` configuration)
  stay out of the jars and are published at `runtime` scope in the `.pom`/`.module`, so consumers
  resolve them from Maven Central: the settings marker declares `commons-configuration2` +
  `kotlin-stdlib`; the project marker additionally declares `kotlinx-serialization-json` and
  `semver4j`.
  `:easy-plugin:verifyShadowPackaging` and `:easy-plugin-settings:verifyShadowPackaging` (both
  wired into `check`) assert jar contents, third-party publication and scope disjointness —
  `check` fails on drift

**Local verification (no remote credentials required):**

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew publishAllPublicationsToMavenStagingRepository
```

Every project stages exactly the artifact set above into its own `build/stagingRepo`
directory (signed, with checksums). This is the fastest way to confirm what the next
`publish` would deploy to any `mavenRepo()`-declared repository.

## Testing

* **Unit:** `easy-plugin/src/test`, `easy-plugin-core/src/test`, `easy-contributor-support/src/test`, `contributor-plugins/*/src/test` — JUnit Jupiter 5.11.3 + AssertJ, `ProjectBuilder` for `PluginRegistryService`/`ExtensionRegistrar`/`PluginRegistrar`.
* **Functional:** `easy-plugin/src/functionalTest` — `GradleRunner` with `withPluginClasspath()`; its `pluginUnderTestMetadata` carries both marker modules (project + settings) so cross-scope tests can apply both IDs (TestKit flattens the classpath, so this does not exercise the real two-jar topology — that is the published-consumer E2E in `TWO_JAR_SPLIT.md` step 5). `contributor-plugins/*/*-test-plugin` — `GradleRunner` + harness plugins (`com.mreil.easy.test.publish` etc.) that apply the core `ProjectPluginEntryPoint`.
* **Isolated functional tests (recommended):** Use `@DisableAllEasyPlugins` + `@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)` from `easy-test-support` (`com.mreil.easy.test.support`). The extension sets `easy.disableAllPlugins=true` on both the host (via `System.setProperty`) and the TestKit child (via `GradleTestProject.systemProperty`) before each test and clears after. With all `CanBeEnabled` disabled (`ExtensionRegistrar.kt:131`), tests explicitly re-enable needed plugins via `easy { <name> { enabled.set(true) } }` – isolated by design, no transitive surprise (e.g. `publish` needing `codemeta` must declare `implementation(project(":contributor-plugins:codemeta:codemeta-plugin"))` and `easy { publish { enabled.set(true) }; codemeta { enabled.set(true) } }`). For low-level verification of the flag itself, see `DisableAllPluginsFuncTest.kt:14` which still uses manual `@SetSystemProperty` + `systemProperty`.

Run `./gradlew :easy-plugin:check` (or `./gradlew build` for all modules + aggregated reports) before submitting.

New contributor plugins must ship a configuration-cache compatibility test that fails on CC validation problems (e.g. external processes started at configuration time) — the VCS plugin's `git rev-parse ... @{u}` / `git remote get-url origin` calls broke CC and were only found after release.

## Manual Release / Snapshot Testing

No smoke-test projects are committed — they required constant up-keeping and are now done ad-hoc.
See AGENTS.md → "Ad-hoc Release / Snapshot Verification" for the throwaway-project recipe (snapshot
via `latest.integration` from `mreilComGradlePluginsSnapshots`, release pinned from the Plugin
Portal, plus the local-Nexus JReleaser rehearsal). `./gradlew :easy-plugin:publish` still deploys the
snapshot those ad-hoc projects consume.

`easy-plugin/build.gradle.kts` adds only `publishing.repositories` for snapshots; marker publications are created by `java-gradle-plugin`.

## CI (GitHub Actions)

Maven-repository credentials are stored in the repository's Actions settings
(`Settings → Secrets and variables → Actions`). The publish contributor resolves
repository credentials from the Gradle properties `<repoName>Username`/`<repoName>Password`
(`passwordCredentials` repos), so GitHub exposes them via Gradle's
`ORG_GRADLE_PROJECT_<NAME>` environment-variable convention (env vars are
converted to project properties).

Currently configured in GitHub:

| Kind | Env var (GitHub) | Gradle property | Used for |
|---|---|---|---|
| Variable | `ORG_GRADLE_PROJECT_MREILCOMGRADLEPLUGINSSNAPSHOTSUSERNAME` | `mreilComGradlePluginsSnapshotsUsername` | Snapshot repo user (`mreilComGradlePluginsSnapshots`) |
| Secret | `ORG_GRADLE_PROJECT_MREILCOMGRADLEPLUGINSSNAPSHOTSPASSWORD` | `mreilComGradlePluginsSnapshotsPassword` | Snapshot repo password |
| Variable | `ORG_GRADLE_PROJECT_SONATYPESNAPSHOTSUSERNAME` | `sonatypeSnapshotsUsername` | Sonatype snapshots user (`toSonatypeSnapshots()`) |
| Secret | `ORG_GRADLE_PROJECT_SONATYPESNAPSHOTSPASSWORD` | `sonatypeSnapshotsPassword` | Sonatype snapshots token |
| Secret | `JRELEASER_PGP_PRIVATEKEY` | `jreleaser.gpg.privateKey` (env `JRELEASER_GPG_PRIVATE_KEY`) | PGP signing (base64 armored private key) |
| Secret | `JRELEASER_PGP_PASSPHRASE` | `jreleaser.gpg.passphrase` (env `JRELEASER_GPG_PASSPHRASE`) | PGP signing passphrase |
| Secret | `GRADLE_PUBLISH_KEY` | `gradle.publish.key` | Plugin Portal `publishPlugins` (`gradle.publish.key`) |
| Secret | `GRADLE_PUBLISH_SECRET` | `gradle.publish.secret` | Plugin Portal `publishPlugins` (`gradle.publish.secret`) |

Notes:

* Regular **variables** hold the non-secret usernames; **secrets** hold the passwords/tokens (masked in logs).
* The `ORG_GRADLE_PROJECT_` convention only reaches repositories read through
  `maven-publish`'s `PasswordCredentials`. Other credential sets (JReleaser
  `jreleaser.mavencentral.*`, GPG signing, Plugin Portal `gradle.publish.*`,
  Nexus) are separate — see `contributor-plugins/publish/publish-plugin/README.md`.
* GPG keys are read directly as env vars by the publish plugin's `PropertyResolver`
  (`jreleaser.gpg.privateKey` → `JRELEASER_GPG_PRIVATE_KEY`, `jreleaser.gpg.passphrase`
  → `JRELEASER_GPG_PASSPHRASE`), so the workflow remaps the GitHub secret names to
  those exact env var names in the `publish` job's `env` block.
* Manual snapshot smoke-tests (ad-hoc, see AGENTS.md → "Ad-hoc Release / Snapshot
  Verification") resolve the `com.mreil.easy.*` plugins from
  `mreilComGradlePluginsSnapshots`, so CI deploys and manual dogfooding share the same
  credentials.

## Commands

```bash
./gradlew build                                # all projects, warning.mode=all (includes aggregated coverage/reports)
./gradlew :easy-plugin:check                   # unit + functional + detekt + jacocoTestReport
./gradlew :easy-plugin:test                    # unit tests only
./gradlew :easy-plugin:functionalTest          # functional tests (GradleRunner)
./gradlew :easy-plugin:detekt                  # code analysis
./gradlew :easy-plugin-core:check              # core unit tests + detekt + jacoco
./gradlew :contributor-plugins:publish:publish-plugin:check
./gradlew :contributor-plugins:publish:publish-test-plugin:check
./gradlew :contributor-plugins:jvm-defaults:jvm-defaults-plugin:check
./gradlew :contributor-plugins:jvm-defaults:jvm-defaults-test-plugin:check
./gradlew :easy-plugin:publishToMavenLocal
./gradlew :easy-plugin:publish                 # publish snapshots to mreilComGradlePluginsSnapshots (requires credentials)
./gradlew spotlessCheck                        # verify Kotlin/Gradle formatting (ktlint)
./gradlew spotlessApply                        # auto-format all sources
./gradlew testCodeCoverageReport testAggregateTestReport  # aggregated JaCoCo + test reports (root)
```

Use `./gradlew` (wrapper, Gradle 9.4.1) — not system `gradle`.

## Conventions

* Use imports instead of fully qualified names everywhere (e.g., `import kotlin.reflect.KClass` + `KClass`).
* Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`). Root `build.gradle.kts` must keep `kotlin-jvm`/`detekt`/`spotless` `apply false`; its leaf `subprojects { }` centrally applies Kotlin JVM + detekt + Spotless and wires the shared detekt config + `check.dependsOn("detekt")` — keep module build files free of those declarations.
* Plugin IDs are the single source in `gradle.properties` (`plugin.project`/`plugin.settings`), read via `providers.gradleProperty(...).get()` in `easy-plugin/build.gradle.kts` / `easy-plugin-settings/build.gradle.kts` respectively; runtime mirror is `easy-contributor-api/.../PluginIds.kt` — keep in sync.
* Plugin registration via `gradlePlugin { plugins.creating { id, implementationClass } }`. The build dogfoods the released `com.mreil.easy.settings` (settings scope, in `settings.gradle.kts`) and `com.mreil.easy.project` (project scope, in the root build); the project plugin's `jvm-defaults` contributor auto-configures test suites (framework + catalog test deps + `java-gradle-plugin` classpath/`testSourceSets` + `check` wiring) and applies `jacoco`; module build files therefore only declare repo-specific test-helper deps and `jvmArgs`, and JaCoCo report formats are centralized in the root `subprojects` block.
* CC/parallel/caching/warning.mode=all are on — tasks must be CC-compatible (providers/properties, no `project` at execution).
* ServiceLoader SPI: `EasyPluginContributor` in `easy-contributor-api`, `META-INF/services/com.mreil.easy.EasyPluginContributor`. The core entry-point bases `ProjectPluginEntryPoint`/`SettingsPluginEntryPoint` and `PluginRegistrar` call `registry.loadFromServiceLoader(javaClass.classLoader)` (the caller's loader — which is why the concrete marker classes, not the core bases, are registered under the plugin IDs) then defer via `project.plugins.withType(ProjectPluginEntryPoint::class.java) { apply }` / `settings.pluginManager.withPlugin(PluginIds.SETTINGS) { apply }` + `pluginManager.apply(kclass.java)` (no `newInstance().apply()`). Settings and project scopes resolve distinct `PluginRegistryService` instances via `PluginRegistry.SETTINGS_NAME`/`PluginRegistry.NAME`; `SettingsPluginEntryPoint` neither applies the project entry point nor copies its extension into projects. Ordering is `orderedAllProjects` (root + subprojects sorted by path); `@ApplyToSubprojects` controls fan-out, `@EnabledBy` + `CanBeEnabled` controls lazy enabling via `easy { }`. Extensions can expose public API via `@PublicType` — `ExtensionRegistrar.createExtensionAs` registers under public type and instantiates implementation via `resolvePublicType()`.

## Dependencies

* All dependencies/plugins must be in `gradle/libs.versions.toml` and referenced by alias (`alias(libs.plugins.kotlin.jvm)`, `libs.assertj.core`). No hardcoded coordinates/versions. This includes the formatter: ktlint is pinned via `libs.versions.ktlint` (currently 1.8.0) in the root Spotless block, so a Spotless upgrade cannot silently change it.

## Code Analysis

* detekt 2.0.0-alpha.6 (`dev.detekt` plugin). Config in `config/detekt/detekt.yml` (maxLineLength 140, EmptyFunctionBlock off). Root `build.gradle.kts` routes the conventional `detekt` task — and therefore `check` — through the type-aware per-source-set tasks (`detektMain`, `detektTest`, `detektFunctionalTest`, …) and disables the plain task's own run. `check` also depends on `jacocoTestReport` (plus `functionalTest` where applicable). See AGENTS.md → "Code Analysis".
* Spotless (with ktlint, version pinned as above) enforces formatting across Kotlin sources and Gradle scripts (centralized in root `build.gradle.kts` for leaf projects). Run `./gradlew spotlessCheck` / `spotlessApply`.
* Spotless troubleshooting: a `spotlessKotlinCheck` failure shaped like `FILE:LINE_UNDEFINED ktlint(java.lang.reflect.InvocationTargetException) (...)` is not a real lint — it is Spotless wrapping a ktlint engine throw caused by stale/corrupt per-machine formatter state (seen when the same tree is green in CI/Docker). Re-run with `--rerun-tasks`; if it persists, `./gradlew --stop` and delete the cached ktlint artifacts (`rm -rf ~/.gradle/caches/modules-2/files-2.1/com.pinterest.ktlint`). Use `--info --stacktrace` to reveal the underlying `Caused by:` when the crash is genuine.

## Editing Guidelines

* Prefer editing over creating files. Match existing Kotlin style (no extra comments unless requested).
* When adding a new plugin/task/extension, update `gradlePlugin` block (or contributor SPI + `META-INF/services`), add `EasyPluginExtension` + `EnabledBy` if needed, add tests in both suites, and verify with `check`.
* Do not disable CC/parallel/caching/warning.mode without justification.

## Future Improvements

* Reconcile the other unmerged branches: `fix-KGP-classloader-issues`, `split-settings-and-project-plugin`, `central-kgp-application`, `fix-double-action`, `add-central-publishing`.
* Publish a shared `easy-plugin-core` Maven dependency instead of bundling it into both marker jars.
* Drop the redundant `-api` publishes (they are bundled *and* published today).
* Extract `PropertyResolver` (and the `com.mreil.utils` helpers) so the settings marker no longer declares an unused `commons-configuration2` dependency.
* Rename `easy-plugin` → `easy-plugin-project` for symmetry with `easy-plugin-settings`.
* Contributor harnesses use `withPluginClasspath`; add new contributors under `contributor-plugins/<name>/<name>-plugin` + `<name>-test-plugin` with `Easy<Name>Plugin`/`Easy<Name>Extension` naming.
* Design/risk record for the settings/project artifact split: `TWO_JAR_SPLIT.md`.
