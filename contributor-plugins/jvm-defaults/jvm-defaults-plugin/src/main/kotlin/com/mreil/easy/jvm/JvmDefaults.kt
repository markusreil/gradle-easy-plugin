package com.mreil.easy.jvm

import com.mreil.easy.findEasyChild
import org.gradle.api.Project

/** The `easy.jvmDefaults` implementation attached to this project, if any. */
internal fun Project.jvmDefaults(): DefaultEasyJvmDefaultsExtension? =
    findEasyChild<EasyJvmDefaultsExtension, DefaultEasyJvmDefaultsExtension>()

/** Whether JaCoCo is enabled for this project ([EasyJvmDefaultsExtension.jacocoEnabled]). */
internal fun Project.jacocoEnabled(): Boolean = jvmDefaults()?.jacocoEnabled?.get() ?: false

/** Whether root-level report aggregation is enabled ([EasyJvmDefaultsExtension.aggregateReports]). */
internal fun Project.aggregateReportsEnabled(): Boolean = jvmDefaults()?.aggregateReports?.get() ?: false
