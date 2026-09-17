# jvm-defaults-plugin (com.mreil.easy.jvm)

Easy plugin that applies sensible JVM defaults to `java` projects: sources/javadoc jars, a
pinned Java toolchain, auto-configured unit and functional test suites, and JaCoCo coverage
wiring.

It is a *contributor plugin*: it is discovered via the `EasyPluginContributor` ServiceLoader
SPI (`EasyJvmDefaultsContributor`) and applied through the shared easy plugin infrastructure.
It is applied to the root project **and every subproject** (`@ApplyToSubprojects`) and activates
by default (`enabled` defaults to `true` in `DefaultEasyJvmDefaultsExtension`); disable it with
`easy { jvmDefaults { enabled.set(false) } }`. The public extension API
(`EasyJvmDefaultsExtension`) lives in `jvm-defaults-plugin-api`; the implementation
(`DefaultEasyJvmDefaultsExtension`, annotated `@PublicType`) and wiring
(`EasyJvmDefaultsPlugin`, `TestSuiteWiring`) live here.

All behaviour is registered only when the `java` plugin is present, from the plugin's
`afterEnabled` hook (which the shared lifecycle invokes after evaluation, so `easy { }`
configuration is respected).

## Behaviour

### Sources and Javadoc jars

* Calls `withSourcesJar()` and `withJavadocJar()` on the `java` extension unless a `sourcesJar`
  / `javadocJar` task already exists.
