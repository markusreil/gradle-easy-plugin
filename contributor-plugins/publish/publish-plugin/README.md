# publish-plugin (com.mreil.easy.publish)

Easy plugin that wraps Gradle's [`maven-publish`](https://docs.gradle.org/current/userguide/publishing_maven.html)
to publish a project's artifacts to Maven repositories with minimal configuration.

It is a *contributor plugin*: it is discovered via the `EasyPluginContributor`
ServiceLoader SPI (see `EasyPublishContributor`) and applied through the shared easy
plugin infrastructure. It activates by default (`enabled` defaults to `true` in `DefaultEasyPublishExtension`) and can be disabled via `easy.publish.enabled.set(false)`. The public extension API (`EasyPublishExtension` + `MavenRepoSpec`) lives in `publish-plugin-api`; the implementation
(`DefaultEasyPublishExtension` with `@PublicType`) and wiring (`EasyPublishPlugin`) live here.

## Features

* **Zero-config publications** – when applied to a `java` project, applies
  `maven-publish` and creates a default `maven` publication from the `java`
  component.
* **Plugin markers supported** – for projects using `java-gradle-plugin`, the
  plugin recognizes the existing publications (including `*PluginMarkerMaven`) and
  does not create a conflicting default publication.
* **Consistent coordinates** – `groupId`/`artifactId`/`version` are derived from the
  project; publishing fails fast with a clear error if `group`/`version` are unset.
* **POM metadata** – every publication gets a POM with name, description, URL
  (from Codemeta when enabled, else `codeRepository`), license, developers and SCM
  metadata plus dependency version mapping. Without Codemeta, `url`/`scm` are left
  unset rather than invented — `checkCentralPoms` reports them when deploying to
  Maven Central.
* **Declarative repositories** – the `easy.publish.mavenRepo(...)` DSL attaches
  named Maven repositories to the `publishing` extension lazily (both `Action<MavenRepoSpec>` and convenience `mavenRepo(name, url, role, withPasswordCredentials)` overloads). Repos flagged `promotionOnly` are skipped by plain `publish` and attached with `-Ppromote` (promoting a snapshot fails fast).
* **Password credentials** – opt-in per repository via `passwordCredentials` (or `withPasswordCredentials = true`).
* **Staging repository** – `toMavenStaging(path)` creates a `mavenStaging` file repo under `build/<path>` (default `build/stagingRepo`).
* **Maven local wiring** – `deployToMavenLocal()` makes `publish` depend on `publishToMavenLocal`.
* **Semver-aware routing** – when `easy.semver` is enabled (`easy { semver {} }`), the version is parsed via `semver4j` (`EasySemver.of(project)`). A version is a snapshot iff its semver pre-release is exactly `SNAPSHOT` (Maven convention); `0.x` releases and other pre-releases (e.g. `1.0.0-RC1`) are treated as releases. Every repo declares a `role` (`RepoRole.SNAPSHOT`/`RELEASE`/`NEUTRAL`): snapshots publish to `SNAPSHOT` repos, releases to `RELEASE` repos, `NEUTRAL` repos always publish. An unset role fails fast. Without semver, a `-SNAPSHOT` version suffix decides instead, so routing always filters.
* **Sonatype snapshots** – `deployToSnapshots()` publishes snapshots directly to Central's snapshot repository via `maven-publish` (parallel, no JReleaser round-trip). Creates the `sonatypeSnapshots` repo (`https://central.sonatype.com/repository/maven-snapshots/` with standard `sonatypeSnapshotsUsername`/`sonatypeSnapshotsPassword` credentials) unless already declared manually. It is a pure repo shorthand: routing is decided by semver when enabled, or by the `-SNAPSHOT` suffix when semver is off — no semver requirement.
* **Gradle Plugin Portal** – `deployToPortal()` makes `publish` run the `publishPlugins` task (from `com.gradle.plugin-publish`) on release versions in plugin projects. Snapshots are silently skipped. Intended as a build-wide toggle in the root `easy { publish { } }` block: it inherits to every project, but projects that are not Gradle plugin projects (no `publishPlugins` task) — including a non-`java` root with no `publish` task — are skipped silently (info-level note). Missing Portal credentials (`gradle.publish.key` / `gradle.publish.secret`) in a plugin project fail `publish` with an actionable error. Credentials can be provided via `GRADLE_PUBLISH_KEY`/`GRADLE_PUBLISH_SECRET` environment variables or the corresponding Gradle properties.

## Usage

Apply the easy project plugin; the `publish` extension is enabled by default:

