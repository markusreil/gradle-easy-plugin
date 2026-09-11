package com.mreil.easy.release

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named
import org.gradle.api.provider.Property

interface EasyReleaseExtension :
    EasyPluginExtension,
    CanBeEnabled {
    companion object : Named {
        override val name: String = "release"
    }

    /**
     * Regex matched against the current VCS branch to decide release readiness.
     */
    val releaseBranchPattern: Property<String>
}
