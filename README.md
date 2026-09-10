# gradle-easy-plugin

Umbrella Gradle plugin that provides a single `easy { }` DSL and discovers feature plugins via SPI. Built with Kotlin, `java-gradle-plugin`, Gradle 9.4.1.

> **For contributors & plugin development, see [DEVELOPMENT.md](DEVELOPMENT.md).**

## Requirements

* **Java 21+** — the plugin is compiled/published for JVM 21 (`org.gradle.jvm.version=21`). Consumers must run Gradle with Java 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk`).

## Installation

`settings.gradle.kts`:

```kotlin
plugins {
    id("com.mreil.easy.settings")
}
```

`build.gradle.kts` (root or single-project):

```kotlin
plugins {
    `java-library`
    id("com.mreil.easy.project")
}

group = "com.example"
version = "1.2.3"
description = "Example library"
```

Plugin IDs are the single source in `gradle.properties` (`plugin.project`/`plugin.settings`).

## Configuration

All features are configured via `easy { }`. `publish` is **enabled by default** (disable it with `enabled.set(false)`); `semver`/`codemeta` are enabled when their block is present.

```kotlin
easy {
    // Publish to Maven repositories (enabled by default — declare repos to publish)
    publish {
        // publish to build/stagingRepo (creates mavenStaging repository)
        toMavenStaging("stagingRepo")
        // also publish to local maven on `publish`
        toMavenLocal()

        // named repository - Action<MavenRepoSpec> style
        mavenRepo("myReleases") {
            url.set("https://repo.example.com/releases")
            passwordCredentials.set(false)
        }
        // convenience overload with credentials from -PmySnapshotsUsername/-PmySnapshotsPassword
        mavenRepo(
            name = "mySnapshots",
            url = "https://repo.example.com/snapshots",
            withPasswordCredentials = true,
        )
    }

    // Semantic versioning - typed access to project.version
    semver {
        // marker extension; no config. Use in build logic:
        // val semver = EasySemver.of(project).get() // major/minor/patch/isStable/version
        // enabled.set(false)
    }

    // CodeMeta generation
    codemeta {
        filename.set("codemeta.json") // convention: "codemeta.json"
        // enabled.set(false)
    }

    // jvm-defaults has no DSL - auto-applies when java plugin is present:
    // JavaPluginExtension.withSourcesJar() + withJavadocJar()
}
```

* With `semver` enabled, `publish` automatically skips `*release*` repos for snapshots and `*snapshot*` repos for releases.
* With `codemeta` enabled, pre-create `codemeta.json` or let `generateCodemeta` create an initial file (first build fails by design to let you edit it).

## Features

| Feature | Extension (`easy.<name>`) | Description |
|---|---|---|
| `publish` | `easy.publish` (enabled by default — set `enabled.set(false)` to disable) | Wraps `maven-publish`. Creates default `maven` publication from `java` component (unless `java-gradle-plugin` present), wires `mavenRepo {}` declarations and snapshot/release filtering. |
| `jvm-defaults` | — (no DSL) | When `java` plugin is present, configures `withSourcesJar()` / `withJavadocJar()`. Always active. |
| `semver` | `easy.semver` | Validates `group`/`version` are SEMVER and exposes `EasySemver.of(project)` for typed access. |
| `codemeta` | `easy.codemeta` (`filename` default `codemeta.json`) | Manages `codemeta.json` via `generateCodemeta` task (fails first build to let you edit if file is missing). |

## Multi-project

Configure `easy { }` in the root; subprojects inherit via `ExtensionCopier` and `@ApplyToSubprojects`. `ProjectPlugin` injects `easy` copies to subprojects automatically.
