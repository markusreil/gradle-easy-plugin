package com.mreil.easy.projectdefaults

import com.mreil.easy.PublicType

/**
 * Internal implementation of [EasyProjectDefaultsExtension].
 *
 * Empty for now - no additional configuration needed.
 */
@PublicType(EasyProjectDefaultsExtension::class)
abstract class DefaultEasyProjectDefaultsExtension : EasyProjectDefaultsExtension {
    init {
        enabled.convention(true)
    }
}
