package com.mreil.easy.codemeta

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named
import org.gradle.api.provider.Property

/**
 * Public API for the `easy.codemeta` extension.
 *
 * Controls the root project's shared `codemeta.json`: `filename` selects the file location,
 * `updateOnRelease` enables updating `version` and `dateModified` on every release via the
 * release lifecycle (`com.mreil.easy.release.EasyRelease.beforePreReleaseCommit`).
 */
interface EasyCodemetaExtension :
    EasyPluginExtension,
    CanBeEnabled {
    val filename: Property<String>

    /** Whether the codemeta file is updated on every release (version + dateModified). */
    val updateOnRelease: Property<Boolean>

    companion object : Named {
        override val name: String = "codemeta"
    }
}
