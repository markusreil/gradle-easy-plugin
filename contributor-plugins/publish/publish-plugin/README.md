# publish-plugin (com.mreil.easy.publish)

Easy plugin that wraps Gradle's [`maven-publish`](https://docs.gradle.org/current/userguide/publishing_maven.html)
to publish a project's artifacts to Maven repositories with minimal configuration.

It is a *contributor plugin*: it is discovered via the `EasyPluginContributor`
ServiceLoader SPI (see `EasyPublishContributor`) and applied through the shared easy
plugin infrastructure. It only activates when `easy.publish` is explicitly enabled (`enabled` defaults to `false` in `DefaultEasyPublishExtension`). The public extension API (`EasyPublishExtension` + `MavenRepoSpec`) lives in `publish-plugin-api`; the implementation
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
  named Maven repositories to the `publishing` extension lazily (both `Action<MavenRepoSpec>` and convenience `mavenRepo(name, url, withPasswordCredentials)` overloads).
* **Password credentials** – opt-in per repository via `passwordCredentials` (or `withPasswordCredentials = true`).
* **Staging repository** – `toMavenStaging(path)` creates a `mavenStaging` file repo under `build/<path>` (default `build/stagingRepo`).
* **Maven local wiring** – `toMavenLocal()` makes `publish` depend on `publishToMavenLocal`.
* **Semver-aware routing** – when `easy.semver` is enabled (`easy { semver {} }`), the version is parsed via `semver4j` (`EasySemver.of(project)`). Snapshots (`!isStable`) skip `*release*` repos, releases skip `*snapshot*` repos; neutral names always publish. Without semver, all repos are used.

## Usage

Apply the easy project plugin, then enable the `publish` extension (disabled by default):

```kotlin
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)   // if using Kotlin
    id("com.mreil.easy.project")
}

group = "com.example"
version = "1.0.0"

easy {
    publish {
        enabled.set(true) // required — publish is disabled by default
        mavenRepo("releases") {
            url.set("https://repo.example.com/releases")
            passwordCredentials.set(true)   // optional
        }
    }
}
```

Then publish with Gradle's standard tasks:

```bash
./gradlew publish                     # publish to all declared repositories
./gradlew publishToMavenLocal         # publish to the local Maven cache
```

The plugin also works when you publish locally to a file repository:

```kotlin
easy {
    publish {
        enabled.set(true)
        mavenRepo("local", "build/repo")
        // or with credentials: mavenRepo("releases", "https://repo.example.com/releases", true)
    }
}
```

Staging and mavenLocal helpers:

```kotlin
easy {
    semver {} // enable semver for release/snapshot routing (optional)
    publish {
        enabled.set(true)             // required — disabled by default
        toMavenStaging()              // -> file: build/stagingRepo as `mavenStaging`
        toMavenStaging("custom")      // -> file: build/custom
        toMavenLocal()                // publish -> publishToMavenLocal

        mavenRepo("myRelease", "https://repo.example.com/releases")
        mavenRepo("mySnapshot", "https://repo.example.com/snapshots")
        mavenRepo("myNeutral", "https://repo.example.com/central") // always published
        // version 1.0.0 -> publishes to myRelease + myNeutral
        // version 1.0.0-SNAPSHOT -> publishes to mySnapshot + myNeutral
    }
}
```

## Extension reference

### `easy.publish`

The public `EasyPublishExtension` interface (in `publish-plugin-api`) backs the `easy { publish { ... } }` block and gates the
plugin's activation (`enabled` defaults to `false` — `enabled.set(true)` is required to activate publishing). The implementation is `DefaultEasyPublishExtension` (in `publish-plugin`), annotated with
`@PublicType(EasyPublishExtension::class)` so `ExtensionRegistrar.createExtensionAs` registers the extension under the
interface's `Named` companion (`"publish"`) while instantiating the implementation. `mavenRepos` is intentionally internal to the
implementation and not part of the public API – consumers use `mavenRepo(name) { ... }`.

| Member | Description |
| ------ | ----------- |
| `mavenRepo(name) { ... }` | Declares a named Maven repository and configures a `MavenRepoSpec` (public API). |
| `mavenRepo(name, url, withPasswordCredentials = false)` | Convenience overload — creates `MavenRepoSpec` with `url`/`passwordCredentials` without exposing spec type. |
| `toMavenStaging(path = "stagingRepo")` | Creates `mavenStaging` file repo under `build/<path>` via `Property<MavenRepoSpec>` (`stagingPath`) + helper `mavenRepo`. |
| `toMavenLocal()` | One-shot flag (`Property<Boolean> toMavenLocal`) — makes `publish` depend on `publishToMavenLocal`. |
| `mavenRepos` | `NamedDomainObjectContainer<MavenRepoSpec>` of declared repositories (internal, on `DefaultEasyPublishExtension`). |
| `stagingPath` | `Property<MavenRepoSpec>` holding staging template (creates `mavenStaging` per-project via `buildDirectory`). |

### `MavenRepoSpec`

Public spec type (in `publish-plugin-api`) for a single repository.

| Property | Description |
| -------- | ----------- |
| `url` | The repository URL (`Property<String>`). |
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

## Module overview

```text
EasyPublishPlugin (@ApplyToSubprojects)
├── MavenPublicationConfigurer (coordinates, POM, versionMapping)
├── PomCheckWiring -> CheckCentralPomsTask -> PomRequirementsChecker
└── mavenStaging repo + RepoRouting (release/snapshot filtering)

EasyJreleaserPlugin (root-only)
├── JreleaserConfigWiring -> GenerateJreleaserConfigTask -> JreleaserYaml + JreleaserDeployers
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
6. Resolves semver lazily via `EasySemver.of(target).orNull` (`semver4j`, strict parse, `isExtensionEnabled(EasySemverExtension::class)` guard) — `null` → no filtering. Otherwise `isSnapshot = RepoRouting.isSnapshot(semver)`; `RepoRouting.shouldPublishToRepo(name, isSnapshot)` skips `*release*` repos for snapshots and `*snapshot*` repos for releases (case-insensitive); neutral names always added.
7. Attaches filtered `mavenRepos` to `publishing.repositories` (`spec.configure(target, repo)`), enabling `PasswordCredentials` when requested.
8. If `toMavenLocal` is true, wires `publish -> publishToMavenLocal` eagerly via `tasks.named("publish").configure { dependsOn("publishToMavenLocal") }` (CC-safe, one-shot; no `afterEvaluate` needed since only final extension values are read).