```kotlin
plugins {
    id("com.mreil.easy.project")
}

easy {
    publish {
        /* Disable all publishing defaults. Default is `true`. */
        enabled = false
        /* Publish to a remote repo. */
        mavenRepo("releases") {
            url.set("https://repo.example.com/releases")
            role.set(RepoRole.RELEASE)
            passwordCredentials.set(true) // optional. Use gradle default mechanism to retrieve credentials.
        }
        /* Publish to a local repo. */
        mavenRepo("local", "build/repo", RepoRole.NEUTRAL)
        /* Publish to a promotion-gated repo: skipped by plain `publish`, attached with `-Ppromote`. */
        mavenRepo("gated") {
            url.set("https://repo.example.com/gated")
            role.set(RepoRole.RELEASE)
            promotionOnly.set(true)
        }
        /* Execute `publishToMavenLocal` when `publish` task is executed */
        deployToMavenLocal()
        /* Publish to a per-project repo in the project's build directory
           Mainly used for publishing to mavenCentral. 
           Default is: `./build/stagingRepo`. */
        toMavenStaging()
        toMavenStaging("custom")
        /* Publish to Maven Central via jreleaser cli. */
        deployToCentral()
        /* Publish to the Gradle Plugin Portal on releases (plugin projects only). */
        deployToPortal()
        /* Promotion-gated variants: active only on `-Ppromote` invocations. */
        promoteToCentral()
        promoteToPortal()
        /* Enable PGP artifact signing. */
        signingEnabled = true
    }
}
```

Then publish with Gradle's standard tasks:

```bash
./gradlew publish                     # publish to all declared repositories
```


Release and snapshot routing:

```kotlin
easy {
    // semver decides routing when enabled; without it the -SNAPSHOT suffix decides
    // semver {
    //     enabled.set(true)
    // } 
    publish {
        mavenRepo("myRelease", "https://repo.example.com/releases", RepoRole.RELEASE)
        mavenRepo("mySnapshot", "https://repo.example.com/snapshots", RepoRole.SNAPSHOT)
        mavenRepo("myNeutral", "https://repo.example.com/central", RepoRole.NEUTRAL) // always published
        // version 1.0.0 -> publishes to myRelease + myNeutral
        // version 1.0.0-SNAPSHOT -> publishes to mySnapshot + myNeutral
        // version 0.0.100 -> publishes to myRelease + myNeutral
        // version 1.0.0-RC1 -> publishes to myRelease + myNeutral
    }
}
```

Snapshots to Sonatype Snapshot Repo (pure repo shorthand — routing via semver or `-SNAPSHOT` suffix):

```kotlin
easy {
    // semver { enabled.set(true) }
    publish {
        toSonatypeSnapshots() // -> sonatypeSnapshots repo, parallel maven-publish
    }
}
```

## Tasks

The plugin registers its own tasks in two layers — per-project wiring from
`EasyPublishPlugin` and root-only Central/JReleaser wiring from `EasyJreleaserPlugin` —
plus the dynamically generated `maven-publish` tasks. All plugin tasks are registered
lazily and only become active when the feature that needs them is switched on.

### Per-project tasks (registered in every enabled project)

| Task | Active when | What it does / how it is wired |
| ---- | ----------- | ------------------------------- |
| `checkCentralPoms` (`verification`) | `toMavenCentral()` | Validates this project's generated POMs against the Maven Central metadata requirements (missing developer emails are warnings, everything else fails). Every `PublishToMavenRepository` upload depends on it; when central is on, it also consumes each publication's `generatePomFileFor*` output (`onlyIf` skips it when the project has no POMs). |
| `stripSignatureChecksums` (`verification`) | `toMavenCentral()` | Two uploads: (1) `maven-publish` stages into the local `mavenStaging` dir, (2) JReleaser deploys the cleaned dir to Central. This task runs between them — after the staging upload, before the deploy — removing signature checksums (`*.asc.md5/sha1/sha256/sha512`) and optional SHA-256/512 artifact checksums (Central only wants `.md5`/`.sha1`). Registered only when a `stagingPath` is set. |
| `cleanStagingRepo` (`publishing`) | `stagingPath` set | Deletes the plugin-managed staging dir (`build/<stagingPath>`) so stale prior-version artifacts never reach Central. The `mavenStaging` upload task depends on it. |

### Root-only tasks (`EasyJreleaserPlugin`)

