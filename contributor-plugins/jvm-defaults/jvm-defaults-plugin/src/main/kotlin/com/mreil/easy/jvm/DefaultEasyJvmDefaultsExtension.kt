package com.mreil.easy.jvm

import com.mreil.easy.PublicType

/**
 * Internal implementation of [EasyJvmDefaultsExtension].
 *
 * Empty for now - no additional configuration needed.
 *
 * Abstract for Gradle extension decoration via extensions.create (requires a non-final type).
 */
@Suppress("UnnecessaryAbstractClass")
@PublicType(EasyJvmDefaultsExtension::class)
abstract class DefaultEasyJvmDefaultsExtension : EasyJvmDefaultsExtension {
    init {
        enabled.convention(true)
    }
}
