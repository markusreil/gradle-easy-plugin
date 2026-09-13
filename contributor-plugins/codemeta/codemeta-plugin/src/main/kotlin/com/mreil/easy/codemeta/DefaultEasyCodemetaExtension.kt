package com.mreil.easy.codemeta

import com.mreil.easy.PublicType

/**
 * Internal implementation of [EasyCodemetaExtension].
 *
 * Holds the default conventions; enabled by default.
 */
@PublicType(EasyCodemetaExtension::class)
abstract class DefaultEasyCodemetaExtension : EasyCodemetaExtension {
    init {
        enabled.convention(true)
        filename.convention("codemeta.json")
        updateOnRelease.convention(true)
    }
}
