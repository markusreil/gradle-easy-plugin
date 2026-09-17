package com.mreil.easy.jvm

import com.mreil.easy.easyInfo
import com.mreil.easy.findEasyChild
import org.gradle.api.Project
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.base.TestingExtension
import org.gradle.testing.jacoco.plugins.JacocoCoverageReport
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

/**
 * Applies JaCoCo and wires the auto-configured functional suites into the project's coverage
 * report, gated by [EasyJvmDefaultsExtension.jacocoEnabled].
 *
 * Applying the `jacoco` plugin instruments every `Test` task, but the project's `jacocoTestReport`
 * only consumes the built-in `test` suite's execution data — each functional suite's execution
 * data is added explicitly. On the build's root project the same gate applies
 * `jacoco-report-aggregation` and registers a root-level `<suite>CodeCoverageReport` per functional
 * suite, mirroring the `<suite>CodeCoverageReport` convention of the root build's own coverage
 * report, and wires them into the root `check` task.
 */
internal object JacocoWiring {
    /** Applies report aggregation to the build's root, once, when coverage is enabled there. */
    internal fun configureRootAggregation(target: Project) {
        if (target != target.rootProject || !target.jacocoEnabled()) return
        target.pluginManager.apply(JACOCO_REPORT_AGGREGATION_PLUGIN)
        target.easyInfo("Applied JaCoCo report aggregation to the root project")
    }

    internal fun configure(target: Project) {
        if (!target.jacocoEnabled()) return
        target.pluginManager.apply(JACOCO_PLUGIN)
        val report = target.tasks.named(JACOCO_REPORT_TASK, JacocoReport::class.java)
        val names =
            target
                .findTestSuites()
                .filter { it.type == TestSuiteType.FUNCTIONAL }
                .map { it.name }
                .toSet()
        target.extensions
            .getByType(TestingExtension::class.java)
            .suites
            .withType(JvmTestSuite::class.java)
            .matching { it.name in names }
            .configureEach { wireFunctionalSuite(target, report, it) }
        target.tasks.named(CHECK_TASK).configure { check -> check.dependsOn(report) }
        target.easyInfo("Applied JaCoCo to the project's test suites")
    }

    private fun wireFunctionalSuite(
        target: Project,
        report: TaskProvider<JacocoReport>,
        suite: JvmTestSuite,
    ) {
        suite.targets.all { suiteTarget ->
            val testTask = suiteTarget.testTask
            report.configure { reportTask ->
                reportTask.dependsOn(testTask)
                reportTask.executionData(
                    testTask.map { it.extensions.getByType(JacocoTaskExtension::class.java).destinationFile },
                )
            }
        }
        registerRootCodeCoverageReport(target, suite.name)
        target.easyInfo("Wired functional test suite '{}' into the JaCoCo report", suite.name)
    }

    /**
     * Registers a root-level [JacocoCoverageReport] for [suiteName] on the consuming build's root
     * project, declares this project in the root's `jacocoAggregation` configuration and wires the
     * report into the root `check` task. No-op unless the root aggregates coverage, and idempotent
     * across projects sharing the same suite name.
     */
    private fun registerRootCodeCoverageReport(
        target: Project,
        suiteName: String,
    ) {
        val root = target.rootProject
        root.pluginManager.withPlugin(JACOCO_REPORT_AGGREGATION_PLUGIN) {
            val reports = root.extensions.getByType(ReportingExtension::class.java).reports
            val reportName = codeCoverageReportName(suiteName)
            val report = reports.maybeCreate(reportName, JacocoCoverageReport::class.java)
            report.testSuiteName.set(suiteName)
            root.dependencies.add(
                JACOCO_AGGREGATION,
                root.dependencies.project(mapOf(PROJECT_PATH to target.path)),
            )
            root.tasks.matching { task -> task.name == CHECK_TASK }.configureEach { checkTask ->
                checkTask.dependsOn(report.reportTask)
            }
            target.easyInfo(
                "Registered root-level code coverage report '{}' for test suite '{}'",
                reportName,
                suiteName,
            )
        }
    }

    private fun Project.jacocoEnabled(): Boolean =
        findEasyChild<EasyJvmDefaultsExtension, DefaultEasyJvmDefaultsExtension>()
            ?.jacocoEnabled
            ?.get()
            ?: false
}

/** Mirrors the root build's `testCodeCoverageReport` naming (`<suiteName>CodeCoverageReport`). */
private fun codeCoverageReportName(suiteName: String): String = "${suiteName}CodeCoverageReport"

private const val JACOCO_PLUGIN = "jacoco"

/** Plugin id of Gradle's JaCoCo report aggregation plugin. */
private const val JACOCO_REPORT_AGGREGATION_PLUGIN = "jacoco-report-aggregation"

private const val JACOCO_REPORT_TASK = "jacocoTestReport"

/** Configuration through which the root declares the projects whose coverage is aggregated. */
private const val JACOCO_AGGREGATION = "jacocoAggregation"

private const val CHECK_TASK = "check"

private const val PROJECT_PATH = "path"
