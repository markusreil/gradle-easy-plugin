package com.mreil.easy.jvm

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named
import org.gradle.api.provider.Property

/**
 * Public API for the project-scope `easy.jvmDefaults` extension.
 *
 * Enables JVM defaults (toolchain pinning, sources/javadoc jars) via `easy { jvmDefaults {} }` in
 * the root project. When [configureTestSuites] is enabled, the plugin configures the
 * framework-provided `test` suite and discovers the project's `src/<name>test/{java,kotlin}` source
 * directories as a basis for auto-configuring further test suites.
 *
 * Settings scope has a separate, distinct extension ([EasyJvmDefaultsSettingsExtension]); the two
 * are not copied into each other.
 */
interface EasyJvmDefaultsExtension :
    EasyPluginExtension,
    CanBeEnabled {
    /**
     * Whether test suites are auto-configured: the framework-provided `test` suite and suites
     * discovered from the project's `src/<name>test/{java,kotlin}` source directories
     * (convention `true`).
     */
    val configureTestSuites: Property<Boolean>

    /**
     * Whether root-level report aggregation is configured automatically (convention `true`): the
     * plugin applies `test-report-aggregation` to the build's root (and, when [jacocoEnabled],
     * `jacoco-report-aggregation`), registers a root `<suite>AggregateTestReport` and — for
     * coverage — `<suite>CodeCoverageReport` for every configured suite (the built-in `test` suite
     * and each discovered `*Test` suite), declares each contributing project in the root
     * `testReportAggregation`/`jacocoAggregation` configurations and wires the reports into the
     * root `check` task.
     *
     * Disable it to manage root aggregation manually; the per-project `jacocoTestReport` wiring is
     * unaffected (it is gated by [jacocoEnabled] only).
     */
    val aggregateReports: Property<Boolean>

    /**
     * Whether JaCoCo is applied to the project and auto-configured functional suites are wired into
     * its coverage report (convention `true`). Root-level coverage aggregation is gated by
     * [aggregateReports]; disabling it here therefore also drops this project's coverage from the
     * aggregated root report.
     */
    val jacocoEnabled: Property<Boolean>

    companion object : Named {
        override val name: String = "jvmDefaults"
    }
}