* When the task already exists (configured manually), it is left untouched and a migration hint
  is logged instead (see [Migration hints](#migration-hints)).

### Java toolchain pinning

* If the `java.toolchainVersion` property is set, the `java` extension's toolchain language
  version is pinned to that value.
* If the property is absent, nothing is changed and an `INFO` log explains that the default
  toolchain is left in place.

The value is resolved from the environment, a system property or a Gradle property (in that
order), using any common naming convention. Equivalent forms:

| Source | Key |
| ------ | --- |
| Environment variable | `JAVA_TOOLCHAIN_VERSION` |
| System property | `java.toolchain.version` / `java.toolchainVersion` |
| Gradle property | `java.toolchain.version` / `java.toolchainVersion` |

```bash
./gradlew build -Pjava.toolchainVersion=17
JAVA_TOOLCHAIN_VERSION=17 ./gradlew build
```

### Test-suite auto-configuration

Gated by `configureTestSuites` (default `true`). The framework-provided `test` suite is always
configured in place; in addition, for every discovered `src/<name>/{java,kotlin}` directory,
`TestSuiteWiring` classifies the suite by directory name:

| Directory `src/<name>/…` | Type | Plugin behaviour |
| ------------------------ | ---- | ---------------- |
| `test` | `UNIT` | Configured in place (framework + catalog test dependencies, see below) — but never registered, ordered, wired into `check` or exposed as a plugin-under-test source set, since Gradle already provides all of that. |
| `*Test` (`[A-Za-z][A-Za-z0-9]*Test`, e.g. `functionalTest`, `integrationTest`, `smokeTest`) | `FUNCTIONAL` | Registered if missing and fully configured (see below). |
| anything else (`testFixtures`, `latest`, …) | ignored | Not a test suite; skipped. |

A directory is only considered when it contains at least one existing `java` or `kotlin`
subdirectory. The built-in `test` suite is also used as the ordering anchor.

For the framework-provided `test` suite the plugin:

* selects `JUnit Jupiter` with the consuming build's catalog-pinned version (same as for
  functional suites below);
* adds the catalog-declared unit-test dependencies — AssertJ (`assertj-core`, falling back to
  `assertj`), JUnit Pioneer (`junit-pioneer`), JUnit Jupiter params (`junit-jupiter-params`) and
  Mockito (`mockito-core`) — using the catalog's coordinates and version;
* registers the root-level `testAggregateTestReport` / `testCodeCoverageReport` aggregate reports
  for it (same as for functional suites, gated by `aggregateReports`);
* nothing else: no suite registration, ordering, `check` wiring or `testSourceSets` entry. In
  plugin projects Gradle's `java-gradle-plugin` already puts `gradleTestKit()` and the
  plugin-under-test metadata on the built-in `test` suite's classpath, so the plugin does not
  duplicate that.

For each functional suite the plugin:

* registers the suite via `testing.suites` (`maybeCreate(name, JvmTestSuite)`, idempotent) when
  the build did not declare it itself;
* adds the project's own `main` source set output to the suite's `implementation`, so functional
  tests can exercise the code under test — the built-in `test` suite gets this from the `java`
  plugin, a custom test suite does not (equivalent to declaring `implementation(project())`);
* selects `JUnit Jupiter` via `useJUnitJupiter()`, using the version the consuming build pins for
  `junit-jupiter`/`junit-jupiter-api` in its `libs` catalog when it declares one; otherwise
  Gradle's default JUnit Jupiter version is used;
* adds well-known test dependencies that the consuming build declares in its `libs` version
  catalog — currently AssertJ (`assertj-core`, falling back to `assertj`), JUnit Pioneer
  (`junit-pioneer`) and JUnit Jupiter params (`junit-jupiter-params`) — using the catalog's
  coordinates and version; aliases absent from the catalog (and builds without one) are skipped.
  Mockito is deliberately not added here: functional tests exercise real builds rather than
  mocking collaborators.
* makes the suite's test task `shouldRunAfter` the built-in `test` suite;
* logs at info level when it auto-configures a suite (the suite's test-task description stays at
  the standard Gradle default);
* wires the suite into `check` (`check.dependsOn(suite's test task)`);
* when `java-gradle-plugin` is applied, reproduces Gradle's plugin-classpath wiring for the suite
  (Gradle itself skips suites that are registered after evaluation): `gradleTestKit()` and
  `gradleApi()` on `implementation`, the `pluginUnderTestMetadata` output on `runtimeOnly` — so
  `GradleRunner.withPluginClasspath()` sees the metadata — and the suite's source set in
  `gradlePlugin.testSourceSets`;
* when `aggregateReports` is enabled (default), registers a root-level `AggregateTestReport`
  named `<suite>AggregateTestReport` (e.g. `functionalTestAggregateTestReport`, mirroring Gradle's
  automatic naming) targeting the suite's results, declares this project in the root's
  `testReportAggregation` configuration, and wires the report into the root `check` task — the
  same setup the root build uses for its own `testAggregateTestReport`. `ReportAggregationWiring`
  applies `test-report-aggregation` to the root, so no consumer setup is needed.

Registration uses the **live** `testing.suites` container: configuration is applied to every
matching suite whenever it appears, so a suite the build declares itself is configured the same
way the plugin would configure a newly created one (including `useJUnitJupiter()`), and
creation never collides with a pre-existing suite.

What the plugin does **not** add (consumer responsibility):

* assertion/helper libraries other than the catalog-declared ones (e.g. an AssertJ that the
  consuming build does not pin in its `libs` version catalog) — catalog-declared AssertJ, JUnit
  Pioneer and JUnit Jupiter params are added to functional suites, and Mockito additionally to the
  built-in `test` suite (see above); everything else is left to the build;
* `implementation(project(...))` for *other* projects and test fixtures shared across projects —
  the project's own `main` output is added to functional suites (see above);
* framework overrides — only JUnit Jupiter is selected;
* test-task customization (e.g. `maxParallelForks`, `failFast`, `jvmArgs`, test filtering) — the
  consuming build configures the suite's test task directly, e.g.
  `tasks.named<Test>("functionalTest") { maxParallelForks = ... }`;
* repositories — the consuming build must declare e.g. `mavenCentral()` so that
  `useJUnitJupiter()`'s `org.junit.jupiter:junit-jupiter` dependency can resolve.

```kotlin
plugins {
    `java-library`
    id("com.mreil.easy.project")
}

// src/functionalTest/kotlin/FooFuncTest.kt is picked up automatically and wired into `check`.
easy {
    jvmDefaults {
        // Optional: turn the test-suite auto-configuration off.
        configureTestSuites.set(false)
    }
}
```

### Code coverage

Gated by `jacocoEnabled` (default `true`); root-level aggregation additionally requires
`aggregateReports`. When enabled, each project with the `java` plugin gets
the `jacoco` plugin applied, which instruments every test task; the project's `jacocoTestReport`
then also consumes each auto-configured functional suite's execution data and is wired into
`check`:

* all test tasks are instrumented once `jacoco` is applied, but `jacocoTestReport` only consumes
  the built-in `test` suite's execution data by default, so every functional suite's execution data
  is added explicitly (via the task's `JacocoTaskExtension.destinationFile`) together with a task
  dependency on it;
* on the build's **root** project the plugin applies `jacoco-report-aggregation` (when coverage is
  enabled there) and registers a root-level `JacocoCoverageReport` named `<suite>CodeCoverageReport`
  for the built-in `test` suite and every auto-configured functional suite (e.g.
  `testCodeCoverageReport`, `functionalTestCodeCoverageReport`, matching the
  `<suite>CodeCoverageReport` convention of the root build's own coverage report), declares the
  project in the root's `jacocoAggregation` configuration, and wires the report into the root
  `check` task. Coverage aggregation is per test suite, so functional suites get their own root
  report rather than being merged into the `test` suite's report;
* coverage is all-or-nothing per project: with `jacocoEnabled.set(false)` nothing is applied (no
  `jacoco`/`jacoco-report-aggregation`); disabling it on the root project disables root-level
  coverage aggregation for the whole build.

The root project needs a repository (e.g. `mavenCentral()`) so the JaCoCo Ant library
(`org.jacoco:org.jacoco.ant`) used by the aggregation report can resolve.

```kotlin
plugins {
    `java-library`
    id("com.mreil.easy.project")
}

easy {
    jvmDefaults {
        // Optional: skip the JaCoCo application and coverage aggregation.
        jacocoEnabled.set(false)
    }
}
```

### Migration hints

When a manually configured `sourcesJar`/`javadocJar` is detected, the plugin logs a lifecycle
hint rather than reconfiguring it. Hints can be silenced with
`easy.migrationHintsEnabled=false` (environment variable, system or Gradle property).

## Extension reference

### `easy.jvmDefaults`

The public `EasyJvmDefaultsExtension` interface (in `jvm-defaults-plugin-api`) backs the
`easy { jvmDefaults { … } }` block and gates the plugin's activation. The implementation is
`DefaultEasyJvmDefaultsExtension` (in `jvm-defaults-plugin`), annotated with
`@PublicType(EasyJvmDefaultsExtension::class)` so `ExtensionRegistrar.createExtensionAs`
registers the extension under the interface's `Named` companion (`"jvmDefaults"`) while
instantiating the implementation.

| Member | Description | Default |
| ------ | ----------- | ------- |
| `enabled` | Master switch for the plugin (`Property<Boolean>`). | `true` |
| `configureTestSuites` | Whether test suites are auto-configured (the framework-provided `test` suite and discovered `*Test` suites) (`Property<Boolean>`). | `true` |
| `aggregateReports` | Whether root-level report aggregation is configured automatically: applies `test-report-aggregation` (and, with `jacocoEnabled`, `jacoco-report-aggregation`) to the root and registers `<suite>AggregateTestReport`/`<suite>CodeCoverageReport` for the built-in `test` suite and every auto-configured `*Test` suite (`Property<Boolean>`). | `true` |
| `jacocoEnabled` | Whether JaCoCo is applied and functional suites are wired into the coverage report (and, on the root project, whether coverage is aggregated) (`Property<Boolean>`). | `true` |

## Structure

* `jvm-defaults-plugin-api` — public `EasyJvmDefaultsExtension` interface.
* `jvm-defaults-plugin` — `DefaultEasyJvmDefaultsExtension` (`@PublicType`),
  `EasyJvmDefaultsPlugin` (`@EnabledBy`, `@ApplyToSubprojects`), `ToolchainWiring`
  (toolchain pinning), `TestSuiteWiring` (test-suite auto-configuration), `JacocoWiring`
  (JaCoCo application + coverage aggregation),
  `TestSourceDiscovery` (`findTestSuites()` + `TestSuiteType`/`DiscoveredTestSuite`),
  `EasyJvmDefaultsContributor` + `META-INF/services`.
* `jvm-defaults-test-plugin` — harness `com.mreil.easy.test.jvm`
  (`JvmDefaultsTestHarnessPlugin` applying `ProjectPlugin`) + functional tests.

## How it works

`EasyJvmDefaultsPlugin` (`@EnabledBy(EasyJvmDefaultsExtension::class)`,
`@ApplyToSubprojects`) + `DefaultEasyJvmDefaultsExtension`
(`@PublicType(EasyJvmDefaultsExtension::class)`, discovered via `EasyJvmDefaultsContributor`):

1. `EasyJvmDefaultsContributor` contributes `DefaultEasyJvmDefaultsExtension::class` via
   `pluginExtensions()` and `EasyJvmDefaultsPlugin::class` via `projectPlugins()`;
   `ExtensionRegistrar` resolves the public type via `@PublicType` so the extension is reachable
   as `easy.jvmDefaults`.
2. `AbstractEasyProjectPlugin.apply` runs `init()` eagerly and defers `afterEnabled()` to
   `afterEvaluate`, where `enabled` reflects user configuration.
3. `afterEnabled` calls `ReportAggregationWiring.configureRootAggregation(target)` (root only,
   gated by `aggregateReports`: applies `test-report-aggregation`, plus
   `jacoco-report-aggregation` when coverage is enabled), waits for the `java` plugin and then, in
   order: ensures the sources/javadoc jars, calls `ToolchainWiring.configure(target)` (toolchain
   pinning), `TestSuiteWiring.configure(target)`, and `JacocoWiring.configure(target)`.
4. `TestSuiteWiring` checks `configureTestSuites`, configures the framework-provided `test` suite
   in place (framework + catalog test dependencies), discovers suites via
   `Project.findTestSuites()` (classification in `TestSourceDiscovery`), and registers/configures
   the functional ones through the live `testing.suites` container.
5. `JacocoWiring` checks `jacocoEnabled`, applies `jacoco`, extends `jacocoTestReport` with every
   auto-configured functional suite's execution data, and — with `aggregateReports` — registers a
   root-level `<suite>CodeCoverageReport` for the built-in `test` suite and every functional suite.

## Tests and verification

```bash
./gradlew :contributor-plugins:jvm-defaults:jvm-defaults-plugin:check        # unit tests + detekt + jacoco
./gradlew :contributor-plugins:jvm-defaults:jvm-defaults-test-plugin:check   # harness functional tests
./gradlew spotlessCheck
```

Unit tests (`EasyJvmDefaultsPluginTest`) cover jar/toolchain behaviour, the
`configureTestSuites` default and opt-out, suite classification, functional-suite registration,
the built-in `test` suite being configured with framework and catalog dependencies but no
functional-only wiring, the `java-gradle-plugin` classpath wiring (`gradleTestKit()`,
`gradleApi()`, plugin-under-test metadata, `testSourceSets`) being mirrored on auto-configured
functional suites, the project's `main` output being added to auto-configured functional suites,
`check` wiring, root-level report aggregation, the JaCoCo application and
wiring (functional execution data on `jacocoTestReport`, the root `<suite>CodeCoverageReport` +
`jacocoAggregation` registration, and the `jacocoEnabled` opt-out), the absent-catalog case (no
extra test dependencies are added to either suite), and — via Mockito-stubbed
`VersionCatalog`/`JvmTestSuite` (Mockito stays confined to the test code) — that catalog-declared
test dependencies are added per suite (Mockito only for the built-in `test` suite) and that JUnit
Jupiter is pinned to the catalog's version. The harness `EasyJvmDefaultsPluginFuncTest` keeps
functional coverage deliberately thin — everything else is unit-covered — and cross-checks the
paths that need a real build: sources/javadoc jar execution, the catalog probe, a multi-project
build with a `test-report-aggregation` root whose auto-configured `functionalTest` suite is
aggregated into a root-level `functionalTestAggregateTestReport`, and a multi-project build whose
`functionalTestCodeCoverageReport` records coverage of a main class exercised only by the
functional suite; the aggregation builds run with `--configuration-cache` twice, asserting the
entry is stored and reused without problems (configuration-cache compatibility, per the
contributor-plugin requirement). Automatic root aggregation (`aggregateReports`, including the
auto-applied `test-report-aggregation` and the built-in `test` suite's reports) and its opt-out are
covered by the unit and functional tests.

## Known limitations

* Classification is intentionally minimal: everything matching `*Test` is treated as a
  functional suite (so `integrationTest`/`smokeTest` are registered too, not only
  `functionalTest`).
* Coverage aggregation is per test suite: auto-configured functional suites are aggregated at the
  root via a `<suite>CodeCoverageReport` (e.g. `functionalTestCodeCoverageReport`), not merged
  into the `test` suite's `testCodeCoverageReport`. The root project needs a repository for the
  JaCoCo Ant library used by the report.
* Root aggregation is gated by `aggregateReports` (default `true`); setting it to `false` leaves
  `test-report-aggregation`/`jacoco-report-aggregation` and all root reports to the consumer.
* Suite discovery reads the filesystem at configuration time; Gradle's configuration cache does
  not track directory listings as inputs, so adding a brand-new `src/<name>Test` directory may
  not invalidate a cached configuration.
* No framework override; only `useJUnitJupiter()` is applied. The JUnit Jupiter version is pinned
  from the consuming build's `libs` catalog when it declares `junit-jupiter`/`junit-jupiter-api`,
  otherwise Gradle's default version is used.
* Catalog-aware dependencies are limited to AssertJ, JUnit Pioneer, JUnit Jupiter params and
  Mockito, and only when the consuming build declares them in its `libs` catalog; Mockito is added
  to the built-in `test` suite only, not to functional suites.
