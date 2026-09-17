package com.mreil.easy.jvm

import com.mreil.easy.easyInfo
import com.mreil.utils.catalogLibrary
import org.gradle.api.Project
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.AggregateTestReport
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata
import org.gradle.testing.base.TestingExtension

/**
 * Configures test suites, gated by [EasyJvmDefaultsExtension.configureTestSuites].
 *
 * The built-in `test` suite is configured in place (JUnit Jupiter version + catalog test
 * dependencies) but never registered, ordered, wired into `check` or exposed as a
 * plugin-under-test source set — Gradle already provides all of that. Suites discovered for every
 * `src/<name>Test/{java,kotlin}` directory are registered and configured. Both are matched through
 * the live [TestingExtension.suites] container, so suites a consumer registers itself are
 * configured too, and creation is idempotent via `maybeCreate`.
 */
internal object TestSuiteWiring {
    internal fun configure(target: Project) {
        val extension = target.jvmDefaults() ?: return
        if (!extension.configureTestSuites.get()) return
        applyUnitSuiteConfiguration(target)
        registerRootAggregateReport(target, DEFAULT_UNIT_SUITE)
        configureFunctionalSuites(target)
    }

    /** Configures the framework-provided `test` suite, which needs no registration or wiring. */
    private fun applyUnitSuiteConfiguration(target: Project) {
        target.extensions
            .getByType(TestingExtension::class.java)
            .suites
            .withType(JvmTestSuite::class.java)
            .matching { it.name == DEFAULT_UNIT_SUITE }
            .configureEach { configureUnitSuite(target, it) }
    }

    /**
     * Pins the test framework and adds the catalog-declared unit-test dependencies for the
     * framework-provided `test` suite. Deliberately skips the functional-only wiring (ordering,
     * `check`, `testSourceSets`) that [applyFunctionalSuiteConfiguration] applies. In plugin
     * projects Gradle's `java-gradle-plugin` already puts `gradleTestKit()` and the
     * plugin-under-test metadata on the built-in `test` suite's classpath, so nothing is added
     * here for that.
     */
    internal fun configureUnitSuite(
        target: Project,
        suite: JvmTestSuite,
    ) {
        configureTestFramework(target, suite)
        addCatalogDependencies(target, suite, CATALOG_UNIT_TEST_DEPENDENCIES)
        target.easyInfo(
            "Auto-configured the framework-provided '{}' test suite via the Easy Gradle jvm-defaults plugin",
            suite.name,
        )
    }

    /** Registers and configures every discovered `src/<name>Test` suite. */
    private fun configureFunctionalSuites(target: Project) {
        val names =
            target
                .findTestSuites()
                .filter { it.type == TestSuiteType.FUNCTIONAL }
                .map { it.name }
                .toSet()
        if (names.isEmpty()) return
        val suites = target.extensions.getByType(TestingExtension::class.java).suites
        suites
            .withType(JvmTestSuite::class.java)
            .matching { it.name in names }
            .configureEach { applyFunctionalSuiteConfiguration(target, it) }
        names.forEach { suites.maybeCreate(it, JvmTestSuite::class.java) }
        names.forEach { registerRootAggregateReport(target, it) }
    }

    /**
     * Configures a discovered functional suite: framework, main classes, catalog dependencies,
     * ordering, `check`, and — for plugin projects — the `java-gradle-plugin` classpath wiring.
     *
     * The built-in `test` suite gets the project's main output from the `java` plugin, but a
     * JVM test suite registered here does not, so the main source set's output is added explicitly
     * (the equivalent of the `implementation(project())` a consumer would otherwise declare).
     *
     * `java-gradle-plugin` only wires the source sets it sees while it runs its own configuration
     * hook, so a suite registered here (after evaluation) is missed. The same dependencies are
     * therefore added explicitly: `gradleTestKit()`/`gradleApi()` on `implementation` and the
     * `pluginUnderTestMetadata` output on `runtimeOnly`, which is what puts
     * `plugin-under-test-metadata.properties` on the classpath for
     * `GradleRunner.withPluginClasspath()`. The test-task input and normalization side of that
     * wiring is project-wide and already applied by `java-gradle-plugin`.
     */
    private fun applyFunctionalSuiteConfiguration(
        target: Project,
        suite: JvmTestSuite,
    ) {
        configureTestFramework(target, suite)
        addMainOutput(target, suite)
        addCatalogDependencies(target, suite, CATALOG_FUNCTIONAL_TEST_DEPENDENCIES)
        target.easyInfo(
            "Auto-configured functional test suite '{}' via the Easy Gradle jvm-defaults plugin",
            suite.name,
        )
        suite.targets.all {
            it.testTask.configure { task ->
                task.shouldRunAfter(DEFAULT_UNIT_SUITE)
            }
        }
        target.tasks.named("check").configure { checkTask ->
            suite.targets.all { checkTask.dependsOn(it.testTask) }
        }
        target.pluginManager.withPlugin("java-gradle-plugin") {
            target.extensions
                .getByType(GradlePluginDevelopmentExtension::class.java)
                .testSourceSets
                .add(suite.sources)
            suite.dependencies.implementation.add(target.dependencies.gradleTestKit())
            suite.dependencies.implementation.add(target.dependencies.gradleApi())
            val pluginUnderTestMetadata =
                target.tasks.named(PLUGIN_UNDER_TEST_METADATA, PluginUnderTestMetadata::class.java)
            suite.dependencies.runtimeOnly.add(target.layout.files(pluginUnderTestMetadata))
        }
    }

