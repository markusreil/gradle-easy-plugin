# gradle-plugin-utils

Utility library for Gradle plugin development.

## Purpose

`gradle-plugin-utils` collects small, reusable helpers that are frequently needed when authoring Gradle plugins but are not specific to any single plugin. The goal is to reduce duplication across `easy-plugin-core`, contributor plugins, and future plugins.

Typical utilities include:

- **Task helpers** — creation/registration, cacheability, input/output wiring.
- **Property & provider helpers** — safe `Property<T>` / `Provider<T>` conventions, CC-compatible access.
- **Project / Settings helpers** — extenabssion registration, plugin application, `withType` helpers.
- **File & path helpers** — layout-aware file resolution, directory creation.
- **Testing helpers** — shared assertions or fixtures for `ProjectBuilder` / `GradleRunner` tests.

This module is a plain `java-library` (Kotlin) with `gradleApi()` as `compileOnly`, so it can be consumed by any plugin without pulling in extra runtime dependencies.

## Utilities

### `PropertyResolver` — unified env / system / Gradle property lookup

`src/main/kotlin/com/mreil/utils/PropertyResolver.kt:25`

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

Helpers `PropertyResolver.toEnvKey(name)` at `PropertyResolver.kt:88` and `PropertyResolver.toPropertyKey(name)` at `PropertyResolver.kt:98` expose the conversions.

#### `PropertyResolver.StringProvider`

`PropertyResolver.kt:68` — inner `Provider<String>` wrapping a delegate.

Delegates all `Provider` operations (`get()`, `isPresent`, `map`, `flatMap`, `orElse`, etc.) and adds:

- `base64Decode(): Provider<String>` at `PropertyResolver.kt:77` — lazy `Base64.getDecoder().decode` with UTF-8 via `Provider.map`; absent stays absent. Top-level `typealias StringProvider = PropertyResolver.StringProvider` at `PropertyResolver.kt:107` kept for backward compat.

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
  src/main/kotlin/com/mreil/utils/PropertyResolver.kt  # PropertyResolver + inner StringProvider
  src/test/kotlin/com/mreil/utils/PropertyResolverTest.kt
```

## Development

```bash
./gradlew :gradle-plugin-utils:check
./gradlew :gradle-plugin-utils:test
./gradlew spotlessCheck
```
