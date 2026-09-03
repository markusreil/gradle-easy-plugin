package com.mreil.easy.codemeta

import com.mreil.easy.PublicType

/**
 * Internal implementation of [EasyCodemetaExtension].
 *
 * Empty for now - no additional configuration needed.
 */
@PublicType(EasyCodemetaExtension::class)
abstract class DefaultEasyCodemetaExtension : EasyCodemetaExtension {
    init {
        enabled.convention(true)
        filename.convention("codemeta.json")
    }
}
