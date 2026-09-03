package com.mreil.easy.semver

import com.mreil.easy.PublicType

/**
 * Internal implementation of [EasySemverExtension].
 *
 * Empty for now - no additional configuration needed.
 */
@PublicType(EasySemverExtension::class)
abstract class DefaultEasySemverExtension : EasySemverExtension {
    init {
        enabled.convention(true)
    }
}