| Task | Active when | What it does / how it is wired |
| ---- | ----------- | ------------------------------- |
| `generateJreleaserConfig` (`publishing`) | `toMavenCentral()` | Generates `build/jreleaser/jreleaser.json` from the enabled projects' staging dirs (projects with publications only), project coordinates and Maven Central credentials (or the test-Nexus override properties). Depends on every project's `checkCentralPoms`, keeping POM validation a separate step ahead of generation. |
| `publishToMavenCentral` (`publishing`) | `toMavenCentral()`, run manually or via root `publish` on a non-`-SNAPSHOT` version | Runs the JReleaser CLI (`deploy`) via `JavaExec` against the generated config. Depends on `generateJreleaserConfig` and every `mavenStaging` upload + `stripSignatureChecksums`, so one invocation stages, validates and deploys. Skips with a warning when nothing was staged. |
| `publish` (root) (`publishing`) | Always (registered on first use) | Lifecycle aggregation task: depends on every subproject `publish`, so a single `./gradlew publish` stages all modules. When central is on it additionally depends on `publishToMavenCentral`. |


## Extension reference

### `easy.publish`

The public `EasyPublishExtension` interface (in `publish-plugin-api`) backs the `easy { publish { ... } }` block and gates the
plugin's activation (`enabled` defaults to `true` — publishing is active out of the box; set `enabled.set(false)` to disable). The implementation is `DefaultEasyPublishExtension` (in `publish-plugin`), annotated with
`@PublicType(EasyPublishExtension::class)` so `ExtensionRegistrar.createExtensionAs` registers the extension under the
interface's `Named` companion (`"publish"`) while instantiating the implementation. `mavenRepos` is intentionally internal to the
implementation and not part of the public API – consumers use `mavenRepo(name) { ... }`.

| Member | Description | Requires / turns on automatically |
| ------ | ----------- | --------------------------------- |
| `enabled` | Master switch for the plugin (`Property<Boolean>`, default `true`). | Nothing; set `enabled.set(false)` to disable. |
| `mavenRepo(name) { ... }` | Declares a named Maven repository and configures a `MavenRepoSpec` (public API). | Nothing. |
| `mavenRepo(name, url, role, withPasswordCredentials = false)` | Convenience overload — creates `MavenRepoSpec` with `url`/`role`/`passwordCredentials` without exposing spec type. | Nothing. |
| `promoteToCentral()` / `promoteToPortal()` | Promotion-gated variants of `deployToCentral()` / `deployToPortal()` — active only on `-Ppromote` invocations. | Same side effects as the ungated variant (staging default + signing for Central). Setting both spellings for one destination fails fast. |
| `toMavenStaging(path = "stagingRepo")` | Creates `mavenStaging` file repo under `build/<path>` via `Property<String>` (`stagingPath`) + helper `mavenRepo`. | Nothing; setting `stagingPath` registers the repo and `cleanStagingRepo` per project. |
| `deployToMavenLocal()` | One-shot flag (`Property<Boolean> deployToMavenLocal`) — makes `publish` depend on `publishToMavenLocal`. | Nothing. |
| `deployToCentral()` | One-shot flag (`Property<Boolean> deployToCentral`) — stages + deploys releases to Maven Central via JReleaser. | Turns on `signingEnabled` and a default `stagingPath` (`build/stagingRepo`) automatically. Requires the `codemeta` extension and a non-snapshot version — otherwise central wiring is skipped with a log message. |
| `deployToSnapshots()` | One-shot flag (`Property<Boolean> deployToSnapshots`) — creates the `sonatypeSnapshots` repo (Central snapshots URL + password credentials) unless present; routing via semver or `-SNAPSHOT` suffix. | Nothing (pure repo shorthand). |
| `deployToPortal()` | One-shot flag (`Property<Boolean> deployToPortal`) — makes `publish` depend on `publishPlugins` (from `com.gradle.plugin-publish`) on releases in plugin projects; snapshots skip; non-plugin projects (and root projects without a `publish` task) skip silently (info-level note). | Requires `gradle.publish.key`/`gradle.publish.secret` credentials in plugin projects (missing ones fail `publish` with an actionable error). |
| `signingEnabled` | Enables PGP artifact signing (`Property<Boolean>`, default `false`). | Auto-enabled by `deployToCentral()` (and `promoteToCentral()`); set `signingEnabled.set(false)` afterwards to opt out. |
| `mavenRepos` | `NamedDomainObjectContainer<MavenRepoSpec>` of declared repositories (internal, on `DefaultEasyPublishExtension`). | — (populated via `mavenRepo(...)` / the `deployTo*` shorthands). |
| `stagingPath` | `Property<String>` holding staging template (creates `mavenStaging` per-project via `buildDirectory`). | Set via `toMavenStaging(...)`, or automatically by `deployToCentral()` / `promoteToCentral()`. |

