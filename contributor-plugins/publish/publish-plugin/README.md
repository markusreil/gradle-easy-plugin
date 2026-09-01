# publish-plugin (com.mreil.easy.publish)

Easy plugin that wraps Gradle's [`maven-publish`](https://docs.gradle.org/current/userguide/publishing_maven.html)
to publish a project's artifacts to Maven repositories with minimal configuration.

It is a *contributor plugin*: it is discovered via the `EasyPluginContributor`
ServiceLoader SPI (see `EasyPublishContributor`) and applied through the shared easy
plugin infrastructure. It only activates on projects that enable the `easy.publish`
extension.

## Features

* **Zero-config publications** – when applied to a `java` project, applies
  `maven-publish` and creates a default `maven` publication from the `java`
  component.
* **Plugin markers supported** – for projects using `java-gradle-plugin`, the
  plugin recognizes the existing publications (including `*PluginMarkerMaven`) and
  does not create a conflicting default publication.
* **Consistent coordinates** – `groupId`/`artifactId`/`version` are derived from the
  project; publishing fails fast with a clear error if `group`/`version` are unset.
* **POM metadata** – every publication gets a POM with name, description, URL,
  MIT license and SCM metadata plus dependency version mapping.
* **Declarative repositories** – the `easy.publish.mavenRepo(...)` DSL attaches
  named Maven repositories to the `publishing` extension lazily.
* **Password credentials** – opt-in per repository via `passwordCredentials`.

## Usage

Apply the easy project plugin, then enable the `publish` extension:

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
        mavenRepo("local") {
            url.set("build/repo")
        }
    }
}
```

## Extension reference

### `easy.publish`

The `EasyPublishExtension` backs the `easy { publish { ... } }` block and gates the
plugin's activation.

| Member | Description |
| ------ | ----------- |
| `mavenRepos` | `NamedDomainObjectContainer<MavenRepoSpec>` of declared repositories. |
| `mavenRepo(name) { ... }` | Declares a named Maven repository and configures a `MavenRepoSpec`. |

### `MavenRepoSpec`

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

## How it works

`EasyPublishPlugin` (in
`contributor-plugins/publish-plugin/src/main/kotlin/com/mreil/easy/publish/EasyPublishPlugin.kt`):

1. When applied to a project with the `java` plugin, applies `maven-publish`.
2. Creates the default `maven` publication from the `java` component — unless the
   `java-gradle-plugin` plugin is present (it manages its own publications and plugin
   markers).
3. Normalizes every publication (regular and plugin marker): fills in missing
   coordinates/version, populates the POM, and configures version mapping.
4. Attaches every `mavenRepo` from the extension to `publishing.repositories`,
   enabling `PasswordCredentials` when requested.
