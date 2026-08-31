# test-fixtures

Shared test fixtures and dummy plugins used for functional testing across the project without polluting the production runtime classpath of `easy-plugin-core` or `easy-plugin`.

## How Classes End Up in the Test Classpath

Test fixtures are wired into Gradle TestKit functional tests through two distinct mechanisms in `easy-plugin/build.gradle.kts`:

1. **TestKit Plugin Classpath (`pluginUnderTestMetadata`)**
   - A dedicated resolvable configuration `fixtures` is declared in `easy-plugin/build.gradle.kts`:
     ```kotlin
     val fixtures by configurations.creating {
         isCanBeConsumed = false
         isCanBeResolved = true
     }
     dependencies {
         fixtures(project(":test-fixtures"))
     }
     ```
   - The `pluginUnderTestMetadata` task adds `fixtures` to the generated plugin classpath metadata:
     ```kotlin
     tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
         pluginClasspath.from(fixtures)
     }
     ```
   - When tests execute via Gradle TestKit (`GradleRunner.withPluginClasspath()`), Gradle includes `:test-fixtures` and its `META-INF/services` declarations on the plugin classpath of the tested build.

2. **Test Suite Compilation & Runtime Classpath (`functionalTest`)**
   - The `functionalTest` test suite explicitly depends on `:test-fixtures`:
     ```kotlin
     val functionalTest by registering(JvmTestSuite::class) {
         dependencies {
             implementation(project(":test-fixtures"))
         }
     }
     ```
   - This allows test source files to directly import and reference fixture classes for type-safe assertions and configuration scripts.

## Fixture Classes & Where They Are Used

- **`DummyProjectPlugin`** (`com.mreil.easy.fixtures.DummyProjectPlugin`)
  - A sample `Plugin<Project>` that registers a `"dummyTask"` on the target project.
  - **Used in:** `PluginRegistryFuncTest` (`easy-plugin/src/functionalTest/...`) to test dynamic plugin registration in `PluginRegistryService` and application to Gradle projects.

- **`DummyExtension`** (`com.mreil.easy.fixtures.DummyExtension`)
  - A test `EasyPluginExtension` with a `message: Property<String>` property, `CanBeEnabled` capability, and a companion object named `"dummy"`.
  - **Used in:** Verifying that contributor sub-extensions are discovered via SPI and attached under `easy.extensions`.

- **`DummyContributor`** (`com.mreil.easy.fixtures.DummyContributor`)
  - An `EasyPluginContributor` SPI implementation that registers `DummyExtension::class` in `pluginExtensions()`.
  - **Service Registration:** Declared in `src/main/resources/META-INF/services/com.mreil.easy.EasyPluginContributor`.
  - **Used in:** Automatically discovered by `PluginRegistryService` via `ServiceLoader` during TestKit functional test runs to test extension contribution without manual registration.
