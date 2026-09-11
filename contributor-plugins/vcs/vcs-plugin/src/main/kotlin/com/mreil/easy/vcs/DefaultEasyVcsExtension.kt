package com.mreil.easy.vcs

import com.mreil.easy.PublicType

/**
 * Internal implementation of [EasyVcsExtension].
 *
 * Empty for now - no additional configuration needed.
 */
@PublicType(EasyVcsExtension::class)
abstract class DefaultEasyVcsExtension : EasyVcsExtension {
    init {
        enabled.convention(true)
    }
}