    /**
     * Registers a root-level [AggregateTestReport] for [suiteName] on the consuming build's root
     * project, mirroring the root build's aggregate report setup for the built-in `test` suite.
     * No-op unless [EasyJvmDefaultsExtension.aggregateReports] is enabled and the root applied
     * `test-report-aggregation` (which [ReportAggregationWiring] does by default), and idempotent
     * across projects sharing the same suite name.
     */
    private fun registerRootAggregateReport(
        target: Project,
        suiteName: String,
    ) {
        if (!target.aggregateReportsEnabled()) return
        val root = target.rootProject
        root.pluginManager.withPlugin(TEST_REPORT_AGGREGATION_PLUGIN) {
            val reports = root.extensions.getByType(ReportingExtension::class.java).reports
            val reportName = aggregateReportName(suiteName)
            val report = reports.maybeCreate(reportName, AggregateTestReport::class.java)
            report.testSuiteName.set(suiteName)
            root.dependencies.add(
                TEST_REPORT_AGGREGATION,
                root.dependencies.project(mapOf(PROJECT_PATH to target.path)),
            )
            root.tasks.matching { task -> task.name == "check" }.configureEach { checkTask ->
                checkTask.dependsOn(report.reportTask)
            }
            target.easyInfo(
                "Registered root-level aggregate report '{}' for test suite '{}'",
                reportName,
                suiteName,
            )
        }
    }

    /**
     * Selects JUnit Jupiter for [suite], pinning the version from the consuming build's `libs`
     * catalog when it declares `junit-jupiter` or `junit-jupiter-api`; otherwise Gradle's default
     * JUnit Jupiter version is used.
     */
    internal fun configureTestFramework(
        target: Project,
        suite: JvmTestSuite,
    ) {
        val version =
            JUPITER_CATALOG_ALIASES
                .firstNotNullOfOrNull { alias ->
                    target
                        .catalogLibrary(alias)
                        .orNull
                        ?.versionConstraint
                        ?.requiredVersion
                }?.takeIf { it.isNotBlank() }
        if (version != null) {
            suite.useJUnitJupiter(version)
            target.easyInfo(
                "Pinned JUnit Jupiter to {} for test suite '{}' (from libs catalog)",
                version,
                suite.name,
            )
        } else {
            suite.useJUnitJupiter()
        }
    }

    /**
     * Adds the project's own classes (the `main` source set output) to [suite], so functional
     * tests can exercise the code under test the same way the built-in `test` suite can.
     */
    private fun addMainOutput(
        target: Project,
        suite: JvmTestSuite,
    ) {
        val main = target.extensions.getByType(SourceSetContainer::class.java).getByName(MAIN_SOURCE_SET)
        suite.dependencies.implementation.add(main.output)
    }

    /**
     * Adds well-known test dependencies that the consuming build declares in its `libs` version
     * catalog, so a configured suite uses the build's pinned coordinates and version. Aliases
     * absent from the catalog (and builds without a catalog) are skipped.
     */
    internal fun addCatalogDependencies(
        target: Project,
        suite: JvmTestSuite,
        dependencies: List<List<String>>,
    ) {
        dependencies.forEach { aliases ->
            aliases
                .map { target.catalogLibrary(it) }
                .firstOrNull { it.isPresent }
                ?.let { suite.dependencies.implementation.add(it) }
        }
    }
}

/** Aliases probed for a JUnit Jupiter version in the consuming build's `libs` catalog. */
private val JUPITER_CATALOG_ALIASES = listOf("junit-jupiter", "junit-jupiter-api")

/** Name of the source set whose output is added to auto-configured functional suites. */
private const val MAIN_SOURCE_SET = "main"

/**
 * Test dependencies to add to the framework-provided `test` suite, each with its alias candidates
 * tried in order. Add new entries here as the build's unit-test setups grow.
 */
internal val CATALOG_UNIT_TEST_DEPENDENCIES =
    listOf(
        listOf("assertj-core", "assertj"),
        listOf("junit-pioneer"),
        listOf("junit-jupiter-params"),
        listOf("mockito-core"),
    )

/**
 * Test dependencies to add to auto-configured functional suites, each with its alias candidates
 * tried in order. Mockito is deliberately absent: functional tests exercise real builds rather
 * than mocking collaborators.
 */
internal val CATALOG_FUNCTIONAL_TEST_DEPENDENCIES =
    listOf(
        listOf("assertj-core", "assertj"),
        listOf("junit-pioneer"),
        listOf("junit-jupiter-params"),
    )

/** Task generating the `plugin-under-test-metadata.properties` consumed by `withPluginClasspath()`. */
private const val PLUGIN_UNDER_TEST_METADATA = "pluginUnderTestMetadata"

private const val PROJECT_PATH = "path"

/** Mirrors Gradle's automatic report naming (`<suiteName>AggregateTestReport`). */
private fun aggregateReportName(suiteName: String): String = "${suiteName}AggregateTestReport"
