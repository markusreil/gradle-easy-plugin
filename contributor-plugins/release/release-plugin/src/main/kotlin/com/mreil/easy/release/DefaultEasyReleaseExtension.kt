package com.mreil.easy.release

import com.mreil.easy.PublicType

@Suppress("UnnecessaryAbstractClass")
@PublicType(EasyReleaseExtension::class)
abstract class DefaultEasyReleaseExtension : EasyReleaseExtension {
    init {
        enabled.convention(true)
        releaseBranchPattern.convention("(main|master|rel-.*)")
        preReleaseCommitMessage.convention("Set version for release: \$v")
        tagTemplate.convention("v\$v")
        postReleaseCommitMessage.convention("Set new version after release: \$v")
    }
}
