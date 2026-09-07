package com.mreil.easy.semver

import com.mreil.easy.PublicType

/**
 * Internal implementation of [EasySemverExtension].
 *
 * Empty for now - no additional configuration needed.
 *
 * Abstract for Gradle extension decoration via extensions.create (requires a non-final type).
 */
@Suppress("UnnecessaryAbstractClass")
@PublicType(EasySemverExtension::class)
abstract class DefaultEasySemverExtension : EasySemverExtension {
    init {
        enabled.convention(true)
    }
}
