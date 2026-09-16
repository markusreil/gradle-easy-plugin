package com.mreil.easy.codemeta

import com.mreil.easy.PublicType

/**
 * Internal implementation of [EasyCodemetaExtension].
 *
 * Holds the default conventions; enabled by default.
 */
@Suppress("AbstractClassCanBeInterface")
@PublicType(EasyCodemetaExtension::class)
abstract class DefaultEasyCodemetaExtension : EasyCodemetaExtension {
    init {
        enabled.convention(true)
        filename.convention("codemeta.json")
        updateOnRelease.convention(true)
    }
}
