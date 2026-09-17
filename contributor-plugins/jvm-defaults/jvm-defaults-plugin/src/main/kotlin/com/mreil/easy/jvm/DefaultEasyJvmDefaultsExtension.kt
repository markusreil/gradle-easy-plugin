package com.mreil.easy.jvm

import com.mreil.easy.PublicType
import org.gradle.api.provider.Property

/**
 * Internal implementation of [EasyJvmDefaultsExtension].
 *
 * Abstract for Gradle extension decoration via extensions.create (requires a non-final type).
 */
@Suppress("AbstractClassCanBeInterface")
@PublicType(EasyJvmDefaultsExtension::class)
abstract class DefaultEasyJvmDefaultsExtension : EasyJvmDefaultsExtension {
    init {
        enabled.convention(true)
        configureTestSuites.convention(true)
        aggregateReports.convention(true)
        jacocoEnabled.convention(true)
    }

    abstract override val configureTestSuites: Property<Boolean>

    abstract override val aggregateReports: Property<Boolean>

    abstract override val jacocoEnabled: Property<Boolean>
}
