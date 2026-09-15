# gradle-plugin-utils

Utility library for Gradle plugin development.

## Purpose

`gradle-plugin-utils` collects small, reusable helpers that are frequently needed when authoring Gradle plugins but are not specific to any single plugin. The goal is to reduce duplication across `easy-plugin-core`, contributor plugins, and future plugins.

Typical utilities include:

- **Task helpers** — creation/registration, cacheability, input/output wiring.
- **Property & provider helpers** — safe `Property<T>` / `Provider<T>` conventions, CC-compatible access.
- **Project / Settings helpers** — extension registration, plugin application, `withType` helpers.
- **File & path helpers** — layout-aware file resolution, directory creation.
- **Testing helpers** — shared assertions or fixtures for `ProjectBuilder` / `GradleRunner` tests.

This module is a plain `java-library` (Kotlin) with `gradleApi()` as `compileOnly`, so it can be consumed by any plugin without pulling in extra runtime dependencies.

## Utilities

### `PropertyResolver` — unified env / system / Gradle property lookup

`src/main/kotlin/com/mreil/utils/PropertyResolver.kt`

Resolves a value from environment, system property or Gradle property through a single name.
Takes a `ProviderFactory` (e.g. `project.providers`) and returns a CC-compatible lazy `Provider`.

Resolution order: `environmentVariable` → `systemProperty` → `gradleProperty`. Name conversions are applied
automatically so any convention matches:

- `MY_PROPERTY` ↔ `my.property` ↔ `myProperty` ↔ `my-property`

```kotlin
val resolver = PropertyResolver(providers)

// any convention works — checks MY_PROPERTY and my.property
val token: PropertyResolver.StringProvider = resolver.get("myProperty")
val tokenValue: String = token.get()
val isPresent: Boolean = token.isPresent

// CC-compatible transformations on the provider
val decoded: Provider<String> = resolver.get("MY_SECRET").base64Decode()
```

Helpers `PropertyResolver.toEnvKey(name)` and `PropertyResolver.toPropertyKey(name)` expose the conversions.

#### `PropertyResolver.StringProvider`

Inner `Provider<String>` wrapping a delegate.

Delegates all `Provider` operations (`get()`, `isPresent`, `map`, `flatMap`, `orElse`, etc.) and adds:

- `base64Decode(): Provider<String>` — lazy `Base64.getDecoder().decode` with UTF-8 via `Provider.map`; absent stays absent. Top-level `typealias StringProvider = PropertyResolver.StringProvider` kept for backward compat.

### `ProviderExtensions` — fail-fast required values

`src/main/kotlin/com/mreil/utils/ProviderExtensions.kt`

- `Provider<T>.required(message)` — returns the provider's value, or throws `GradleException` with `message` when absent. Reads lazily at call time (task action or finalized configuration), so it stays configuration-cache compatible; prefer it over a throwing provider convention.

```kotlin
val username = mavenCentralUsername.required("Maven Central username is required")
```

### `GradleProperties` — layout-preserving `gradle.properties` reader/writer

`src/main/kotlin/com/mreil/utils/GradleProperties.kt`

Reads and writes `gradle.properties` files while preserving their layout (comments, blank lines,
escaping, key order). Uses Apache Commons Configuration under the hood so untouched keys are written
back byte-for-byte.

- `locateDeclaringFile(dirsInPriorityOrder, key)` — returns the first `gradle.properties` declaring `key`, or null.
- `readRawValue(file, key)` — returns the raw value of `key` in `file`, or null.
- `writeValue(file, key, value)` — writes `value` for `key` into `file`, preserving unrelated lines.

### `ProjectCoordinates` — project group/version inspection

`src/main/kotlin/com/mreil/utils/ProjectCoordinates.kt`

Extension functions for checking whether a project has usable coordinates:

- `String?.isSpecified()` — returns true when the value is set (neither null, empty nor Gradle's `"unspecified"` default).
- `Project.hasGroup()` — returns true when the project has a usable `group`.
- `Project.hasVersion()` — returns true when the project has a usable `version`.

### `ProjectExtensions` — project root and directory helpers

`src/main/kotlin/com/mreil/utils/ProjectExtensions.kt`

- `Project.isRoot()` — returns true when this project is the root project of the build.
- `Project.propertiesDirs()` — returns `[Project.projectDir]` followed by each ancestor directory up to and including the root project directory. Useful for walking `gradle.properties` files from a project toward the build root, e.g. with `GradleProperties.locateDeclaringFile`.

### `VersionCatalogVersions` — catalog version lookup with fallback

`src/main/kotlin/com/mreil/utils/VersionCatalogVersions.kt`

- `Project.catalogVersionOrDefault(alias, default, catalogName = "libs")` — returns the version for `alias` in the named version catalog, or `default` when absent or blank. Never throws.

### `SpdxLicense` — SPDX license id normalization

`src/main/kotlin/com/mreil/utils/SpdxLicense.kt`

Normalizes SPDX license ids to `https://spdx.org/licenses/<id>` URLs and back:

- `SpdxLicense.toUrl(raw)` — accepts either the SPDX id shorthand (`MIT`) or a full URL; anything starting with `http` is treated as already-complete.
- `SpdxLicense.toSpdxId(raw)` — extracts the SPDX id from a URL (or returns the shorthand as-is).

## Usage

Add as a dependency in a plugin module:

```kotlin
dependencies {
    implementation(project(":gradle-plugin-utils"))
}
```

## Structure

```
gradle-plugin-utils/
  build.gradle.kts
  README.md
  src/main/kotlin/com/mreil/utils/PropertyResolver.kt        # PropertyResolver + inner StringProvider
  src/main/kotlin/com/mreil/utils/ProviderExtensions.kt      # Provider<T>.required(message)
  src/main/kotlin/com/mreil/utils/GradleProperties.kt         # GradleProperties (layout-preserving read/write)
  src/main/kotlin/com/mreil/utils/ProjectCoordinates.kt       # isSpecified, hasGroup, hasVersion
  src/main/kotlin/com/mreil/utils/ProjectExtensions.kt        # Project.isRoot(), Project.propertiesDirs()
  src/main/kotlin/com/mreil/utils/VersionCatalogVersions.kt   # Project.catalogVersionOrDefault(...)
  src/main/kotlin/com/mreil/utils/SpdxLicense.kt              # SpdxLicense (toUrl, toSpdxId)
  src/test/kotlin/com/mreil/utils/PropertyResolverTest.kt
  src/test/kotlin/com/mreil/utils/ProviderExtensionsTest.kt
  src/test/kotlin/com/mreil/utils/GradlePropertiesTest.kt
  src/test/kotlin/com/mreil/utils/ProjectCoordinatesTest.kt
  src/test/kotlin/com/mreil/utils/ProjectExtensionsTest.kt
  src/test/kotlin/com/mreil/utils/VersionCatalogVersionsTest.kt
  src/test/kotlin/com/mreil/utils/SpdxLicenseTest.kt
```

## Development

```bash
./gradlew :gradle-plugin-utils:check
./gradlew :gradle-plugin-utils:test
./gradlew spotlessCheck
```
