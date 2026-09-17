package com.mreil.easy.jvm

import com.mreil.easy.easyInfo
import org.gradle.api.Project

/**
 * Applies root-level report aggregation, gated by [EasyJvmDefaultsExtension.aggregateReports]:
 * `test-report-aggregation` always and `jacoco-report-aggregation` when coverage is enabled
 * ([EasyJvmDefaultsExtension.jacocoEnabled]).
 *
 * The per-suite `<suite>AggregateTestReport` / `<suite>CodeCoverageReport` registrations live in
 * [TestSuiteWiring] / [JacocoWiring] and only fire once the corresponding plugin is applied here.
 */
internal object ReportAggregationWiring {
    internal fun configureRootAggregation(target: Project) {
        if (target != target.rootProject || !target.aggregateReportsEnabled()) return
        target.pluginManager.apply(TEST_REPORT_AGGREGATION_PLUGIN)
        if (target.jacocoEnabled()) {
            target.pluginManager.apply(JACOCO_REPORT_AGGREGATION_PLUGIN)
        }
        target.easyInfo("Applied report aggregation to the root project")
    }
}

/** Plugin id of Gradle's test report aggregation plugin. */
internal const val TEST_REPORT_AGGREGATION_PLUGIN = "test-report-aggregation"

/** Plugin id of Gradle's JaCoCo report aggregation plugin. */
internal const val JACOCO_REPORT_AGGREGATION_PLUGIN = "jacoco-report-aggregation"

/** Configuration through which the root declares the projects whose test results are aggregated. */
internal const val TEST_REPORT_AGGREGATION = "testReportAggregation"

/** Configuration through which the root declares the projects whose coverage is aggregated. */
internal const val JACOCO_AGGREGATION = "jacocoAggregation"
