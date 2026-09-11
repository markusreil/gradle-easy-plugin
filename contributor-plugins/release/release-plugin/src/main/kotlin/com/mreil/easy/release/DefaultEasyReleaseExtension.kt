package com.mreil.easy.release

import com.mreil.easy.PublicType

@Suppress("UnnecessaryAbstractClass")
@PublicType(EasyReleaseExtension::class)
abstract class DefaultEasyReleaseExtension : EasyReleaseExtension {
    init {
        enabled.convention(true)
    }
}