### `MavenRepoSpec`

Public spec type (in `publish-plugin-api`) for a single repository.

| Property | Description |
| -------- | ----------- |
| `url` | The repository URL (`Property<String>`). |
| `role` | Which version kind the repo receives (`Property<RepoRole>` — `SNAPSHOT`/`RELEASE`/`NEUTRAL`, no default; unset fails fast). |
| `promotionOnly` | When `true`, the repo is skipped by plain `publish` and attached with `-Ppromote` (`Property<Boolean>`, default `false`). |
| `passwordCredentials` | When `true`, enables `PasswordCredentials` for the repository (`Property<Boolean>`). |

## Credentials

Credentials are opt-in per repository. When `passwordCredentials.set(true)` is used,
Gradle resolves the username/password for the repository from the project's
`-P<name>Username`/`-P<name>Password` properties (or the same keys in
`~/.gradle/gradle.properties`), where `<name>` is the repository name.

Example for a repository named `releases`:

```bash
./gradlew publish -PreleasesUsername=alice -PreleasesPassword=secret
```

> Note: `file://` repositories do not support credentials; use an `http(s)://` URL
> (or a real remote) when enabling `passwordCredentials`.

Portal credentials (`deployToPortal()` / `promoteToPortal()`) are separate: provide `gradle.publish.key` and
`gradle.publish.secret` via the `GRADLE_PUBLISH_KEY`/`GRADLE_PUBLISH_SECRET` environment
variables or the corresponding Gradle properties.

## Module overview

```text
EasyPublishPlugin (@ApplyToSubprojects)
├── MavenPublicationConfigurer (coordinates, POM, versionMapping)
├── CentralPublishingWiring -> CheckCentralPomsTask -> PomRequirementsChecker
│   └── CentralPublishingWiring -> StripSignatureChecksumsTask (after staging upload, before JReleaser deploy: staging dir -> cleaned dir -> Central)
└── mavenStaging repo + RepoRouting (release/snapshot filtering)

EasyJreleaserPlugin (root-only)
├── JreleaserConfigWiring -> GenerateJreleaserConfigTask -> JreleaserJson + JreleaserDeployers
├── PublishAggregationWiring -> root `publish` (see ensureRootPublishTask)
└── JreleaserDeployWiring -> publishToMavenCentral (JreleaserPublishTask)

Shared: PublishExtensions.publishExtension() + central.ensureRootPublishTask()
```

## How it works

 `EasyPublishPlugin` (in
`contributor-plugins/publish/publish-plugin/src/main/kotlin/com/mreil/easy/publish/EasyPublishPlugin.kt`) +
`DefaultEasyPublishExtension` (`@PublicType(EasyPublishExtension::class)`, discovered via `EasyPublishContributor`):

1. `EasyPublishContributor` contributes `DefaultEasyPublishExtension::class` via `pluginExtensions()`; `ExtensionRegistrar` resolves the public type via `@PublicType` and calls `createExtensionAs(publicType, implType)` so the extension is reachable as `easy.publish` (public interface) but instantiated as the implementation.
2. When applied to a project with the `java` plugin, applies `maven-publish`.
3. Creates the default `maven` publication from the `java` component — unless the
   `java-gradle-plugin` plugin is present (it manages its own publications and plugin
   markers).
4. Normalizes every publication (regular and plugin marker): fills in missing
   coordinates/version, populates the POM, and configures version mapping.
   `url`/`scm` come from Codemeta only — without it they stay unset (never a
   hardcoded placeholder) and `checkCentralPoms` reports them before Central upload.
5. If `stagingPath` is present (`toMavenStaging`), creates `mavenStaging` via helper `mavenRepo("mavenStaging", buildDirectory/dir(path))` per-project.
6. Resolves the snapshot flag via `EasySemver.of(target).orNull` (`semver4j`, strict parse, `isExtensionEnabled(EasySemverExtension::class)` guard) — when semver is disabled, a `-SNAPSHOT` version suffix decides. `RepoRouting.shouldPublishToRepo(spec, isSnapshot)` publishes per the spec's explicit `role` (`SNAPSHOT` repos for snapshots, `RELEASE` repos for releases, `NEUTRAL` always); an unset role fails fast.
7. Attaches filtered `mavenRepos` to `publishing.repositories` (`spec.configure(target, repo)`), enabling `PasswordCredentials` when requested.
8. If `toMavenLocal` is true, wires `publish -> publishToMavenLocal` eagerly via `tasks.named("publish").configure { dependsOn("publishToMavenLocal") }` (CC-safe, one-shot; no `afterEvaluate` needed since only final extension values are read).
